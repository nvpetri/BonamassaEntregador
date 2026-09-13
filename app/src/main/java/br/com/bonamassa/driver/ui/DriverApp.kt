package br.com.bonamassa.driver.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.bonamassa.core.delivery.*
import br.com.bonamassa.driver.DriverViewModel
import br.com.bonamassa.driver.ExternalActions

@Composable
fun DriverApp(model: DriverViewModel = viewModel()) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable(ui.generation) { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable(selected, ui.generation) { mutableStateOf<String?>(null) }
    val delivery = ui.data.deliveries.find { it.id == selected }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dismiss = { dialog = null }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        model.clearMessage()
    }
    BackHandler(enabled = selected != null && dialog == null) { selected = null }

    Scaffold(
        containerColor = Brand.Background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { BrandHeader(delivery?.let { "Pedido #${it.orderNumber}" }, if (selected != null) ({ selected = null; dialog = null }) else null) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.loaded && !ui.failed) {
                if (selected == null) NavigationBar(containerColor = Brand.Surface) {
                    listOf("Entregas" to Icons.Default.DeliveryDining, "Histórico" to Icons.Default.History, "Perfil" to Icons.Default.Person).forEachIndexed { index, item ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.second, null) }, label = { Text(item.first) })
                    }
                } else if (delivery != null && !delivery.finished) {
                    Surface(color = Brand.Surface, tonalElevation = 4.dp) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (delivery.status == DeliveryStatus.ASSIGNED && !ui.data.available) Text("Ative sua disponibilidade na tela Entregas para retirar.", style = MaterialTheme.typography.bodySmall, color = Brand.Gold)
                            PrimaryAction(label = when (delivery.status) {
                                DeliveryStatus.ASSIGNED -> "Confirmar retirada"
                                DeliveryStatus.COLLECTED -> "Iniciar entrega"
                                DeliveryStatus.ON_ROUTE -> "Confirmar entrega"
                                DeliveryStatus.RETURNING -> "Confirmar devolução"
                                else -> "Concluído"
                            }, modifier = Modifier.fillMaxWidth(), enabled = !ui.busy && (delivery.status != DeliveryStatus.ASSIGNED || ui.data.available)) {
                                when (delivery.status) {
                                    DeliveryStatus.ASSIGNED -> dialog = "collect"
                                    DeliveryStatus.COLLECTED -> model.start(delivery.id)
                                    DeliveryStatus.ON_ROUTE -> dialog = "complete"
                                    DeliveryStatus.RETURNING -> dialog = "return"
                                    else -> Unit
                                }
                            }
                        }
                    }
                } else Spacer(Modifier.navigationBarsPadding())
            } else Spacer(Modifier.navigationBarsPadding())
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.failed -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
                    EmptyState("Não foi possível abrir seus dados", "Os dados anteriores foram preservados. Tente novamente ou reinicie a demonstração.", Icons.Default.CloudOff)
                    PrimaryAction("Tentar novamente", Modifier.fillMaxWidth(), enabled = !ui.busy, onClick = model::retry)
                    TextButton(onClick = { dialog = "reset" }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Reiniciar demonstração") }
                }
                !ui.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Brand.Gold)
                selected != null && delivery == null -> EmptyState("Entrega não encontrada", "Volte para a lista de entregas.", Icons.Default.SearchOff)
                delivery != null -> DeliveryDetails(delivery, ui.busy,
                    onRoute = { dialog = "route" },
                    onCopy = { clipboard.setText(AnnotatedString(delivery.address)); model.notify("Endereço copiado.") },
                    onDial = { ExternalActions.dial(context, delivery.phone, model::notify) },
                    onIssue = { dialog = "issue" })
                tab == 0 -> QueueScreen(ui.data, ui.busy, model::available, onOpen = { selected = it })
                tab == 1 -> HistoryScreen(ui.data, onOpen = { selected = it })
                else -> ProfileScreen(ui.data, ui.busy, model::name, onReset = { dialog = "reset" })
            }
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Brand.Gold)
        }
    }

    if (dialog == "reset") SimpleConfirmation(
        "Reiniciar demonstração?", "Isso apaga o nome, as etapas e o histórico deste app no aparelho e restaura os três pedidos de exemplo.",
        "Apagar e reiniciar", ui.busy, dismiss, onConfirm = { model.reset { selected = null; dialog = null; tab = 0 } })

    if (delivery != null && !ui.failed) when (dialog) {
        "collect" -> SimpleConfirmation("Retirar pedido #${delivery.orderNumber}?",
            "Confira todos os itens antes de sair.${if (delivery.change > 0) " Leve ${money(delivery.change)} de troco." else ""}${if (delivery.payment == CollectionMethod.CARD) " Leve a maquininha." else ""}",
            "Retirei o pedido", ui.busy, dismiss, onConfirm = { model.collect(delivery.id, dismiss) })
        "complete" -> ConfirmDeliveryDialog(delivery, ui.busy, dismiss) { receiver, paid -> model.complete(delivery.id, receiver, paid, dismiss) }
        "issue" -> IssueDialog(delivery, ui.busy, dismiss) { issue, note -> model.report(delivery.id, issue, note, dismiss) }
        "return" -> SimpleConfirmation("Confirmar devolução?", "Confirme somente depois de devolver o pedido #${delivery.orderNumber} à equipe da pizzaria.",
            "Registrar devolução", ui.busy, dismiss, onConfirm = { model.returned(delivery.id, dismiss) })
        "route" -> AlertDialog(onDismissRequest = dismiss, title = { Text("Abrir rota") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Destino de exemplo: ${delivery.address}")
                Text("Confira o destino no aplicativo de mapas antes de navegar.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                PrimaryAction("Google Maps", Modifier.fillMaxWidth()) { dismiss(); ExternalActions.route(context, delivery.address, false, model::notify) }
                OutlinedButton(onClick = { dismiss(); ExternalActions.route(context, delivery.address, true, model::notify) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Waze") }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("Voltar") } })
    }
}
