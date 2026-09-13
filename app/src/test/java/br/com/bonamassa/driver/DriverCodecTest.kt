package br.com.bonamassa.driver

import br.com.bonamassa.core.delivery.*
import br.com.bonamassa.driver.data.DriverCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriverCodecTest {
    private val now = 1_800_000_000_000L
    private fun inRoute(): DriverState = DeliveryRules.start(DeliveryRules.collect(DriverDemo.seed(now), "demo-1043", now + 1), "demo-1043", now + 2)

    @Test fun seedRoundTripKeepsAllItemsAndPaymentDetails() {
        val state = DriverDemo.seed(now).copy(name = "Entregador demo", available = false)
        assertEquals(state, DriverCodec.decode(DriverCodec.encode(state)))
    }

    @Test fun completionSurvivesRestartWithoutLosingPaymentConfirmation() {
        val state = DeliveryRules.complete(inRoute(), "demo-1043", "Pessoa que recebeu", true, now + 3)
        val restored = DriverCodec.decode(DriverCodec.encode(state))
        assertEquals(state, restored)
        assertEquals(DriverSummary(1, 800, 9000), DeliveryRules.summary(restored))
    }

    @Test fun issueAndReturnHistorySurviveRestart() {
        val issue = DeliveryRules.reportIssue(inRoute(), "demo-1043", DeliveryIssue.OTHER, "Rua interditada", now + 3)
        val restored = DriverCodec.decode(DriverCodec.encode(issue))
        val returned = DeliveryRules.returnToStore(restored, "demo-1043", now + 4)
        assertEquals(returned, DriverCodec.decode(DriverCodec.encode(returned)))
    }

    @Test fun unsupportedSchemaAndUnknownStatusesAreRejected() {
        val root = JSONObject(DriverCodec.encode(DriverDemo.seed(now)))
        root.put("schema", 2)
        assertThrows(IllegalArgumentException::class.java) { DriverCodec.decode(root.toString()) }
        root.put("schema", 1)
        root.getJSONArray("deliveries").getJSONObject(0).put("status", "UNKNOWN")
        assertThrows(IllegalArgumentException::class.java) { DriverCodec.decode(root.toString()) }
    }

    @Test fun duplicateAndNegativeMoneySnapshotsAreRejected() {
        val root = JSONObject(DriverCodec.encode(DriverDemo.seed(now)))
        val list = root.getJSONArray("deliveries")
        list.getJSONObject(0).put("total", -1)
        assertThrows(IllegalArgumentException::class.java) { DriverCodec.decode(root.toString()) }
        list.getJSONObject(0).put("total", 8000)
        list.put(list.getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { DriverCodec.decode(root.toString()) }
    }

    @Test fun corruptedTimelineIsNotSilentlyAccepted() {
        val root = JSONObject(DriverCodec.encode(inRoute()))
        root.getJSONArray("deliveries").getJSONObject(1).getJSONArray("events").getJSONObject(2).put("at", now - 1)
        assertThrows(IllegalArgumentException::class.java) { DriverCodec.decode(root.toString()) }
    }

    @Test fun numericOverflowAndFractionalMoneyAreRejected() {
        val root = JSONObject(DriverCodec.encode(DriverDemo.seed(now)))
        root.getJSONArray("deliveries").getJSONObject(0).put("number", 4_294_968_338L)
        assertThrows(IllegalArgumentException::class.java) { DriverCodec.decode(root.toString()) }
        root.getJSONArray("deliveries").getJSONObject(0).put("number", 1042).put("total", 8000.5)
        assertThrows(IllegalArgumentException::class.java) { DriverCodec.decode(root.toString()) }
    }
}
