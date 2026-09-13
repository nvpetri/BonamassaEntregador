package br.com.bonamassa.driver.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DriverContractTest {
    private val user = User(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Entregador", "driver@teste.example", "11999999999", "DRIVER", true, true, 3)
    private val endpoint = Endpoint.parse("http://192.168.1.10:3001", "bonamassa", true)
    private fun delivery(status: String = "READY", stage: String = "ASSIGNED") = Delivery(UUID.randomUUID().toString(), 101, 4, status, stage,
        "Cliente", "11999999999", null, "", emptyList(), "CASH", false, 10000, 3800, 6200, 700, 0, null, "2026-09-13T18:00:00Z", "2026-09-13T18:00:00Z", emptyList())
    private inline fun rejected(block: () -> Unit) { try { block(); fail("Deveria rejeitar") } catch (_: IllegalArgumentException) { } }
    @Test fun endpointRejectsCredentialsPathsAndInsecureRelease() {
        for (url in listOf("http://host:3001", "https://user:pass@host", "https://host/v1", "https://host/?token=secret", "https://host/#x")) rejected { Endpoint.parse(url, "bonamassa", false) }
        rejected { Endpoint.parse("https://host", "../outra-loja", false) }
        assertEquals("https://host", Endpoint.parse(" https://host/ ", "bonamassa", false).origin)
    }
    @Test fun transitionsMatchServerAndFinishedOrdersAreReadOnly() {
        assertEquals(listOf(Command.COLLECT), delivery().commands)
        assertEquals(listOf(Command.START), delivery(stage = "COLLECTED").commands)
        assertEquals(listOf(Command.COMPLETE, Command.ISSUE), delivery("OUT_FOR_DELIVERY", "ON_ROUTE").commands)
        assertEquals(listOf(Command.RETURN), delivery("RETURNING", "RETURNING").commands)
        for (status in listOf("DELIVERED", "RETURNED", "CANCELLED")) assertTrue(delivery(status, status).commands.isEmpty())
        assertTrue(delivery("READY", "ON_ROUTE").commands.isEmpty())
    }
    @Test fun collectionRequiresAvailabilityButAlreadyCollectedCanStartPaused() {
        rejected { Pending.delivery(delivery(), Command.COLLECT, endpoint, user.copy(available = false)) }
        Pending.delivery(delivery(stage = "COLLECTED"), Command.START, endpoint, user.copy(available = false)).validate()
        rejected { Pending.delivery(delivery(), Command.COMPLETE, endpoint, user, "Cliente", true) }
    }
    @Test fun cashAndCardCompletionRequireRecipientAndExplicitPayment() {
        for (method in listOf("CASH", "CARD")) {
            val d = delivery("OUT_FOR_DELIVERY", "ON_ROUTE").copy(payment = method)
            rejected { Pending.delivery(d, Command.COMPLETE, endpoint, user, "Cliente", false) }
            rejected { Pending.delivery(d, Command.COMPLETE, endpoint, user, "  ", true) }
            val body = JSONObject(Pending.delivery(d, Command.COMPLETE, endpoint, user, " Cliente ", true).body)
            assertEquals("Cliente", body.getString("recipient")); assertTrue(body.getBoolean("paymentCollected"))
        }
    }
    @Test fun RecordedPaymentAndZeroTotalDoNotRequireCollectingAgain() {
        val d = delivery("OUT_FOR_DELIVERY", "ON_ROUTE")
        for (order in listOf(d.copy(paymentRecorded = true), d.copy(total = 0))) {
            val p = Pending.delivery(order, Command.COMPLETE, endpoint, user, "Recebedor")
            assertFalse(JSONObject(p.body).getBoolean("paymentCollected"))
        }
    }
    @Test fun failedAttemptRequiresReasonAndOnlyAllowsReturnAfterwards() {
        rejected { Pending.delivery(delivery("OUT_FOR_DELIVERY", "ON_ROUTE"), Command.ISSUE, endpoint, user, reason = " ") }
        val p = Pending.delivery(delivery("OUT_FOR_DELIVERY", "ON_ROUTE"), Command.ISSUE, endpoint, user, reason = " Cliente ausente ")
        assertEquals("Cliente ausente", JSONObject(p.body).getString("reason"))
        assertEquals(listOf(Command.RETURN), delivery("RETURNING", "RETURNING").commands)
    }
    @Test fun pendingPatchSurvivesSerializationWithoutChangingVersionOrKey() {
        val pending = Pending.availability(false, endpoint, user)
        val saved = SavedState(endpoint.origin, endpoint.storeSlug, user, Session("secret", "2026-09-14T00:00:00Z", user), pending)
        assertEquals(saved, SavedCodec.decode(SavedCodec.encode(saved)))
        assertEquals("PATCH", pending.method); assertEquals(3, JSONObject(pending.body).getInt("expectedVersion"))
    }
    @Test fun pendingDeliverySurvivesLogoutAndCanOnlyResumeSameAccountAndServer() {
        val p = Pending.delivery(delivery(), Command.COLLECT, endpoint, user)
        val saved = SavedState(endpoint.origin, endpoint.storeSlug, user, pending = p)
        val roundtrip = SavedCodec.decode(SavedCodec.encode(saved))
        assertEquals(p, roundtrip.pending)
        val auth = Session("token", "later", user)
        assertEquals(p, roundtrip.signedIn(auth).pending)
        rejected { roundtrip.signedIn(auth.copy(user = user.copy(id = UUID.randomUUID().toString()))) }
        rejected { roundtrip.signedIn(auth.copy(user = user.copy(storeId = UUID.randomUUID().toString()))) }
        rejected { roundtrip.copy(origin = "https://other.example").signedIn(auth) }
        assertFalse(p.belongsTo(endpoint, user.copy(role = "MANAGER")))
    }
    @Test fun malformedOrUnsupportedStorageNeverSilentlyResetsPending() {
        rejected { SavedCodec.decode(SavedCodec.encode(SavedState()).replace("\"version\":1", "\"version\":99")) }
        rejected { Pending.availability(true, endpoint, user).copy(method = "DELETE").validate() }
        rejected { Pending.delivery(delivery(), Command.COLLECT, endpoint, user).copy(path = "/v1/staff/orders/secret/complete").validate() }
    }
    @Test fun retryPreservesExactRequestIncludingPatchAndAuthorization() {
        MockWebServer().use { server ->
            server.start()
            val e = Endpoint.parse(server.url("/").toString(), "bonamassa", true)
            val api = DriverApi(e)
            val pending = Pending.availability(true, e, user)
            repeat(2) { server.enqueue(MockResponse().setBody("{\"id\":\"${user.id}\",\"available\":true,\"version\":4}")) }
            repeat(2) { api.send("token", pending) }
            val a = server.takeRequest(); val b = server.takeRequest()
            assertEquals("PATCH", a.method); assertEquals("/v1/driver/availability", a.path)
            assertEquals("Bearer token", a.getHeader("Authorization"))
            assertEquals(pending.key, a.getHeader("Idempotency-Key")); assertEquals(a.getHeader("Idempotency-Key"), b.getHeader("Idempotency-Key"))
            assertEquals(pending.body, a.body.readUtf8()); assertEquals(pending.body, b.body.readUtf8())
        }
    }
    @Test fun serverErrorKeepsPendingWhileBusinessRejectionCanClearIt() {
        for (status in listOf(401, 408, 429, 500, 502, 503)) assertFalse(ApiFailure(status, "ERROR", "error", null).definitive)
        for (status in listOf(400, 404, 409, 422)) assertTrue(ApiFailure(status, "VERSION_CONFLICT", "error", null).definitive)
        assertFalse(ApiFailure(409, "IDEMPOTENCY_CONFLICT", "error", null).definitive)
    }
    @Test fun listEncodesCursorAndUsesDriverScope() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("{\"items\":[],\"nextCursor\":\"abc\"}"))
            val api = DriverApi(Endpoint.parse(server.url("/").toString(), "bonamassa", true))
            assertEquals("abc", api.deliveries("token", "a+/=&", "RETURNING").nextCursor)
            val request = server.takeRequest()
            assertEquals("/v1/driver/deliveries", request.requestUrl!!.encodedPath)
            assertEquals("a+/=&", request.requestUrl!!.queryParameter("cursor"))
            assertEquals("RETURNING", request.requestUrl!!.queryParameter("status"))
        }
    }
    @Test fun loginRejectsOtherRolesAndRevokesIssuedToken() {
        MockWebServer().use { server ->
            server.start()
            val other = user.copy(role = "MANAGER")
            val response = JSONObject(SavedCodec.encode(SavedState(session = Session("staff-secret", "later", other)))).getJSONObject("session")
            server.enqueue(MockResponse().setBody(response.toString())); server.enqueue(MockResponse().setBody("{}"))
            try { DriverApi(Endpoint.parse(server.url("/").toString(), "bonamassa", true)).signIn("manager@teste.example", "pw"); fail() }
            catch (e: ApiFailure) { assertEquals("DRIVER_ONLY", e.code) }
            assertEquals("/v1/sessions", server.takeRequest().path)
            val revoke = server.takeRequest(); assertEquals("DELETE", revoke.method); assertEquals("Bearer staff-secret", revoke.getHeader("Authorization"))
        }
    }
    @Test fun authenticatedRequestsDoNotFollowRedirects() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/leak")))
            val api = DriverApi(Endpoint.parse(server.url("/").toString(), "bonamassa", true))
            try { api.me("private-token"); fail() } catch (e: ApiFailure) { assertEquals(307, e.status) }
            assertEquals(1, server.requestCount)
        }
    }
}
