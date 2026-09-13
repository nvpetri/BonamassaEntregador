package br.com.bonamassa.core.delivery

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class DeliveryRulesTest {
    private val now = 1_800_000_000_000L
    private fun seed() = DriverDemo.seed(now)
    private fun route(id: String = "demo-1042"): DriverState = DeliveryRules.start(DeliveryRules.collect(seed(), id, now + 1), id, now + 2)
    private fun reject(action: () -> Unit) { assertThrows(IllegalArgumentException::class.java, action) }

    @Test fun pickupIsRequiredBeforeStarting() {
        reject { DeliveryRules.start(seed(), "demo-1042", now + 1) }
        assertEquals(DeliveryStatus.ON_ROUTE, route().deliveries.first().status)
    }

    @Test fun cannotCompleteBeforeStarting() {
        reject { DeliveryRules.complete(seed(), "demo-1042", "Cliente", true, now + 1) }
        val collected = DeliveryRules.collect(seed(), "demo-1042", now + 1)
        reject { DeliveryRules.complete(collected, "demo-1042", "Cliente", true, now + 2) }
    }

    @Test fun prepaidDeliveryDoesNotCountAsMoneyCollectedByDriver() {
        val done = DeliveryRules.complete(route(), "demo-1042", "  Recebedor demo  ", false, now + 3)
        val delivery = done.deliveries.first()
        assertEquals("Recebedor demo", delivery.receiver)
        assertEquals(0L, delivery.amountToCollect)
        assertFalse(delivery.paymentCollected)
        assertEquals(DriverSummary(1, 700, 0), DeliveryRules.summary(done))
    }

    @Test fun cashRequiresPaymentConfirmationAndKeepsChangeSeparateFromCollection() {
        val inRoute = route("demo-1043")
        val delivery = inRoute.deliveries[1]
        assertEquals(1000L, delivery.change)
        reject { DeliveryRules.complete(inRoute, delivery.id, "Cliente", false, now + 3) }
        val done = DeliveryRules.complete(inRoute, delivery.id, "Cliente", true, now + 3)
        assertEquals(DriverSummary(1, 800, 9000), DeliveryRules.summary(done))
    }

    @Test fun cardRequiresConfirmationButDoesNotCountAsCash() {
        val inRoute = route("demo-1044")
        reject { DeliveryRules.complete(inRoute, "demo-1044", "Cliente", false, now + 3) }
        val done = DeliveryRules.complete(inRoute, "demo-1044", "Cliente", true, now + 3)
        assertEquals(DriverSummary(1, 900, 0), DeliveryRules.summary(done))
    }

    @Test fun receiverIsRequired() {
        reject { DeliveryRules.complete(route(), "demo-1042", "   ", true, now + 3) }
        reject { DeliveryRules.complete(route(), "demo-1042", "a".repeat(81), true, now + 3) }
    }

    @Test fun finishedDeliveryCannotBeRecordedTwice() {
        val done = DeliveryRules.complete(route(), "demo-1042", "Cliente", false, now + 3)
        reject { DeliveryRules.complete(done, "demo-1042", "Cliente", false, now + 4) }
        reject { DeliveryRules.reportIssue(done, "demo-1042", DeliveryIssue.REFUSED, "", now + 4) }
        assertEquals(1, DeliveryRules.summary(done).delivered)
    }

    @Test fun pausePreventsNewPickupsButAllowsExistingDeliveriesToFinish() {
        reject { DeliveryRules.collect(seed().copy(available = false), "demo-1042", now + 1) }
        val picked = DeliveryRules.collect(seed(), "demo-1042", now + 1).copy(available = false)
        val started = DeliveryRules.start(picked, "demo-1042", now + 2)
        assertEquals(1, DeliveryRules.summary(DeliveryRules.complete(started, "demo-1042", "Cliente", false, now + 3)).delivered)
    }

    @Test fun multipleOrdersCanBeOutForDeliveryAtOnce() {
        var state = route()
        state = DeliveryRules.collect(state, "demo-1043", now + 3)
        state = DeliveryRules.start(state, "demo-1043", now + 4)
        assertEquals(2, state.deliveries.count { it.status == DeliveryStatus.ON_ROUTE })
    }

    @Test fun failedAttemptNeedsASeparateReturnConfirmation() {
        val returning = DeliveryRules.reportIssue(route(), "demo-1042", DeliveryIssue.CUSTOMER_ABSENT, "Sem resposta", now + 3)
        assertFalse(returning.deliveries.first().finished)
        assertEquals(DriverSummary(0, 0, 0), DeliveryRules.summary(returning))
        reject { DeliveryRules.complete(returning, "demo-1042", "Cliente", true, now + 4) }
        val returned = DeliveryRules.returnToStore(returning, "demo-1042", now + 4)
        assertTrue(returned.deliveries.first().finished)
        assertEquals(DeliveryStatus.RETURNED, returned.deliveries.first().status)
        assertEquals(5, returned.deliveries.first().events.size)
        assertEquals(0L, DeliveryRules.summary(returned).fees)
    }

    @Test fun issueAndReturnCannotSkipTheRoute() {
        reject { DeliveryRules.reportIssue(seed(), "demo-1042", DeliveryIssue.REFUSED, "", now + 1) }
        reject { DeliveryRules.returnToStore(route(), "demo-1042", now + 3) }
    }

    @Test fun otherIssueRequiresAnExplanation() {
        reject { DeliveryRules.reportIssue(route(), "demo-1042", DeliveryIssue.OTHER, "  ", now + 3) }
        reject { DeliveryRules.reportIssue(route(), "demo-1042", DeliveryIssue.OTHER, "a".repeat(281), now + 3) }
        val updated = DeliveryRules.reportIssue(route(), "demo-1042", DeliveryIssue.OTHER, " Acesso bloqueado ", now + 3)
        assertEquals("Acesso bloqueado", updated.deliveries.first().issueNote)
    }

    @Test fun backwardClockDoesNotRewriteHistory() {
        reject { DeliveryRules.collect(seed(), "demo-1042", now - 1) }
    }

    @Test fun unknownOrderAndDuplicateIdsAreRejected() {
        reject { DeliveryRules.collect(seed(), "missing", now + 1) }
        reject { DeliveryRules.validate(seed().copy(deliveries = seed().deliveries + seed().deliveries.first())) }
    }

    @Test fun invalidMoneyAndForgedCompletedStateAreRejected() {
        val state = seed()
        fun replace(delivery: Delivery) = state.copy(deliveries = listOf(delivery))
        reject { DeliveryRules.validate(replace(state.deliveries[1].copy(changeFor = 100))) }
        reject { DeliveryRules.validate(replace(state.deliveries[0].copy(driverFee = -1))) }
        reject { DeliveryRules.validate(replace(state.deliveries[0].copy(status = DeliveryStatus.DELIVERED))) }
    }

    @Test fun mapLinksEncodeAddressesInsteadOfInjectingExtraParameters() {
        val address = "Praça de exemplo, 10 & destination=outro # SP"
        val maps = URI(DeliveryLinks.googleMaps(address))
        val params = maps.rawQuery.split('&').associate { part -> part.substringBefore('=') to URLDecoder.decode(part.substringAfter('='), "UTF-8") }
        assertEquals("www.google.com", maps.host)
        assertEquals(address, params["destination"])
        assertEquals("1", params["api"])
        assertNull(maps.fragment)
        assertEquals(3, params.size)
        val waze = URI(DeliveryLinks.waze(address))
        assertEquals("waze.com", waze.host)
        assertEquals(2, waze.rawQuery.split('&').size)
    }

    @Test fun dialerRejectsEmptyAndServiceCodes() {
        assertNull(DeliveryLinks.dialNumber(""))
        assertNull(DeliveryLinks.dialNumber("*123#"))
        assertNull(DeliveryLinks.dialNumber("tel:11999999999"))
        assertEquals("+5511999999999", DeliveryLinks.dialNumber("+55 (11) 99999-9999"))
    }
}
