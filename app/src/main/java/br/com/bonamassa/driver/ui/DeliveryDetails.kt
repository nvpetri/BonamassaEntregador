package br.com.bonamassa.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bonamassa.core.delivery.*

@Composable
fun DeliveryDetails(delivery: Delivery, busy: Boolean, onRoute: () -> Unit, onCopy: () -> Unit, onDial: () -> Unit, onIssue: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Tag(delivery.status.label, statusColor(delivery.status))
            Spacer(Modifier.height(12.dp))
            Text(when (delivery.status) {
                DeliveryStatus.ASSIGNED -> "Vamos buscar?"
                DeliveryStatus.COLLECTED -> "Tudo pronto para sair."
                DeliveryStatus.ON_ROUTE -> "A caminho do cliente."
                DeliveryStatus.DELIVERED -> "Entrega concluída."
                DeliveryStatus.RETURNING -> "Retorno à pizzaria."
                DeliveryStatus.RETURNED -> "Devolução confirmada."
            }, style = MaterialTheme.typography.headlineLarge)
        }
        item {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = Brand.Gold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Destino do pedido", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                        Text(delivery.address, style = MaterialTheme.typography.titleLarge)
                        Text(delivery.customer, style = MaterialTheme.typography.bodyMedium)
                        if (delivery.reference.isNotBlank()) Text(delivery.reference, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
                if (!delivery.finished && delivery.status != DeliveryStatus.RETURNING) {
                    PrimaryAction("Abrir rota", Modifier.fillMaxWidth(), icon = Icons.Default.NearMe, onClick = onRoute)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Copiar endereço") }
                    OutlinedButton(onClick = onDial, enabled = DeliveryLinks.dialNumber(delivery.phone) != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Ligar") }
                }
                if (delivery.phone.isBlank()) Text("Telefone não cadastrado nos pedidos de exemplo.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
        item {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (delivery.payment == CollectionMethod.CASH) Icons.Default.Payments else Icons.Default.CreditCard, null, tint = Brand.Gold)
                    Text("Pagamento do pedido", style = MaterialTheme.typography.titleLarge)
                }
                Text(delivery.payment.label, color = if (delivery.amountToCollect == 0L) Brand.Green else Brand.Cream)
                AmountLine("Total do pedido", delivery.orderTotal)
                AmountLine(if (delivery.finished) "Valor previsto na entrega" else "Valor a cobrar", delivery.amountToCollect, highlight = true)
                if (delivery.payment == CollectionMethod.CASH) {
                    if (delivery.changeFor > 0) {
                        AmountLine("Cliente vai pagar com", delivery.changeFor)
                        AmountLine("Troco a devolver", delivery.change, highlight = true)
                    } else Text("Cliente informou pagamento sem troco.", color = Brand.Muted)
                }
                if (delivery.payment == CollectionMethod.CARD) Text("Leve a maquininha. Confira a aprovação antes de confirmar a entrega.", color = Brand.Gold)
                HorizontalDivider(color = Brand.Border)
                AmountLine("Taxa do entregador", delivery.driverFee)
                Text("Valores e pagamento demonstrativos. Este app não realiza cobrança.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
        item {
            Panel {
                Text("Conferir o pedido", style = MaterialTheme.typography.titleLarge)
                delivery.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = Brand.Border)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tag("${item.quantity}×")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.details, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                            if (item.note.isNotBlank()) Text("Observação: ${item.note}", style = MaterialTheme.typography.bodyMedium, color = Brand.Gold)
                        }
                    }
                }
            }
        }
        if (delivery.status == DeliveryStatus.RETURNING || delivery.status == DeliveryStatus.RETURNED) item {
            Panel {
                Text("Tentativa sem sucesso", style = MaterialTheme.typography.titleLarge)
                Text(delivery.issue?.label.orEmpty(), color = Brand.Gold)
                if (delivery.issueNote.isNotBlank()) Text(delivery.issueNote, color = Brand.Muted)
                if (delivery.status == DeliveryStatus.RETURNING) Text("Retorne com o pedido e confirme a devolução depois de entregá-lo à equipe da pizzaria.", color = Brand.Muted)
            }
        }
        if (delivery.status == DeliveryStatus.DELIVERED) item {
            Panel {
                Icon(Icons.Default.TaskAlt, null, tint = Brand.Green)
                Text("Recebido por ${delivery.receiver}", style = MaterialTheme.typography.titleLarge)
                Text(if (delivery.paymentCollected) "Recebimento do pagamento registrado pelo entregador (demo)." else "Pedido informado como pago antecipadamente (demo).", color = Brand.Muted)
            }
        }
        item {
            Panel {
                Text("Etapas registradas", style = MaterialTheme.typography.titleLarge)
                delivery.events.forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(10.dp).background(statusColor(event.status), CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(event.status.label, style = MaterialTheme.typography.titleMedium)
                            Text(timestamp(event.at), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                            if (event.note.isNotBlank()) Text(event.note, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        }
                    }
                }
            }
        }
        if (delivery.status == DeliveryStatus.ON_ROUTE) item {
            OutlinedButton(onClick = onIssue, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.ReportProblem, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Não consegui entregar")
            }
        }
    }
}
