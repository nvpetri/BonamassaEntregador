package br.com.bonamassa.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.bonamassa.core.delivery.*

@Composable
fun ConfirmDeliveryDialog(delivery: Delivery, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var receiver by rememberSaveable(delivery.id) { mutableStateOf("") }
    var paid by rememberSaveable(delivery.id) { mutableStateOf(false) }
    val needsPayment = delivery.payment != CollectionMethod.PREPAID
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Confirmar entrega #${delivery.orderNumber}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Confirme depois de entregar o pedido ao destinatário.")
                OutlinedTextField(receiver, { receiver = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Quem recebeu?") },
                    singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                if (needsPayment) {
                    Row(Modifier.fillMaxWidth().toggleable(value = paid, enabled = !busy, role = Role.Checkbox, onValueChange = { paid = it }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(paid, null, enabled = !busy)
                        Text("Confirmo que recebi ${money(delivery.amountToCollect)} ${if (delivery.payment == CollectionMethod.CASH) "em dinheiro" else "pela maquininha"}.", Modifier.weight(1f))
                    }
                    if (delivery.change > 0) Text("Troco combinado: ${money(delivery.change)}.", color = Brand.Gold)
                } else Text("Pedido informado como pago antecipadamente.", color = Brand.Green)
                Text("Este registro é apenas da demonstração.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(receiver, paid) }, enabled = !busy && receiver.trim().length >= 2 && (!needsPayment || paid)) { Text("Registrar entrega") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Voltar") } }
    )
}

@Composable
fun IssueDialog(delivery: Delivery, busy: Boolean, onDismiss: () -> Unit, onConfirm: (DeliveryIssue, String) -> Unit) {
    var selected by rememberSaveable(delivery.id) { mutableStateOf(DeliveryIssue.CUSTOMER_ABSENT.name) }
    var note by rememberSaveable(delivery.id) { mutableStateOf("") }
    val reason = DeliveryIssue.valueOf(selected)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Tentativa sem sucesso") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pedido #${delivery.orderNumber}. Registre o motivo e retorne à pizzaria com o pedido.")
                DeliveryIssue.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().selectable(selected = selected == option.name, enabled = !busy, role = Role.RadioButton, onClick = { selected = option.name }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == option.name, null, enabled = !busy)
                        Text(option.label, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(note, { note = it.take(280) }, Modifier.fillMaxWidth(), label = { Text(if (reason == DeliveryIssue.OTHER) "Descreva o motivo" else "Observação (opcional)") },
                    minLines = 2, maxLines = 4, enabled = !busy, supportingText = { Text("${note.length}/280") })
                Text("A entrega ficará aguardando a confirmação da devolução.", color = Brand.Gold)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason, note) }, enabled = !busy && (reason != DeliveryIssue.OTHER || note.trim().length >= 5)) { Text("Registrar tentativa") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Voltar") } }
    )
}

@Composable
fun SimpleConfirmation(title: String, detail: String, action: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Text(detail) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Voltar") } })
}
