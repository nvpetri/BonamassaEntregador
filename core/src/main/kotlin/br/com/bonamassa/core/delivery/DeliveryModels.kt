package br.com.bonamassa.core.delivery

data class DeliveryItem(val title: String, val details: String, val quantity: Int, val unitPrice: Long, val note: String = "")

enum class DeliveryStatus(val label: String) {
    ASSIGNED("A retirar"), COLLECTED("Retirado"), ON_ROUTE("Em rota"),
    DELIVERED("Entregue"), RETURNING("Retornar à pizzaria"), RETURNED("Devolvido")
}

enum class CollectionMethod(val label: String) {
    PREPAID("Pago antecipadamente"), CASH("Dinheiro na entrega"), CARD("Cartão na entrega")
}

enum class DeliveryIssue(val label: String) {
    CUSTOMER_ABSENT("Cliente ausente"), ADDRESS_NOT_FOUND("Endereço não localizado"),
    REFUSED("Pedido recusado"), OTHER("Outro motivo")
}

data class DeliveryEvent(val status: DeliveryStatus, val at: Long, val note: String = "")

data class Delivery(
    val id: String,
    val orderNumber: Int,
    val customer: String,
    val phone: String,
    val address: String,
    val reference: String,
    val items: List<DeliveryItem>,
    val orderTotal: Long,
    val driverFee: Long,
    val payment: CollectionMethod,
    val changeFor: Long = 0,
    val status: DeliveryStatus = DeliveryStatus.ASSIGNED,
    val events: List<DeliveryEvent>,
    val receiver: String = "",
    val paymentCollected: Boolean = false,
    val issue: DeliveryIssue? = null,
    val issueNote: String = ""
) {
    val finished: Boolean get() = status == DeliveryStatus.DELIVERED || status == DeliveryStatus.RETURNED
    val amountToCollect: Long get() = if (payment == CollectionMethod.PREPAID) 0 else orderTotal
    val change: Long get() = if (payment == CollectionMethod.CASH && changeFor > 0) changeFor - orderTotal else 0
}

data class DriverState(
    val name: String = "Entregador",
    val available: Boolean = true,
    val deliveries: List<Delivery> = emptyList()
)

data class DriverSummary(val delivered: Int, val fees: Long, val cashCollected: Long)
