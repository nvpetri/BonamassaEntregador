package br.com.bonamassa.driver

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import br.com.bonamassa.driver.client.*
import br.com.bonamassa.driver.connected.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.UUID

/** Opt-in: uses only the isolated PostgreSQL/API store provisioned by CI. */
class DriverApiFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val endpoint = Endpoint.parse("http://10.0.2.2:3001", "bonamassa", true)
    private val api = DriverApi(endpoint)
    private val password = "Driver-ci-password-2026"
    private fun key() = UUID.randomUUID().toString()
    private fun waitText(text: String) = compose.waitUntil(60_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        try {
            compose.waitUntil(60_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Exception) {
            screenshot("falha-clique.png")
            throw AssertionError("Botão não disponível: $text", e)
        }
        val node = compose.onNodeWithText(text)
        if (compose.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        node.performClick()
    }
    private fun input(label: String, text: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(text) }
    private fun waitAvailable() = compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("availability") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (command in listOf("mkdir -p /data/local/tmp/bonamassa-screenshots", "screencap -p /data/local/tmp/bonamassa-screenshots/$name")) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        }
    }
    private fun manager(): Session = Decode.session(api.request("POST", "/v1/sessions", body = objectOf("storeSlug" to "bonamassa", "email" to "manager@teste.example", "password" to "Manager-ci-only-password-2026")))
    private fun createDriver(manager: Session, name: String): User {
        val user = Decode.user(api.request("POST", "/v1/staff/users", manager.accessToken,
            objectOf("email" to "driver-${key()}@teste.example", "password" to password, "name" to name, "phone" to "11922223333", "role" to "DRIVER"), key()))
        api.request("POST", "/v1/auth/email-verification/request", body = objectOf("storeSlug" to "bonamassa", "email" to user.email))
        api.request("POST", "/v1/auth/email-verification/confirm", body = objectOf("storeSlug" to "bonamassa", "email" to user.email, "code" to "123456"))
        return user
    }
    private fun staff(order: JSONObject, action: String, manager: Session, extra: JSONObject = JSONObject()): JSONObject = api.request("POST", "/v1/staff/orders/${order.getString("id")}/$action", manager.accessToken, extra.put("expectedVersion", order.getInt("version")), key())
    private fun order(manager: Session, driver: User, payment: String = "CASH", number: String = "10", complement: String = ""): JSONObject {
        val customerEmail = "customer-${key()}@teste.example"
        api.request("POST", "/v1/customers", body = objectOf("storeSlug" to "bonamassa", "email" to customerEmail, "password" to "Customer-ci-password-2026", "name" to "Cliente Teste", "phone" to "11912345678"))
        val customer = Decode.session(api.request("POST", "/v1/auth/email-verification/confirm", body = objectOf("storeSlug" to "bonamassa", "email" to customerEmail, "code" to "123456")))
        val quote = api.request("POST", "/v1/orders/quote", customer.accessToken, objectOf(
            "items" to JSONArray().put(objectOf("kind" to "PIZZA", "flavorIds" to JSONArray(listOf("calabresa", "frango")), "size" to "LARGE", "crust" to "CREAM", "quantity" to 1, "note" to "Sem cebola")),
            "mode" to "DELIVERY", "address" to objectOf("street" to "Rua do Teste", "number" to number, "neighborhood" to "Centro", "city" to "São Paulo", "state" to "SP", "postalCode" to "01001000", "reference" to "Portão azul", "complement" to complement, "noComplement" to complement.isBlank()),
            "payment" to payment, "cashTendered" to if (payment == "CASH") 10000 else null, "note" to "Chamar no portão", "promotionId" to null), key())
        var order = api.request("POST", "/v1/orders", customer.accessToken, objectOf("quoteId" to quote.getString("quoteId")), key())
        for (action in listOf("accept", "prepare", "ready")) order = staff(order, action, manager)
        return staff(order, "assign", manager, objectOf("driverId" to driver.id))
    }
    private fun openOrder(number: Int) {
        scrollQueue("#$number")
        compose.onNodeWithText("#$number").performClick()
    }
    private fun scrollQueue(text: String) {
        compose.waitUntil(30_000) {
            runCatching { compose.onNodeWithTag("api_queue").performScrollToNode(hasText(text)); true }.getOrDefault(false)
        }
    }
    private fun requireStatus(token: String, id: String, status: String): Delivery {
        val actual = api.delivery(token, id); assertEquals(status, actual.status); return actual
    }

    @Test fun assignedCashDeliveryUpdatesPanelAndRequiresExplicitHandoff() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bonamassaIntegration") == "true")
        val manager = manager(); val driver = createDriver(manager, "Nicolas Teste")
        val currentDriver = api.me(api.signIn(driver.email, password).accessToken)
        api.request("PATCH", "/v1/staff/drivers/${driver.id}/availability", manager.accessToken, objectOf("expectedVersion" to currentDriver.version, "available" to false), key())
        val secure = SecureStore(InstrumentationRegistry.getInstrumentation().targetContext)
        secure.write(SavedState(origin = endpoint.origin, slug = "bonamassa"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("Entrar nas entregas")
            input("E-mail", driver.email); input("Senha", password); click("Entrar nas entregas")
            waitText("Novas coletas pausadas"); waitAvailable()
            compose.onNodeWithTag("availability").performClick()
            waitText("Disponível para coletas"); waitAvailable()
            val auth = requireNotNull(secure.read()?.session)
            assertTrue(api.me(auth.accessToken).available)
            val created = order(manager, driver)
            val id = created.getString("id")
            openOrder(created.getInt("number"))
            waitText("Confirmar retirada")
            screenshot("entregador-pedido.png")
            assertEquals("Rua do Teste", api.delivery(auth.accessToken, id).address?.street)
            click("Confirmar retirada"); click("Retirei o pedido")
            waitText("Iniciar entrega")
            assertEquals("COLLECTED", api.delivery(auth.accessToken, id).deliveryStatus)
            // Pausing new collections cannot prevent completing a collected order.
            compose.onNodeWithContentDescription("Voltar").performClick()
            waitAvailable(); compose.onNodeWithTag("availability").performClick()
            waitText("Novas coletas pausadas"); waitAvailable()
            openOrder(created.getInt("number"))
            click("Iniciar entrega"); click("Sair para entrega")
            waitText("Confirmar entrega")
            requireStatus(auth.accessToken, id, "OUT_FOR_DELIVERY")
            scenario.recreate(); waitText("Confirmar entrega")
            click("Confirmar entrega")
            compose.onNodeWithText("Registrar entrega").assertIsNotEnabled()
            input("Quem recebeu?", "Recebedor Teste")
            compose.onNodeWithText("Registrar entrega").assertIsNotEnabled()
            compose.onNodeWithText("Confirmo que recebi", substring = true).performClick()
            click("Registrar entrega")
            waitText("Entrega concluída")
            val delivered = requireStatus(auth.accessToken, id, "DELIVERED")
            assertTrue(delivered.paymentRecorded); assertEquals("Recebedor Teste", delivered.recipient)
            assertEquals(delivered.driverFee, delivered.driverEarnings)
            val staffView = api.request("GET", "/v1/staff/orders/$id", manager.accessToken)
            assertEquals("DELIVERED", staffView.getString("status"))
            assertEquals("Recebedor Teste", staffView.getString("recipient"))
            assertEquals(1, delivered.events.count { it.action == "complete" })
            // Transient confirmation snackbars may cover content/footer touch targets.
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Registro confirmado pela pizzaria.").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Entrega concluída").performScrollTo()
            screenshot("entregador-concluido.png")
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithText("Histórico").performClick()
            waitText("1 entregas · 0 devoluções")
            compose.onNodeWithText("Perfil").performClick()
            click("Sair da conta")
            click("Sair agora")
            waitText("Entrar nas entregas")
            // Local sign-out renders first; wait for the server revocation to finish too.
            compose.waitUntil(30_000) { compose.onAllNodes(hasText("E-mail") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            assertNull(secure.read()?.session)
            try { api.me(auth.accessToken); fail("Sessão deveria estar revogada") } catch (e: ApiFailure) { assertEquals(401, e.status) }
        }
    }

    @Test fun groupedStopsStartAllOrdersTogetherAndRetryNeverDuplicatesTheirEvents() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bonamassaIntegration") == "true")
        val manager = manager(); val driver = createDriver(manager, "Motoboy Rota Conjunta")
        var auth = api.signIn(driver.email, password)
        api.send(auth.accessToken, Pending.availability(true, endpoint, auth.user))
        auth = auth.copy(user = api.me(auth.accessToken))
        val a = order(manager, driver, complement = "Apto 12")
        val b = order(manager, driver, complement = "Apto 22")
        val c = order(manager, driver, "CARD", number = "20")
        val selected = listOf(a, b, c).map { api.delivery(auth.accessToken, it.getString("id")) }
        val secure = SecureStore(InstrumentationRegistry.getInstrumentation().targetContext)
        secure.write(SavedState(endpoint.origin, "bonamassa", auth.user, auth))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("Disponível para coletas")
            scrollQueue("2 pedido(s) neste endereço")
            compose.onNodeWithText("2 pedido(s) neste endereço").assertExists()
            scrollQueue("Iniciar rota com 3 pedidos")
            click("Iniciar rota com 3 pedidos")
            waitText("Conferir saída conjunta")
            compose.onNodeWithText("Confirmar e iniciar todas").assertIsNotEnabled()
            assertTrue(selected.all { api.delivery(auth.accessToken, it.id).deliveryStatus == "ASSIGNED" })
            compose.onNodeWithText("Conferi e retirei todos os pedidos desta lista").performScrollTo().performClick()
            click("Confirmar e iniciar todas")
            compose.waitUntil(30_000) { secure.read()?.pending == null && selected.all { api.delivery(auth.accessToken, it.id).deliveryStatus == "ON_ROUTE" } }
            scrollQueue("Abrir rota no Google Maps")
            compose.onNodeWithText("Abrir rota no Google Maps").assertExists()
            screenshot("entregador-rota-conjunta.png")
            val active = selected.map { api.delivery(auth.accessToken, it.id) }
            assertEquals(2, groupDeliveries(active).size)
            assertEquals(listOf("Apto 12", "Apto 22", ""), active.map { it.address?.complement })
            for (d in active) {
                assertEquals("OUT_FOR_DELIVERY", api.request("GET", "/v1/staff/orders/${d.id}", manager.accessToken).getString("status"))
                assertEquals(1, d.events.count { it.action == "start" })
            }
            scenario.recreate(); waitText("Disponível para coletas")
            // Complete only one order; the other apartment and other address remain on route.
            val done = Decode.delivery(api.send(auth.accessToken, Pending.delivery(active[0], Command.COMPLETE, endpoint, auth.user, "Morador", true)))
            assertEquals("DELIVERED", done.status)
            assertEquals("OUT_FOR_DELIVERY", api.delivery(auth.accessToken, active[1].id).status)
            assertEquals("OUT_FOR_DELIVERY", api.delivery(auth.accessToken, active[2].id).status)
        }
        // The durable batch itself also recovers from an acknowledgement lost before restart.
        val d = order(manager, driver, "CARD", number = "30")
        val pending = Pending.route(listOf(api.delivery(auth.accessToken, d.getString("id"))), endpoint, auth.user)
        secure.write(SavedState(endpoint.origin, "bonamassa", auth.user, auth, pending))
        api.send(auth.accessToken, pending)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Envio aguardando confirmação"); click("Verificar envio")
            compose.waitUntil(30_000) { secure.read()?.pending == null }
            assertEquals(1, api.delivery(auth.accessToken, d.getString("id")).events.count { event -> event.action == "start" })
        }
    }

    @Test fun reassignmentsRetriesReturnsAndExpiredSessionsStayConsistent() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bonamassaIntegration") == "true")
        val manager = manager(); val driver = createDriver(manager, "Entregador Recuperação"); val other = createDriver(manager, "Outro Entregador")
        var auth = api.signIn(driver.email, password)
        val second = api.signIn(other.email, password)
        api.send(auth.accessToken, Pending.availability(true, endpoint, auth.user)); auth = auth.copy(user = api.me(auth.accessToken))
        api.send(second.accessToken, Pending.availability(true, endpoint, second.user))
        val secure = SecureStore(InstrumentationRegistry.getInstrumentation().targetContext)
        secure.write(SavedState(endpoint.origin, "bonamassa", auth.user, auth))
        var reassigned = order(manager, driver)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Suas entregas"); openOrder(reassigned.getInt("number"))
            reassigned = staff(reassigned, "assign", manager, objectOf("driverId" to other.id))
            waitText("Nenhuma entrega aqui")
            compose.onNodeWithText("Pedido #${reassigned.getInt("number")}").assertDoesNotExist()
            try { api.delivery(auth.accessToken, reassigned.getString("id")); fail() } catch (e: ApiFailure) { assertEquals(404, e.status) }
        }
        val created = order(manager, driver, "CARD")
        val id = created.getString("id")
        val collected = Pending.delivery(api.delivery(auth.accessToken, id), Command.COLLECT, endpoint, auth.user)
        // Simulate loss of the HTTP acknowledgement followed by process death.
        secure.write(SavedState(endpoint.origin, "bonamassa", auth.user, auth, collected))
        val result = Decode.delivery(api.send(auth.accessToken, collected))
        // Advance on another device: replay must not permanently roll the UI back to COLLECTED.
        val started = Decode.delivery(api.send(auth.accessToken, Pending.delivery(result, Command.START, endpoint, auth.user)))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Envio aguardando confirmação"); click("Verificar envio")
            compose.waitUntil(30_000) { secure.read()?.pending == null }
            openOrder(created.getInt("number")); waitText("Confirmar entrega")
            assertEquals(1, api.delivery(auth.accessToken, id).events.count { e -> e.action == "collect" })
            // Backend rejects stale versions. The client must preserve a pending write until the rejection is known.
            try { api.send(auth.accessToken, Pending.delivery(result, Command.START, endpoint, auth.user)); fail() }
            catch (e: ApiFailure) { assertEquals(409, e.status); assertTrue(e.definitive) }
            click("Não consegui entregar")
            compose.onNodeWithText("Registrar tentativa").assertIsNotEnabled()
            input("Motivo da tentativa", "Cliente ausente no endereço")
            click("Registrar tentativa"); waitText("Confirmar devolução")
            assertEquals("RETURNING", api.delivery(auth.accessToken, id).status)
            click("Confirmar devolução"); click("Devolvi à pizzaria")
            waitText("Devolvido")
            val returned = requireStatus(auth.accessToken, id, "RETURNED")
            assertEquals(0L, returned.driverEarnings); assertFalse(returned.paymentRecorded)
            val view = api.request("GET", "/v1/staff/orders/$id", manager.accessToken)
            val issue = view.getJSONArray("events").let { events -> (0 until events.length()).map { events.getJSONObject(it) }.single { e -> e.getString("action") == "issue" } }
            assertEquals("Cliente ausente no endereço", issue.getJSONObject("data").getString("reason"))
        }
        // An ambiguous PATCH survives a cold launch and an expired session; re-login resumes only this driver.
        val pending = Pending.availability(false, endpoint, auth.user)
        secure.write(SavedState(endpoint.origin, "bonamassa", auth.user, auth, pending))
        api.logout(auth.accessToken)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Entrar nas entregas"); assertEquals(pending, secure.read()?.pending)
            input("E-mail", driver.email); input("Senha", password); click("Entrar nas entregas")
            waitText("Verificar envio"); click("Verificar envio")
            waitText("Novas coletas pausadas")
            compose.waitUntil(30_000) { secure.read()?.pending == null }
            auth = requireNotNull(secure.read()?.session)
            assertFalse(api.me(auth.accessToken).available)
        }
        assertEquals("OUT_FOR_DELIVERY", started.status)
    }
}
