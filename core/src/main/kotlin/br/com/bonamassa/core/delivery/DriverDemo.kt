package br.com.bonamassa.core.delivery

/** Fictional orders, public destination examples and no customer phone numbers. */
object DriverDemo {
    fun seed(now: Long): DriverState = DeliveryRules.validate(DriverState(deliveries = listOf(
        Delivery(
            id = "demo-1042", orderNumber = 1042, customer = "Cliente A · demonstração", phone = "",
            address = "Praça da Sé, Sé, São Paulo - SP", reference = "Destino de exemplo. Não representa uma entrega real.",
            items = listOf(
                DeliveryItem("Pizza grande", "½ Calabresa · ½ Mussarela", 1, 5800, "Sem cebola"),
                DeliveryItem("Refrigerante 2 L", "Cola", 1, 1500, "")
            ),
            orderTotal = 8000, driverFee = 700, payment = CollectionMethod.PREPAID,
            events = listOf(DeliveryEvent(DeliveryStatus.ASSIGNED, now))
        ),
        Delivery(
            id = "demo-1043", orderNumber = 1043, customer = "Cliente B · demonstração", phone = "",
            address = "Praça da República, República, São Paulo - SP", reference = "Destino de exemplo. Conferir o troco antes de sair.",
            items = listOf(
                DeliveryItem("Frango cremoso", "Grande · borda tradicional", 1, 6800, ""),
                DeliveryItem("Guaraná em lata", "350 ml", 2, 700, "")
            ),
            orderTotal = 9000, driverFee = 800, payment = CollectionMethod.CASH, changeFor = 10000,
            events = listOf(DeliveryEvent(DeliveryStatus.ASSIGNED, now))
        ),
        Delivery(
            id = "demo-1044", orderNumber = 1044, customer = "Cliente C · demonstração", phone = "",
            address = "Praça da Liberdade, Liberdade, São Paulo - SP", reference = "Destino de exemplo. Levar a maquininha.",
            items = listOf(
                DeliveryItem("Portuguesa", "Família · borda tradicional", 1, 8600, ""),
                DeliveryItem("Brownie", "Unidade", 1, 1400, "")
            ),
            orderTotal = 10900, driverFee = 900, payment = CollectionMethod.CARD,
            events = listOf(DeliveryEvent(DeliveryStatus.ASSIGNED, now))
        )
    )))
}
