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
    @Test fun nearestStopsUseVerifiedCoordinatesAndNextLegStartsAtPreviousDestination() {
        val origin = GeoPoint(-23.55, -46.63)
        val far = delivery(1, address.copy(number = "1")).copy(navigation = DeliveryNavigation(7000, address.copy(number = "100"), origin, GeoPoint(-23.57, -46.65)))
        val near = delivery(9, address.copy(number = "9")).copy(navigation = DeliveryNavigation(1000, address.copy(number = "100"), origin, GeoPoint(-23.551, -46.631)))
        val sameBuilding = delivery(10, near.address!!.copy(complement = "Apto 33")).copy(navigation = near.navigation)
        val legacy = delivery(2, address.copy(number = "2"))
        val stops = groupDeliveries(listOf(far, legacy, sameBuilding, near))
        assertEquals(listOf(listOf(near.id, sameBuilding.id), listOf(far.id), listOf(legacy.id)), stops.map { it.deliveries.map { order -> order.id } })
        assertEquals(1000L, stops.first().distanceMeters)
        val numbered = (1..7).map { n -> delivery(n, address.copy(number = n.toString())).copy(navigation = DeliveryNavigation(n * 1000L, address, origin, GeoPoint(-23.55 - n * 0.001, -46.63))) }
        val legs = routeLegs(groupDeliveries(numbered.reversed()))
        assertEquals(2, legs.size)
        assertEquals(origin.route, legs[0].url.toHttpUrl().queryParameter("origin"))
        assertEquals(legs[0].stops.last().destination, legs[1].url.toHttpUrl().queryParameter("origin"))
        assertEquals(numbered.map { it.id }, legs.flatMap { it.stops.flatMap { stop -> stop.deliveries.map { order -> order.id } } })
        assertEquals(numbered[3].navigation?.destination?.route, legs[0].url.toHttpUrl().queryParameter("destination"))
        assertEquals(numbered.take(3).map { it.navigation?.destination?.route }, legs[0].url.toHttpUrl().queryParameter("waypoints")?.split('|'))
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
