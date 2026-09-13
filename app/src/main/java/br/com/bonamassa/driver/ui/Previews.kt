package br.com.bonamassa.driver.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import br.com.bonamassa.core.delivery.DriverDemo

@Preview(name = "Fila · 360 dp", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun QueuePreview() = DriverTheme { QueueScreen(DriverDemo.seed(1_800_000_000_000L), false, {}, {}) }

@Preview(name = "Pagamento em dinheiro", widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun DeliveryPreview() = DriverTheme { DeliveryDetails(DriverDemo.seed(1_800_000_000_000L).deliveries[1], false, {}, {}, {}, {}) }

@Preview(name = "Histórico", widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun HistoryPreview() = DriverTheme { HistoryScreen(DriverDemo.seed(1_800_000_000_000L), {}) }
