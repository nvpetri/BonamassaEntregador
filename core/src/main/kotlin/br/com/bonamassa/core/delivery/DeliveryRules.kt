package br.com.bonamassa.core.delivery

object DeliveryRules {
    private val transitions = mapOf(
        DeliveryStatus.ASSIGNED to setOf(DeliveryStatus.COLLECTED),
        DeliveryStatus.COLLECTED to setOf(DeliveryStatus.ON_ROUTE),
        DeliveryStatus.ON_ROUTE to setOf(DeliveryStatus.DELIVERED, DeliveryStatus.RETURNING),
        DeliveryStatus.RETURNING to setOf(DeliveryStatus.RETURNED)
    )

    fun validate(state: DriverState): DriverState = state.also {
        require(it.name.trim().length in 2..40) { "Informe um nome de 2 a 40 caracteres." }
        require(it.deliveries.size <= 200)
        require(it.deliveries.map(Delivery::id).distinct().size == it.deliveries.size)
        require(it.deliveries.map(Delivery::orderNumber).distinct().size == it.deliveries.size)
        it.deliveries.forEach(::validateDelivery)
    }

    private fun validateDelivery(delivery: Delivery) = with(delivery) {
        require(id.isNotBlank() && id.length <= 100 && orderNumber > 0)
        require(customer.isNotBlank() && customer.length <= 100)
        require(phone.length <= 24 && address.isNotBlank() && address.length <= 300 && reference.length <= 300)
        require(items.size in 1..50 && items.all { it.quantity in 1..20 && it.unitPrice in 0..1_000_000L && it.title.isNotBlank() })
        require(orderTotal in 1..10_000_000L && driverFee in 0..orderTotal)
        require(changeFor in 0..10_000_000L)
        require(if (payment == CollectionMethod.CASH) changeFor == 0L || changeFor >= orderTotal else changeFor == 0L)
        require(events.size in 1..5 && events.first().status == DeliveryStatus.ASSIGNED && events.last().status == status)
        require(events.all { it.at >= 0 && it.note.length <= 300 })
        events.zipWithNext().forEach { (before, after) ->
            require(after.at >= before.at && after.status in transitions[before.status].orEmpty())
        }
        if (status == DeliveryStatus.DELIVERED) {
            require(receiver.trim().length in 2..80)
            require(paymentCollected == (payment != CollectionMethod.PREPAID))
        } else {
            require(receiver.isEmpty() && !paymentCollected)
        }
        if (status == DeliveryStatus.RETURNING || status == DeliveryStatus.RETURNED) {
            require(issue != null && issueNote.length <= 280)
            require(issue != DeliveryIssue.OTHER || issueNote.trim().length >= 5)
        } else {
            require(issue == null && issueNote.isEmpty())
        }
    }

    private fun change(state: DriverState, id: String, block: (Delivery) -> Delivery): DriverState {
        validate(state)
        require(state.deliveries.any { it.id == id }) { "Entrega não encontrada." }
        return validate(state.copy(deliveries = state.deliveries.map { if (it.id == id) block(it) else it }))
    }

    private fun advance(delivery: Delivery, status: DeliveryStatus, now: Long, note: String = ""): Delivery {
        require(status in transitions[delivery.status].orEmpty()) { "Esta ação não está disponível na etapa atual." }
        require(now >= delivery.events.last().at) { "Confira a data e a hora do aparelho." }
        return delivery.copy(status = status, events = delivery.events + DeliveryEvent(status, now, note))
    }

    fun collect(state: DriverState, id: String, now: Long): DriverState {
        require(state.available) { "Ative sua disponibilidade para fazer uma nova coleta." }
        return change(state, id) { advance(it, DeliveryStatus.COLLECTED, now) }
    }

    fun start(state: DriverState, id: String, now: Long): DriverState =
        change(state, id) { advance(it, DeliveryStatus.ON_ROUTE, now) }

    fun complete(state: DriverState, id: String, receiver: String, paymentConfirmed: Boolean, now: Long): DriverState =
        change(state, id) { delivery ->
            val name = receiver.trim()
            require(name.length in 2..80) { "Informe quem recebeu, com 2 a 80 caracteres." }
            require(delivery.payment == CollectionMethod.PREPAID || paymentConfirmed) { "Confirme o recebimento do pagamento." }
            advance(delivery, DeliveryStatus.DELIVERED, now).copy(
                receiver = name,
                paymentCollected = delivery.payment != CollectionMethod.PREPAID
            )
        }

    fun reportIssue(state: DriverState, id: String, reason: DeliveryIssue, note: String, now: Long): DriverState =
        change(state, id) { delivery ->
            val detail = note.trim()
            require(detail.length <= 280) { "Use até 280 caracteres na observação." }
            require(reason != DeliveryIssue.OTHER || detail.length >= 5) { "Descreva o motivo com pelo menos 5 caracteres." }
            advance(delivery, DeliveryStatus.RETURNING, now, reason.label).copy(issue = reason, issueNote = detail)
        }

    fun returnToStore(state: DriverState, id: String, now: Long): DriverState =
        change(state, id) { advance(it, DeliveryStatus.RETURNED, now) }

    fun summary(state: DriverState): DriverSummary {
        val completed = state.deliveries.filter { it.status == DeliveryStatus.DELIVERED }
        return DriverSummary(
            completed.size,
            completed.sumOf(Delivery::driverFee),
            completed.filter { it.payment == CollectionMethod.CASH && it.paymentCollected }.sumOf(Delivery::orderTotal)
        )
    }
}
