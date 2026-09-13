package br.com.bonamassa.driver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.bonamassa.core.delivery.*
import br.com.bonamassa.driver.data.LocalDriverRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriverScreenState(
    val data: DriverState = DriverState(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val generation: Int = 0
)

class DriverViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalDriverRepository(application)
    private val mutableUi = MutableStateFlow(DriverScreenState())
    val ui = mutableUi.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private var observation: Job? = null

    init { retry() }

    fun notify(text: String) { mutableMessage.value = text }
    fun clearMessage() { mutableMessage.value = null }
    fun retry() {
        observation?.cancel()
        mutableUi.update { it.copy(failed = false) }
        observation = viewModelScope.launch {
            try {
                repository.state.collect { data -> mutableUi.update { it.copy(data = data, loaded = true, failed = false) } }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableUi.update { it.copy(failed = true) } }
        }
    }

    private fun change(message: String? = null, onDone: () -> Unit = {}, transform: (DriverState) -> DriverState) {
        if (!mutableUi.value.loaded || mutableUi.value.failed || mutableUi.value.busy) return
        mutableUi.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val updated = repository.update(transform)
                mutableUi.update { it.copy(data = updated) }
                onDone()
                if (message != null) notify(message)
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) { notify(e.message ?: "Confira os dados informados.")
            } catch (_: Exception) { notify("Não foi possível salvar a alteração. Tente novamente.")
            } finally { mutableUi.update { it.copy(busy = false) } }
        }
    }

    fun available(value: Boolean) = change { it.copy(available = value) }
    fun name(value: String) = change("Nome salvo neste aparelho.") { DeliveryRules.validate(it.copy(name = value.trim())) }
    fun collect(id: String, onDone: () -> Unit) = change("Retirada registrada na demonstração.", onDone) { DeliveryRules.collect(it, id, System.currentTimeMillis()) }
    fun start(id: String) = change("Entrega em rota na demonstração.") { DeliveryRules.start(it, id, System.currentTimeMillis()) }
    fun complete(id: String, receiver: String, paid: Boolean, onDone: () -> Unit) = change("Entrega registrada no histórico local.", onDone) {
        DeliveryRules.complete(it, id, receiver, paid, System.currentTimeMillis())
    }
    fun report(id: String, issue: DeliveryIssue, note: String, onDone: () -> Unit) = change("Tentativa registrada. Confirme a devolução ao voltar à pizzaria.", onDone) {
        DeliveryRules.reportIssue(it, id, issue, note, System.currentTimeMillis())
    }
    fun returned(id: String, onDone: () -> Unit) = change("Devolução registrada no histórico local.", onDone) {
        DeliveryRules.returnToStore(it, id, System.currentTimeMillis())
    }

    fun reset(onDone: () -> Unit) {
        if (mutableUi.value.busy) return
        mutableUi.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repository.reset()
                mutableUi.update { it.copy(generation = it.generation + 1) }
                retry()
                onDone()
                notify("Demonstração reiniciada com três entregas de exemplo.")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { notify("Não foi possível reiniciar. Tente novamente.")
            } finally { mutableUi.update { it.copy(busy = false) } }
        }
    }
}
