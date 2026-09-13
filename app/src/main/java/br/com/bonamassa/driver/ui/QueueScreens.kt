package br.com.bonamassa.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.bonamassa.core.delivery.*

private enum class QueueFilter(val label: String) { ALL("Todas"), COLLECTION("Coletas"), ROUTE("Em rota") }

@Composable
fun QueueScreen(state: DriverState, busy: Boolean, onAvailable: (Boolean) -> Unit, onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(QueueFilter.ALL.name) }
    val active = state.deliveries.filterNot(Delivery::finished)
    val shown = active.filter {
        when (QueueFilter.valueOf(filter)) {
            QueueFilter.ALL -> true
            QueueFilter.COLLECTION -> it.status == DeliveryStatus.ASSIGNED || it.status == DeliveryStatus.COLLECTED
            QueueFilter.ROUTE -> it.status == DeliveryStatus.ON_ROUTE || it.status == DeliveryStatus.RETURNING
        }
    }.sortedWith(compareBy<Delivery> { if (it.status == DeliveryStatus.RETURNING) 0 else 1 }.thenBy { it.events.first().at }.thenBy { it.orderNumber })

    LazyColumn(Modifier.fillMaxSize().testTag("delivery_queue"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Brand.Button.copy(alpha = .45f), Brand.Surface)), RoundedCornerShape(24.dp))
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.DeliveryDining, null, tint = Brand.Gold, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Boa rota, ${state.name}.", style = MaterialTheme.typography.titleLarge)
                        Text(if (active.isEmpty()) "Tudo certo. Confira seu histórico." else "${active.size} ${if (active.size == 1) "entrega para acompanhar" else "entregas para acompanhar"}.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
                HorizontalDivider(color = Brand.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("A retirar", active.count { it.status == DeliveryStatus.ASSIGNED }.toString(), Modifier.weight(1f))
                    Metric("Em rota", active.count { it.status == DeliveryStatus.ON_ROUTE }.toString(), Modifier.weight(1f))
                    Metric("Entregues", state.deliveries.count { it.status == DeliveryStatus.DELIVERED }.toString(), Modifier.weight(1f))
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.available) "Disponível para coletas" else "Novas coletas pausadas", style = MaterialTheme.typography.titleMedium)
                        if (!state.available) Text("Você pode concluir as já retiradas.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                    Switch(checked = state.available, onCheckedChange = onAvailable, enabled = !busy)
                }
            }
        }
        item {
            Text("Suas entregas", style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QueueFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option.name, onClick = { filter = option.name }, label = { Text(option.label) })
                }
            }
        }
        if (shown.isEmpty()) item { EmptyState("Nenhuma entrega aqui", "Confira os outros filtros ou acompanhe as concluídas no histórico.", Icons.Default.TaskAlt) }
        items(shown, key = Delivery::id) { delivery -> DeliveryCard(delivery) { onOpen(delivery.id) } }
        item { Text("Exemplos salvos neste aparelho. Não recebem pedidos da pizzaria.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
    }
}

@Composable
fun HistoryScreen(state: DriverState, onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Todas") }
    val summary = DeliveryRules.summary(state)
    val deliveries = state.deliveries.filter { it.finished && when (filter) {
        "Entregues" -> it.status == DeliveryStatus.DELIVERED
        "Devolvidas" -> it.status == DeliveryStatus.RETURNED
        else -> true
    } }.sortedByDescending { it.events.last().at }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Cada entrega conta.", style = MaterialTheme.typography.headlineLarge)
            Text("Histórico da demonstração neste aparelho", color = Brand.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Panel {
                Text("Taxas das entregas concluídas", style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
                Text(money(summary.fees), style = MaterialTheme.typography.headlineLarge, color = Brand.Gold)
                HorizontalDivider(color = Brand.Border)
                AmountLine("Dinheiro recebido dos clientes", summary.cashCollected)
                Text("${summary.delivered} entregas concluídas · valores de exemplo. Dinheiro recebido e taxa do entregador são valores separados.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todas", "Entregues", "Devolvidas").forEach { option -> FilterChip(filter == option, { filter = option }, label = { Text(option) }) }
            }
        }
        if (deliveries.isEmpty()) item { EmptyState("Seu histórico começa aqui", "As entregas finalizadas e as devoluções confirmadas aparecerão nesta lista.", Icons.Default.History) }
        items(deliveries, key = Delivery::id) { delivery ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(timestamp(delivery.events.last().at), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                DeliveryCard(delivery) { onOpen(delivery.id) }
            }
        }
    }
}

@Composable
fun ProfileScreen(state: DriverState, busy: Boolean, onSaveName: (String) -> Unit, onReset: () -> Unit) {
    var name by rememberSaveable(state.name) { mutableStateOf(state.name) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Seu espaço.", style = MaterialTheme.typography.headlineLarge)
            Text("Bonamassa Entregas", color = Brand.Gold)
        }
        item {
            Panel {
                Icon(Icons.Default.Person, null, tint = Brand.Gold, modifier = Modifier.size(36.dp))
                Text("Como podemos chamar você?", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Nome do entregador") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    supportingText = { Text("Nome usado apenas nesta demonstração.") })
                PrimaryAction("Salvar nome", Modifier.fillMaxWidth(), enabled = !busy && name.trim().length >= 2 && name.trim() != state.name) { onSaveName(name) }
            }
        }
        item {
            Panel {
                Text("Pronto para demonstrar", style = MaterialTheme.typography.titleLarge)
                Text("Três pedidos de exemplo: pago antecipadamente, dinheiro com troco e cartão na entrega.", color = Brand.Muted)
                Text("O app não recebe entregas reais, não cobra pagamentos e não transmite sua localização. Os destinos são exemplos públicos; os clientes são fictícios.", color = Brand.Muted)
                Text("0.1.0-demo", style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
            }
        }
        item {
            OutlinedButton(onClick = onReset, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.RestartAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Reiniciar demonstração")
            }
        }
    }
}
