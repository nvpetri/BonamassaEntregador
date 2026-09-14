package br.com.bonamassa.driver.connected

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.bonamassa.driver.BuildConfig
import br.com.bonamassa.driver.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class DriverUi(val saved: SavedState = SavedState(), val loaded: Boolean = false, val fatal: Boolean = false,
    val busy: Boolean = false, val refreshing: Boolean = false, val deliveries: List<Delivery> = emptyList(),
    val cursor: String? = null, val selected: String? = null, val error: String? = null, val syncError: String? = null, val updatedAt: Long? = null) {
    val canWrite get() = loaded && !fatal && !busy && saved.session != null && saved.pending == null && updatedAt != null && syncError == null
}

class ConnectedDriverViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureStore(application)
    private val storage = Mutex()
    private val _ui = MutableStateFlow(DriverUi())
    val ui = _ui.asStateFlow()
    private var refreshJob: Job? = null
    private var epoch = 0
    private var historyLoaded = false
    init { load() }
    private fun endpoint() = Endpoint.parse(_ui.value.saved.origin, _ui.value.saved.slug, BuildConfig.DEBUG)
    private fun api() = DriverApi(endpoint())
    fun message(text: String) { _ui.update { it.copy(error = text) } }
    fun clearError() { _ui.update { it.copy(error = null) } }
    fun select(id: String?) { _ui.update { it.copy(selected = id) }; refresh() }

    fun load() {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val configured = Endpoint.parse(
                    BuildConfig.API_URL.ifBlank { if (BuildConfig.DEBUG) "http://10.0.2.2:3001" else "" },
                    BuildConfig.STORE_SLUG,
                    BuildConfig.DEBUG
                )
                val stored = withContext(Dispatchers.IO) { store.read() }
                val saved = when {
                    stored == null -> SavedState(origin = configured.origin, slug = configured.storeSlug)
                    stored.origin == configured.origin && stored.slug == configured.storeSlug -> stored
                    stored.pending != null -> stored
                    else -> SavedState(origin = configured.origin, slug = configured.storeSlug)
                }
                if (saved !== stored) withContext(Dispatchers.IO) { store.write(saved) }
                _ui.update { it.copy(saved = saved, loaded = true, busy = false, fatal = false) }
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.update { it.copy(fatal = true, busy = false, error = "Não foi possível ler os dados protegidos. O envio pendente foi preservado; tente novamente.") } }
        }
    }
    private suspend fun change(transform: (SavedState) -> SavedState) = storage.withLock {
        val next = transform(_ui.value.saved)
        withContext(Dispatchers.IO) { store.write(next) }
        _ui.update { it.copy(saved = next) }
    }
    private fun resetData() {
        epoch++; historyLoaded = false
        _ui.update { it.copy(deliveries = emptyList(), cursor = null, selected = null, updatedAt = null, syncError = null) }
    }
    private suspend fun failure(e: Exception, token: String?) {
        if (e is CancellationException) throw e
        if (e is ApiFailure && e.status in setOf(401, 403) && token != null && token == _ui.value.saved.session?.accessToken) {
            // Hide customer addresses immediately even if secure storage is temporarily unavailable.
            resetData()
            _ui.update { it.copy(saved = it.saved.copy(session = null)) }
            runCatching { change { it.copy(session = null) } }
        }
        message(when (e) {
            is ApiFailure -> if (e.status == 401 && token != null) "Sua sessão expirou. Entre novamente para continuar." else e.message
            is IllegalArgumentException -> e.message ?: "Verifique os dados informados."
            is IOException -> "Não foi possível conectar. Confira a rede e tente novamente."
            else -> "Não foi possível concluir. Os dados anteriores foram mantidos. Tente novamente."
        })
    }
    private fun action(block: suspend () -> Unit) {
        if (!_ui.value.loaded || _ui.value.fatal || _ui.value.busy) return
        val token = _ui.value.saved.session?.accessToken
        _ui.update { it.copy(busy = true, error = null) }
        epoch++
        viewModelScope.launch {
            try { refreshJob?.cancelAndJoin(); block() }
            catch (e: Exception) { failure(e, token) }
            finally { _ui.update { it.copy(busy = false) }; refresh() }
        }
    }
    private fun editable() { require(_ui.value.saved.pending == null) { "Verifique o envio pendente antes de fazer outra alteração ou sair." } }
    private fun session() = requireNotNull(_ui.value.saved.session) { "Entre com sua conta de entregador." }
    fun signIn(email: String, password: String) = action {
        require(email.trim().isNotBlank() && password.isNotEmpty()) { "Informe e-mail e senha." }
        val client = api()
        val next = withContext(Dispatchers.IO) { client.signIn(email, password) }
        try { change { it.signedIn(next) } }
        catch (e: Exception) { withContext(Dispatchers.IO) { runCatching { client.logout(next.accessToken) } }; throw e }
        resetData()
    }
    fun logout() = action {
        editable()
        val old = session(); val client = api()
        change { SavedState(origin = it.origin, slug = it.slug) }; resetData()
        withContext(Dispatchers.IO) { runCatching { client.logout(old.accessToken) } }.onFailure {
            if (it !is ApiFailure || it.status != 401) message("A conta saiu deste aparelho. A revogação no servidor não foi confirmada por falta de conexão.")
        }
    }
    fun available(value: Boolean) = action {
        editable(); require(_ui.value.updatedAt != null && _ui.value.syncError == null) { "Atualize as entregas antes de continuar." }
        val pending = Pending.availability(value, endpoint(), session().user)
        change { it.copy(pending = pending) }; sendPending()
    }
    fun command(delivery: Delivery, command: Command, recipient: String = "", paymentCollected: Boolean = false, reason: String = "") = action {
        editable(); require(_ui.value.updatedAt != null && _ui.value.syncError == null) { "Atualize as entregas antes de continuar." }
        val latest = _ui.value.deliveries.find { it.id == delivery.id }
        require(latest?.version == delivery.version) { "O pedido mudou. Confira a etapa atual." }
        val pending = Pending.delivery(delivery, command, endpoint(), session().user, recipient, paymentCollected, reason)
        change { it.copy(pending = pending) } // Durable before the first attempt.
        sendPending()
    }
    fun retryPending() = action { sendPending() }
    fun startRoute(deliveries: List<Delivery>) = action {
        editable()
        require(_ui.value.updatedAt != null && _ui.value.syncError == null) { "Atualize as entregas antes de continuar." }
        require(deliveries.all { selected -> _ui.value.deliveries.any { it.id == selected.id && it.version == selected.version && it.canStartRoute } }) { "A lista mudou. Confira os pedidos novamente." }
        val pending = Pending.route(deliveries, endpoint(), session().user)
        change { it.copy(pending = pending) }
        sendPending()
    }
    private suspend fun sendPending() {
        val pending = requireNotNull(_ui.value.saved.pending); val auth = session(); val client = api()
        require(pending.belongsTo(client.endpoint, auth.user)) { "Entre na conta original para verificar o envio." }
        try {
            val response = withContext(Dispatchers.IO) { client.send(auth.accessToken, pending) }
            if (pending.availability) {
                require(response.getString("id") == auth.user.id)
                val version = response.getInt("version"); val available = response.getBoolean("available")
                change { saved ->
                    val current = requireNotNull(saved.session)
                    val user = if (version >= current.user.version) current.user.copy(version = version, available = available) else current.user
                    saved.copy(session = current.copy(user = user), account = user, pending = null)
                }
            } else if (pending.startsRoute) {
                val items = response.getJSONArray("items")
                val result = (0 until items.length()).map { Decode.delivery(items.getJSONObject(it)) }
                require(result.map { it.id }.toSet() == pending.routeIds.toSet() && result.size == pending.routeIds.size)
                change { it.copy(pending = null) }
                merge(result)
            } else {
                val result = Decode.delivery(response)
                require(result.id == pending.deliveryId)
                change { it.copy(pending = null) }
                merge(listOf(result))
            }
            // A replay can return an old snapshot. Disable new commands until canonical refresh.
            _ui.update { it.copy(updatedAt = null) }
            message("Registro confirmado pela pizzaria.")
        } catch (e: ApiFailure) {
            if (e.definitive) {
                change { it.copy(pending = null) }
                _ui.update { it.copy(updatedAt = null) }
                if (e.status == 404) remove(pending.deliveryId)
            }
            throw e
        }
    }
    private fun remove(id: String?) { _ui.update { it.copy(deliveries = it.deliveries.filterNot { d -> d.id == id }, selected = it.selected.takeUnless { selected -> selected == id }) } }
    private fun merge(incoming: List<Delivery>) { _ui.update { it.copy(deliveries = (it.deliveries + incoming).groupBy { d -> d.id }.values.map { v -> v.maxBy { d -> d.version } }.sortedByDescending { d -> d.number }) } }
    fun more() = action {
        val cursor = _ui.value.cursor ?: return@action
        val token = session().accessToken; val client = api()
        val page = withContext(Dispatchers.IO) { client.deliveries(token, cursor) }
        merge(page.items); _ui.update { it.copy(cursor = page.nextCursor) }
    }
    /** Read only while STARTED; re-scan active assignments so old orders are never hidden by history. */
    fun refresh() {
        val state = _ui.value
        if (!state.loaded || state.fatal || state.busy || refreshJob?.isActive == true) return
        val auth = state.saved.session ?: return
        val generation = epoch
        val client = try { api() } catch (e: IllegalArgumentException) { _ui.update { it.copy(syncError = e.message) }; return }
        _ui.update { it.copy(refreshing = true) }
        refreshJob = viewModelScope.launch {
            try {
                val me = withContext(Dispatchers.IO) { client.me(auth.accessToken) }
                if (generation != epoch) return@launch
                if (me.id != auth.user.id || me.storeId != auth.user.storeId || me.role != "DRIVER" || !me.enabled) throw ApiFailure(403, "DRIVER_ONLY", "A conta não tem acesso às entregas.", null)
                if (me != _ui.value.saved.session?.user) change { it.copy(account = me, session = it.session?.copy(user = me)) }
                val page = withContext(Dispatchers.IO) { client.deliveries(auth.accessToken) }
                val active = mutableListOf<Delivery>()
                for (status in listOf("READY", "OUT_FOR_DELIVERY", "RETURNING")) {
                    var cursor: String? = null
                    do {
                        val batch = withContext(Dispatchers.IO) { client.deliveries(auth.accessToken, cursor, status) }
                        active.addAll(batch.items); cursor = batch.nextCursor
                        if (generation != epoch) return@launch
                    } while (cursor != null)
                }
                val latest = (page.items + active).toMutableList()
                val removed = mutableSetOf<String>()
                // Includes reassigned/cancelled orders (404) and orders completed on another device.
                val verify = (state.deliveries.filter { it.active }.map { it.id } + listOfNotNull(state.selected)).distinct().filterNot { id -> active.any { it.id == id } }
                for (id in verify) {
                    try { latest.add(withContext(Dispatchers.IO) { client.delivery(auth.accessToken, id) }) }
                    catch (e: ApiFailure) { if (e.status == 404) removed.add(id) else throw e }
                    if (generation != epoch) return@launch
                }
                if (generation != epoch) return@launch
                removed.forEach(::remove)
                merge(latest.filterNot { it.id in removed })
                if (!historyLoaded || (page.nextCursor != null && state.deliveries.none { old -> page.items.any { it.id == old.id } })) _ui.update { it.copy(cursor = page.nextCursor) }
                historyLoaded = true
                _ui.update { it.copy(updatedAt = System.currentTimeMillis(), syncError = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == epoch) {
                    if (e is ApiFailure && e.status in setOf(401, 403)) failure(e, auth.accessToken)
                    _ui.update { it.copy(syncError = if (e is ApiFailure) e.message else "Sem atualização. Confira a conexão; os dados exibidos podem estar desatualizados.") }
                }
            } finally { _ui.update { it.copy(refreshing = false) } }
        }
    }
    fun stopRefreshing() { refreshJob?.cancel() }
}
