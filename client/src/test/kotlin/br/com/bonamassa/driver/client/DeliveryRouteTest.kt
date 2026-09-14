package br.com.bonamassa.driver.client

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DeliveryRouteTest {
    private val user = User(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Motoboy", "driver@test.example", "11999999999", "DRIVER", true, true, 1)
    private val endpoint = Endpoint.parse("https://api.example.com", "bonamassa", false)
    private val address = Address("Rua São João", "10", "Centro", "São Paulo", "SP", "01001000", "Portão azul", "Apto 12")
    private fun delivery(number: Int = 1, a: Address? = address) = Delivery(UUID.randomUUID().toString(), number, 5, "READY", "ASSIGNED", "Cliente $number", "11999999999", a, "", emptyList(), "CARD", false, null, 0, 7000, 800, 0, null, "2026-09-14T20:00:00Z", "2026-09-14T20:00:00Z", emptyList())

    @Test fun groupsSameBuildingPreservingApartmentsAndKeepsOtherNumbersCitiesSeparate() {
        val first = delivery()
        val second = delivery(2, address.copy(street = "  RUA SAO   JOAO ", complement = "Apto 22"))
        val third = delivery(3, address.copy(number = "100"))
        val fourth = delivery(4, address.copy(city = "Outra cidade"))
        val groups = groupDeliveries(listOf(fourth, third, second, first))
        assertEquals(3, groups.size)
        assertEquals(listOf(first.id, second.id), groups.first().deliveries.map { it.id })
        assertEquals(listOf("Apto 12", "Apto 22"), groups.first().deliveries.map { it.address?.complement })
        assertEquals(2, groupDeliveries(listOf(delivery(5, null), delivery(6, null))).size)
    }
    @Test fun mapsContainsAllStopsSplitsMobileLimitsAndEncodesAddresses() {
        val stops = groupDeliveries((1..10).map { delivery(it, address.copy(number = it.toString(), street = "Rua São & João")) })
        val legs = routeLegs(stops)
        assertEquals(listOf(4, 4, 2), legs.map { it.stops.size })
        assertEquals(stops, legs.flatMap { it.stops })
        legs.forEach { leg ->
            assertTrue(leg.url.length <= 2048)
            val url = leg.url.toHttpUrl()
            assertEquals("1", url.queryParameter("api"))
            assertEquals(leg.stops.last().address?.route, url.queryParameter("destination"))
            assertEquals(leg.stops.dropLast(1).map { it.address?.route }, url.queryParameter("waypoints")?.split('|') ?: emptyList<String>())
        }
        val longStops = groupDeliveries((1..5).map { delivery(it, address.copy(number = it.toString(), street = "ã".repeat(120))) })
        assertEquals(longStops, routeLegs(longStops).flatMap { it.stops })
        assertThrows(IllegalArgumentException::class.java) { routeLegs(groupDeliveries(listOf(delivery(a = null)))) }
    }
    @Test fun batchPersistsAllVersionsAndRetryUsesOneRequestWithTheSameKey() {
        val selected = listOf(delivery(), delivery(2).copy(deliveryStatus = "COLLECTED"))
        MockWebServer().use { server ->
            val target = Endpoint.parse(server.url("/").toString(), "bonamassa", true)
            val pending = Pending.route(selected, target, user)
            val saved = SavedState(target.origin, target.storeSlug, user, Session("session", "later", user), pending)
            val recovered = requireNotNull(SavedCodec.decode(SavedCodec.encode(saved)).pending)
            assertEquals(pending, recovered)
            assertEquals(selected.map { it.id }, recovered.routeIds)
            val json = JSONObject(recovered.body)
            assertTrue(json.getBoolean("confirmCollected"))
            assertEquals(5, json.getJSONArray("deliveries").getJSONObject(1).getInt("expectedVersion"))
            repeat(2) { server.enqueue(MockResponse().setBody("{\"items\":[]}")) }
            val api = DriverApi(target)
            repeat(2) { api.send("session", recovered) }
            repeat(2) {
                val sent = server.takeRequest()
                assertEquals("/v1/driver/routes/start", sent.path)
                assertEquals(pending.key, sent.getHeader("Idempotency-Key"))
                assertEquals(pending.body, sent.body.readUtf8())
            }
        }
    }
    @Test fun batchRejectsDuplicatesWrongStageAndUnconfirmedCollection() {
        val a = delivery()
        assertThrows(IllegalArgumentException::class.java) { Pending.route(listOf(a, a), endpoint, user) }
        assertThrows(IllegalArgumentException::class.java) { Pending.route(emptyList(), endpoint, user) }
        assertThrows(IllegalArgumentException::class.java) { Pending.route(listOf(a.copy(status = "DELIVERED")), endpoint, user) }
        assertThrows(IllegalArgumentException::class.java) { Pending.route(listOf(a), endpoint, user.copy(available = false)) }
        Pending.route(listOf(a.copy(deliveryStatus = "COLLECTED")), endpoint, user.copy(available = false)).validate()
        val p = Pending.route(listOf(a), endpoint, user)
        assertFalse(p.belongsTo(endpoint, user.copy(id = UUID.randomUUID().toString())))
        assertFalse(p.belongsTo(endpoint.copy(origin = "https://other.example.com"), user))
        assertThrows(IllegalArgumentException::class.java) { p.copy(body = JSONObject(p.body).put("confirmCollected", false).toString()).validate() }
    }
}
