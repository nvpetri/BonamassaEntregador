package br.com.bonamassa.driver

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.bonamassa.driver.data.LocalDriverRepository
import br.com.bonamassa.driver.ui.money
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Run only on a demo device: each test resets this app's local demonstration data. */
class DeliveryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetDemo() = runBlocking { LocalDriverRepository(compose.activity).reset() }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun cashDeliverySurvivesActivityRecreationAndRequiresPaymentConfirmation() {
        awaitText("Suas entregas")
        compose.onNodeWithTag("delivery_queue").performScrollToNode(hasText("#1043"))
        compose.onNodeWithText("#1043").performClick()
        compose.onNodeWithText("Confirmar retirada").performClick()
        compose.onNodeWithText("Retirei o pedido").performClick()
        awaitText("Iniciar entrega")
        compose.onNodeWithText("Iniciar entrega").performClick()
        awaitText("Confirmar entrega")
        compose.activityRule.scenario.recreate()
        awaitText("Confirmar entrega")
        compose.onNodeWithText("Confirmar entrega").performClick()
        compose.onNodeWithText("Registrar entrega").assertIsNotEnabled()
        compose.onNodeWithText("Quem recebeu?").performTextInput("Recebedor demo")
        compose.onNodeWithText("Registrar entrega").assertIsNotEnabled()
        compose.onNodeWithText("Confirmo que recebi", substring = true).performClick()
        compose.onNodeWithText("Registrar entrega").assertIsEnabled().performClick()
        awaitText("Entrega concluída.")
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Histórico").performClick()
        compose.onNodeWithText(money(800)).assertExists()
        compose.onNodeWithText(money(9000)).assertExists()
    }
}
