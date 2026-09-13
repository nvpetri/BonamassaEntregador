package br.com.bonamassa.driver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.bonamassa.core.delivery.*
import br.com.bonamassa.driver.R

@Composable
fun BrandHeader(title: String? = null, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(Brand.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack == null) Image(painterResource(R.drawable.bonamassa_logo), "Bonamassa Pizzaria", Modifier.size(52.dp).clip(CircleShape))
            else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title ?: "BONAMASSA", style = MaterialTheme.typography.titleMedium)
                Text("ENTREGAS", style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
            }
            Icon(Icons.Default.DeliveryDining, null, tint = Brand.Gold, modifier = Modifier.padding(8.dp).size(28.dp))
        }
        Box(Modifier.fillMaxWidth().background(Brand.Gold.copy(alpha = .10f)).padding(horizontal = 14.dp, vertical = 7.dp)) {
            Text("DEMONSTRAÇÃO · ENTREGAS DE EXEMPLO", style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = Brand.Surface, border = BorderStroke(1.dp, Brand.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PrimaryAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 54.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Button, contentColor = Brand.Cream),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(21.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun Tag(label: String, color: Color = Brand.Gold) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = .12f)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

fun statusColor(status: DeliveryStatus) = when (status) {
    DeliveryStatus.DELIVERED -> Brand.Green
    DeliveryStatus.ON_ROUTE -> Brand.Red
    DeliveryStatus.RETURNING, DeliveryStatus.RETURNED -> Brand.Muted
    else -> Brand.Gold
}

@Composable
fun DeliveryCard(delivery: Delivery, onOpen: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClickLabel = "Ver pedido ${delivery.orderNumber}", onClick = onOpen)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#${delivery.orderNumber}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Tag(delivery.status.label, statusColor(delivery.status))
        }
        Text(delivery.customer, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.LocationOn, null, tint = Brand.Gold, modifier = Modifier.size(20.dp))
            Text(delivery.address, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = Brand.Border)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                val returning = delivery.status == DeliveryStatus.RETURNING || delivery.status == DeliveryStatus.RETURNED
                Text(when {
                    returning -> "Tentativa sem sucesso"
                    delivery.amountToCollect == 0L -> "Pago · exemplo"
                    delivery.status == DeliveryStatus.DELIVERED -> "Recebido ${money(delivery.amountToCollect)}"
                    else -> "Cobrar ${money(delivery.amountToCollect)}"
                }, style = MaterialTheme.typography.titleMedium,
                    color = if (!returning && delivery.amountToCollect == 0L) Brand.Green else Brand.Cream)
                Text(if (!delivery.finished && !returning && delivery.payment == CollectionMethod.CASH && delivery.change > 0) "Troco: ${money(delivery.change)}" else delivery.payment.label,
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
            Icon(Icons.Default.ChevronRight, "Ver detalhes", tint = Brand.Gold)
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(72.dp).background(Brand.Raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(32.dp), tint = Brand.Gold)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun AmountLine(label: String, amount: Long, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Brand.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(money(amount), color = if (highlight) Brand.Gold else Brand.Cream,
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Brand.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
    }
}
