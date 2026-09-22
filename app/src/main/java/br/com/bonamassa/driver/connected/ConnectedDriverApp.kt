package br.com.bonamassa.driver.connected

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.bonamassa.driver.BuildConfig
import br.com.bonamassa.driver.ExternalActions
import br.com.bonamassa.driver.client.*
import br.com.bonamassa.driver.ui.*
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun ConnectedDriverApp(vm: ConnectedDriverViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, vm) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { while (true) { vm.refresh(); delay(if (vm.ui.value.syncError == null) 5_000 else 15_000) } }
            finally { vm.stopRefreshing() }
        }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var command by remember { mutableStateOf<Command?>(null) }
    var routeBatch by remember { mutableStateOf<List<Delivery>?>(null) }
    val delivery = ui.deliveries.find { it.id == ui.selected }
    LaunchedEffect(ui.selected, delivery?.version, ui.saved.session?.accessToken) { command = null }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.error) { ui.error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    BackHandler(ui.selected != null) { vm.select(null) }
    if (ui.saved.session == null) routeBatch = null
    routeBatch?.let { batch ->
        val unchanged = batch.all { selected -> ui.deliveries.any { it.id == selected.id && it.version == selected.version && it.canStartRoute } }
        RouteStartDialog(batch, ui.canWrite && unchanged && (batch.none { it.deliveryStatus == "ASSIGNED" } || ui.saved.session?.user?.available == true),
            { routeBatch = null }, { vm.startRoute(batch); routeBatch = null })
    }
    if (delivery != null && command != null) key(delivery.id, command) {
        CommandDialog(delivery, requireNotNull(command), ui.canWrite, { command = null }) { name, paid, reason ->
            vm.command(delivery, requireNotNull(command), name, paid, reason); command = null
        }
    }
    Scaffold(
        containerColor = Brand.Background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Column {
                BrandHeader(delivery?.let { "Pedido #${it.number}" }, if (ui.selected != null) ({ vm.select(null) }) else null, demo = false)
                if (ui.busy || ui.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand.Gold)
                ui.syncError?.takeIf { ui.saved.session != null }?.let { text ->
                    Surface(color = Brand.Raised) {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = vm::refresh, enabled = !ui.busy && !ui.refreshing) { Text("Atualizar") }
                        }
                    }
                }
                ui.saved.pending?.let {
                    Surface(color = Brand.Gold.copy(alpha = .12f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Envio aguardando confirmação", color = Brand.Gold, style = MaterialTheme.typography.titleSmall)
                            Text(if (ui.saved.session == null) "Entre na conta original para verificar o registro." else "Verifique o resultado antes de registrar outra ação.", style = MaterialTheme.typography.bodySmall)
                            if (ui.saved.session != null) TextButton(onClick = vm::retryPending, enabled = !ui.busy) { Text("Verificar envio") }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.loaded && !ui.fatal && ui.saved.session != null) {
                if (ui.selected == null) NavigationBar(containerColor = Brand.Surface) {
                    listOf("Entregas" to Icons.Default.DeliveryDining, "Histórico" to Icons.Default.History, "Perfil" to Icons.Default.Person).forEachIndexed { index, item ->
                        NavigationBarItem(tab == index, { tab = index }, { Icon(item.second, null) }, label = { Text(item.first) })
                    }
                } else if (delivery != null && delivery.commands.isNotEmpty()) Surface(color = Brand.Surface) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (Command.COLLECT in delivery.commands && ui.saved.session?.user?.available != true) Text("Ative sua disponibilidade na tela Entregas para retirar.", style = MaterialTheme.typography.bodySmall, color = Brand.Gold)
                        delivery.commands.forEach { action ->
                            val enabled = ui.canWrite && (action != Command.COLLECT || ui.saved.session?.user?.available == true)
                            if (action == Command.ISSUE) TextButton(onClick = { command = action }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
                            else PrimaryAction(action.label, Modifier.fillMaxWidth(), enabled) { command = action }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                ui.fatal -> Column(Modifier.padding(18.dp)) {
                    EmptyState("Seus dados estão preservados", "Não foi possível ler os dados protegidos do aparelho.", Icons.Default.Lock)
                    PrimaryAction("Tentar novamente", Modifier.fillMaxWidth(), !ui.busy) { vm.load() }
                }
                !ui.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.saved.session == null -> LoginScreen(ui, vm::signIn, vm::requestEmailVerification, vm::confirmEmail, vm::requestPasswordReset, vm::resetPassword)
                ui.selected != null && delivery != null -> DeliveryScreen(delivery, vm::message)
                tab == 0 -> QueueScreen(ui, vm::available, vm::select, vm::refresh, { routeBatch = it }, vm::message)
                tab == 1 -> HistoryScreen(ui, vm::select, vm::more)
                else -> ProfileScreen(ui, vm::logout)
            }
        }
    }
}

@Composable
private fun LoginScreen(
    ui: DriverUi,
    signIn: (String, String) -> Unit,
    resendVerification: (String) -> Unit,
    confirmEmail: (String, String) -> Unit,
    requestReset: (String, () -> Unit) -> Unit,
    resetPassword: (String, String, String, () -> Unit) -> Unit,
) {
    var email by rememberSaveable(ui.saved.account?.email) { mutableStateOf(ui.saved.account?.email.orEmpty()) }
    var mode by rememberSaveable { mutableStateOf("login") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(when (mode) { "verify" -> "Confirme seu e-mail."; "forgot" -> "Recupere seu acesso."; "reset" -> "Crie uma nova senha."; else -> "Sua próxima entrega\ncomeça aqui." }, style = MaterialTheme.typography.headlineLarge)
        Text(when (mode) { "verify" -> "Digite o código de 6 dígitos enviado para $email."; "forgot" -> "Vamos enviar um código para o seu e-mail."; "reset" -> "Informe o código recebido e a nova senha."; else -> "Entre com a conta cadastrada pela pizzaria." }, color = Brand.Muted)
        Panel {
            if (mode != "verify" && mode != "reset") Input("E-mail", email, { email = it.take(254) }, !ui.busy, KeyboardType.Email)
            if (mode in listOf("verify", "reset")) Input("Código de 6 dígitos", code, { code = it.filter(Char::isDigit).take(6) }, !ui.busy, KeyboardType.Number)
            if (mode == "login") {
                OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Senha") }, singleLine = true,
                    enabled = !ui.busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                PrimaryAction("Entrar nas entregas", Modifier.fillMaxWidth(), !ui.busy && email.isNotBlank() && password.isNotEmpty(), Icons.Default.Login) { signIn(email, password) }
                TextButton({ mode = "forgot" }, enabled = !ui.busy) { Text("Esqueci minha senha") }
                TextButton({ code = ""; mode = "verify"; resendVerification(email) }, enabled = !ui.busy && email.isNotBlank()) { Text("Confirmar meu e-mail") }
            } else if (mode == "verify") {
                PrimaryAction("Confirmar e entrar", Modifier.fillMaxWidth(), !ui.busy && code.length == 6) { confirmEmail(email, code) }
                TextButton({ resendVerification(email) }, enabled = !ui.busy) { Text("Reenviar código") }
                TextButton({ mode = "login" }, enabled = !ui.busy) { Text("Voltar") }
            } else if (mode == "forgot") {
                PrimaryAction("Enviar código", Modifier.fillMaxWidth(), !ui.busy && email.isNotBlank()) { requestReset(email) { code = ""; mode = "reset" } }
                TextButton({ mode = "login" }, enabled = !ui.busy) { Text("Voltar") }
            } else {
                OutlinedTextField(newPassword, { newPassword = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Nova senha") }, singleLine = true,
                    enabled = !ui.busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Text("Use de 12 a 128 caracteres.", color = Brand.Muted)
                PrimaryAction("Salvar nova senha", Modifier.fillMaxWidth(), !ui.busy && code.length == 6 && newPassword.length >= 12) {
                    resetPassword(email, code, newPassword) { password = ""; newPassword = ""; code = ""; mode = "login" }
                }
                TextButton({ requestReset(email) {} }, enabled = !ui.busy) { Text("Reenviar código") }
            }
        }
        Text("Ainda não tem acesso? Peça ao responsável para cadastrar seu perfil de entregador no painel.", style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
    }
}

@Composable
private fun QueueScreen(ui: DriverUi, available: (Boolean) -> Unit, open: (String) -> Unit, refresh: () -> Unit, startRoute: (List<Delivery>) -> Unit, message: (String) -> Unit) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("Todas") }
    val active = ui.deliveries.filter { it.active }
    val shown = active.filter { when (filter) { "Coletas" -> it.deliveryStatus in setOf("ASSIGNED", "COLLECTED"); "Em rota" -> it.deliveryStatus in setOf("ON_ROUTE", "RETURNING"); else -> true } }
    val groups = groupDeliveries(shown)
    val ready = active.filter { it.canStartRoute }
    val onRoute = groupDeliveries(active.filter { it.deliveryStatus == "ON_ROUTE" && it.status == "OUT_FOR_DELIVERY" })
    LazyColumn(Modifier.fillMaxSize().testTag("api_queue"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Boa rota, ${ui.saved.session?.user?.name?.substringBefore(' ')}.", style = MaterialTheme.typography.headlineLarge)
            Text("Pedidos atribuídos pela Bonamassa", color = Brand.Muted)
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (ui.saved.session?.user?.available == true) "Disponível para coletas" else "Novas coletas pausadas", style = MaterialTheme.typography.titleMedium)
                        Text("Pausar mantém as entregas já retiradas.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                    Switch(ui.saved.session?.user?.available == true, available, enabled = ui.canWrite, modifier = Modifier.testTag("availability"))
                }
                HorizontalDivider(color = Brand.Border)
                Row(Modifier.fillMaxWidth()) {
                    Metric("A retirar", active.count { it.deliveryStatus == "ASSIGNED" }.toString(), Modifier.weight(1f))
                    Metric("Retirados", active.count { it.deliveryStatus == "COLLECTED" }.toString(), Modifier.weight(1f))
                    Metric("Em rota", active.count { it.deliveryStatus == "ON_ROUTE" }.toString(), Modifier.weight(1f))
                }
            }
        }
        if (ready.isNotEmpty()) item {
            Panel {
                Text("Saída conjunta", style = MaterialTheme.typography.titleLarge)
                Text("${ready.size} pedidos · ${groupDeliveries(ready).size} endereços. Confira todos os volumes antes de sair.", color = Brand.Muted)
                if (ready.any { it.deliveryStatus == "ASSIGNED" } && ui.saved.session?.user?.available != true) Text("Ative sua disponibilidade para retirar os pedidos.", color = Brand.Gold)
                PrimaryAction("Iniciar rota com ${ready.size} pedidos", Modifier.fillMaxWidth(), ui.canWrite && ready.size <= 100 && (ready.none { it.deliveryStatus == "ASSIGNED" } || ui.saved.session?.user?.available == true)) { startRoute(ready) }
            }
        }
        if (onRoute.isNotEmpty()) item {
            Panel {
                Text("Rota em andamento", style = MaterialTheme.typography.titleLarge)
                Text("${onRoute.sumOf { it.deliveries.size }} pedidos · ${onRoute.size} paradas. Conclua cada pedido separadamente.", color = Brand.Muted)
                val legs = runCatching { routeLegs(onRoute) }
                if (legs.isFailure) Text(legs.exceptionOrNull()?.message.orEmpty(), color = Brand.Gold)
                legs.getOrNull()?.let { parts ->
                    if (parts.size > 1) Text("O Maps abre esta rota em ${parts.size} trechos. Todos os endereços estão listados abaixo.", color = Brand.Gold)
                    parts.forEachIndexed { index, leg ->
                        OutlinedButton({ ExternalActions.routeLeg(context, leg, message) }, Modifier.fillMaxWidth(), enabled = ui.canWrite) {
                            Text(if (parts.size == 1) "Abrir rota no Google Maps" else "Abrir trecho ${index + 1} de ${parts.size} · ${leg.stops.size} paradas")
                        }
                    }
                }
                Text("Paradas na ordem do pedido mais antigo de cada endereço. Confira a sequência no Maps antes de dirigir.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Suas entregas", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                IconButton(refresh, enabled = !ui.busy && !ui.refreshing) { Icon(Icons.Default.Refresh, "Atualizar entregas") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todas", "Coletas", "Em rota").forEach { label -> FilterChip(filter == label, { filter = label }, label = { Text(label) }) }
            }
        }
        if (shown.isEmpty()) item { EmptyState("Nenhuma entrega aqui", if (ui.updatedAt == null) "Aguarde a atualização do servidor." else "As entregas atribuídas pelo painel aparecerão aqui.", Icons.Default.TaskAlt) }
        items(groups, key = { it.key }) { stop ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stop.address?.let { "${it.street}, ${it.number} · ${it.city}/${it.state}" } ?: "Endereço a confirmar", style = MaterialTheme.typography.titleMedium, color = Brand.Gold)
                Text("${stop.deliveries.size} pedido(s) neste endereço", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                stop.deliveries.forEach { d -> DeliveryCard(d) { open(d.id) } }
            }
        }
        item { Text(ui.updatedAt?.let { "Atualizado em ${timestamp(it)}" } ?: "Aguardando conexão", color = Brand.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DeliveryCard(d: Delivery, open: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClickLabel = "Ver pedido ${d.number}", onClick = open)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${d.number}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Tag(d.label, if (d.status == "DELIVERED") Brand.Green else Brand.Gold)
        }
        Text(d.customer, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        Text(d.address?.let { "${it.street}, ${it.number}\n${it.neighborhood}" } ?: "Endereço não informado")
        d.address?.let { if (it.complement.isNotBlank()) Text("Complemento: ${it.complement}", color = Brand.Gold) else if (it.noComplement) Text("Sem complemento", color = Brand.Muted) }
        HorizontalDivider(color = Brand.Border)
        Text(when { d.status in setOf("RETURNING", "RETURNED") -> "Tentativa sem sucesso"; d.needsPayment -> "Cobrar ${money(d.total)}"; else -> "Pagamento confirmado" }, color = if (!d.needsPayment) Brand.Green else Brand.Cream)
        if (d.needsPayment && d.payment == "CASH" && d.change > 0) Text("Levar troco: ${money(d.change)}", style = MaterialTheme.typography.bodySmall, color = Brand.Gold)
    }
}

@Composable
private fun HistoryScreen(ui: DriverUi, open: (String) -> Unit, more: () -> Unit) {
    val finished = ui.deliveries.filterNot { it.active }
    LazyColumn(Modifier.fillMaxSize().testTag("api_history"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Cada entrega conta.", style = MaterialTheme.typography.headlineLarge) }
        item {
            Panel {
                Text("Taxas no histórico carregado", color = Brand.Muted)
                Text(money(finished.sumOf { it.driverEarnings }), style = MaterialTheme.typography.headlineLarge, color = Brand.Gold)
                Text("${finished.count { it.status == "DELIVERED" }} entregas · ${finished.count { it.status == "RETURNED" }} devoluções", color = Brand.Muted)
                Text("Valores registrados pela pizzaria. Não representam confirmação de repasse ao entregador.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
        if (finished.isEmpty()) item { EmptyState("Seu histórico começa aqui", "Entregas finalizadas e devoluções aparecerão nesta lista.", Icons.Default.History) }
        items(finished, key = { it.id }) { d ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(date(d.updatedAt), color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                DeliveryCard(d) { open(d.id) }
            }
        }
        if (ui.cursor != null) item { PrimaryAction("Carregar mais histórico", Modifier.fillMaxWidth(), !ui.busy, onClick = more) }
    }
}

@Composable
private fun ProfileScreen(ui: DriverUi, logout: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Sair da conta?") },
        text = { Text("Sair não altera sua disponibilidade nem cancela entregas. Pause novas coletas antes de encerrar o turno.") },
        confirmButton = { TextButton({ confirm = false; logout() }, enabled = !ui.busy && ui.saved.pending == null) { Text("Sair agora") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Voltar") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Seu espaço.", style = MaterialTheme.typography.headlineLarge)
        Panel {
            Icon(Icons.Default.Person, null, tint = Brand.Gold, modifier = Modifier.size(36.dp))
            Text(ui.saved.session?.user?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text(ui.saved.session?.user?.email.orEmpty(), color = Brand.Muted)
            Tag("ENTREGADOR")
            Text("Os dados da conta são administrados pela pizzaria.", color = Brand.Muted)
        }
        Panel {
            Text("Conectado à Bonamassa", style = MaterialTheme.typography.titleLarge)
            Text("Pedidos e atualizações sincronizados enquanto o aplicativo estiver aberto.", color = Brand.Muted)
            Text("Rotas abrem no Maps ou Waze. Rastreamento e notificações em segundo plano ainda não estão disponíveis.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            Text("Versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
        }
        OutlinedButton({ confirm = true }, Modifier.fillMaxWidth(), enabled = !ui.busy && ui.saved.pending == null) { Text("Sair da conta") }
    }
}

@Composable
private fun Input(label: String, value: String, change: (String) -> Unit, enabled: Boolean = true, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
private fun date(iso: String) = runCatching { timestamp(Instant.parse(iso).toEpochMilli()) }.getOrDefault(iso)

@Composable
private fun DeliveryScreen(d: Delivery, message: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Tag(d.label, if (d.status == "DELIVERED") Brand.Green else Brand.Gold)
        Text(d.customer, style = MaterialTheme.typography.headlineLarge)
        if (d.status == "RETURNING") Panel {
            Text("Retorne à pizzaria", style = MaterialTheme.typography.titleLarge)
            Text("Avise a equipe e devolva o pedido antes de confirmar a devolução.", color = Brand.Muted)
        }
        Panel {
            Text("Destino", style = MaterialTheme.typography.titleLarge)
            val address = d.address
            if (address == null) Text("Endereço indisponível. Confirme com a pizzaria.") else {
                Text("${address.street}, ${address.number}")
                Text("${address.neighborhood} · ${address.city}/${address.state}\nCEP ${address.postalCode}", color = Brand.Muted)
                if (address.complement.isNotBlank()) Text("Complemento: ${address.complement}", color = Brand.Gold)
                if (address.noComplement) Text("Sem complemento", color = Brand.Muted)
                if (address.reference.isNotBlank()) Text("Referência: ${address.reference}", color = Brand.Gold)
                if (d.status !in setOf("RETURNING", "RETURNED")) {
                    PrimaryAction("Abrir no Google Maps", Modifier.fillMaxWidth(), icon = Icons.Default.NearMe) { ExternalActions.route(context, address.route, false, message) }
                    OutlinedButton({ ExternalActions.route(context, address.route, true, message) }, Modifier.fillMaxWidth()) { Text("Abrir no Waze") }
                }
                TextButton({ clipboard.setText(AnnotatedString(listOf(address.route, address.complement, address.reference).filter { it.isNotBlank() }.joinToString(" · "))); message("Endereço copiado.") }) { Text("Copiar endereço") }
            }
            if (d.phone.isNotBlank()) OutlinedButton({ ExternalActions.dial(context, d.phone, message) }, Modifier.fillMaxWidth()) { Text("Ligar para o cliente") }
        }
        Panel {
            Text("Pagamento", style = MaterialTheme.typography.titleLarge)
            Tag(if (d.paymentRecorded || d.total == 0L) "PAGAMENTO CONFIRMADO" else "COBRAR NA ENTREGA", if (d.needsPayment) Brand.Gold else Brand.Green)
            AmountLine("Total do pedido", d.total, true)
            Text(when (d.payment) { "CASH" -> "Dinheiro"; "CARD" -> "Cartão · leve a maquininha"; "PREPAID" -> "Pagamento antecipado informado pela pizzaria"; else -> "Confira a forma de pagamento com a pizzaria" }, color = Brand.Muted)
            if (d.needsPayment && d.payment == "CASH") {
                d.cashTendered?.let { AmountLine("Troco para", it) }
                AmountLine("Levar de troco", d.change)
            }
            HorizontalDivider(color = Brand.Border)
            AmountLine(if (d.active) "Taxa prevista da entrega" else "Taxa registrada", if (d.active) d.driverFee else d.driverEarnings)
        }
        Panel {
            Text("Confira o pedido", style = MaterialTheme.typography.titleLarge)
            d.items.forEach { item ->
                Text("${item.quantity}× ${item.name}", style = MaterialTheme.typography.titleMedium)
                if (item.detail.isNotBlank()) Text(item.detail, color = Brand.Muted)
                item.components.forEach { component ->
                    Text("${component.quantity}× ${component.name} · ${component.detail}", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    if (component.note.isNotBlank()) Text(component.note, color = Brand.Gold)
                }
                if (item.note.isNotBlank()) Text(item.note, color = Brand.Gold)
            }
            if (d.note.isNotBlank()) { HorizontalDivider(color = Brand.Border); Text("Observação: ${d.note}", color = Brand.Gold) }
        }
        if (d.status == "DELIVERED") Panel {
            Text("Entrega concluída", style = MaterialTheme.typography.titleLarge, color = Brand.Green)
            Text("Recebido por ${d.recipient.orEmpty()}")
        }
        Panel {
            Text("Etapas do pedido", style = MaterialTheme.typography.titleLarge)
            d.events.forEach { event -> Text("${eventLabel(event.action)}\n${date(event.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
        }
    }
}
@Composable
private fun RouteStartDialog(batch: List<Delivery>, enabled: Boolean, dismiss: () -> Unit, start: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Conferir saída conjunta") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${batch.size} pedidos serão marcados como a caminho. A pizzaria e os clientes receberão a atualização.")
            groupDeliveries(batch).forEach { stop ->
                Text(stop.address?.route ?: "Endereço não informado", style = MaterialTheme.typography.titleSmall)
                stop.deliveries.forEach { d ->
                    Text("#${d.number} · ${d.customer}" + d.address?.complement?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
                    Text(d.items.joinToString("; ") { "${it.quantity}× ${it.name}" }, style = MaterialTheme.typography.bodySmall)
                    if (d.needsPayment) Text("Cobrar ${money(d.total)}" + if (d.payment == "CARD") " · Levar maquininha" else " · Troco ${money(d.change)}", color = Brand.Gold)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { checked = !checked }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, { checked = it })
                Text("Conferi e retirei todos os pedidos desta lista")
            }
            if (!enabled) Text("A lista ou sua disponibilidade mudou. Volte e atualize antes de sair.", color = Brand.Gold)
        }
    }, confirmButton = { TextButton(start, enabled = enabled && checked) { Text("Confirmar e iniciar todas") } },
        dismissButton = { TextButton(dismiss) { Text("Voltar") } })
}

private fun eventLabel(action: String) = when (action) {
    "created" -> "Pedido recebido"; "accept" -> "Pedido aceito"; "prepare" -> "Em preparo"; "ready" -> "Pronto"
    "assign" -> "Atribuído ao entregador"; "collect" -> "Retirado na pizzaria"; "start" -> "Saiu para entrega"
    "complete" -> "Entrega concluída"; "issue" -> "Tentativa sem sucesso"; "return" -> "Devolvido à pizzaria"
    "record-payment" -> "Pagamento registrado"; "cancel" -> "Pedido cancelado"; else -> "Pedido atualizado"
}

@Composable
private fun CommandDialog(d: Delivery, action: Command, enabled: Boolean, dismiss: () -> Unit, send: (String, Boolean, String) -> Unit) {
    var recipient by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf(false) }
    val valid = when (action) { Command.COMPLETE -> recipient.trim().isNotEmpty() && (!d.needsPayment || paid); Command.ISSUE -> reason.trim().isNotEmpty(); else -> true }
    val label = when (action) { Command.COLLECT -> "Retirei o pedido"; Command.START -> "Sair para entrega"; Command.COMPLETE -> "Registrar entrega"; Command.ISSUE -> "Registrar tentativa"; Command.RETURN -> "Devolvi à pizzaria" }
    AlertDialog(onDismissRequest = dismiss, title = { Text(action.label) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pedido #${d.number} · ${d.customer}")
            when (action) {
                Command.COLLECT -> Text("Confira os itens e confirme quando estiver com o pedido em mãos.")
                Command.START -> Text("A pizzaria e o cliente serão informados de que o pedido saiu para entrega.")
                Command.RETURN -> Text("Confirme somente após devolver o pedido à equipe da pizzaria.")
                Command.ISSUE -> {
                    Text("Informe o motivo e retorne com o pedido à pizzaria.")
                    OutlinedTextField(reason, { reason = it.take(240) }, label = { Text("Motivo da tentativa") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
                Command.COMPLETE -> {
                    Input("Quem recebeu?", recipient, { recipient = it.take(80) })
                    if (d.needsPayment) Row(Modifier.fillMaxWidth().clickable { paid = !paid }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(paid, { paid = it })
                        Text("Confirmo que recebi ${money(d.total)} ${if (d.payment == "CASH") "em dinheiro" else "no cartão"}.", style = MaterialTheme.typography.bodyMedium)
                    } else Text("O pagamento já está confirmado pela pizzaria.", color = Brand.Green)
                }
            }
        }
    }, confirmButton = { TextButton({ send(recipient, paid, reason) }, enabled = enabled && valid) { Text(label) } },
        dismissButton = { TextButton(dismiss) { Text("Voltar") } })
}
