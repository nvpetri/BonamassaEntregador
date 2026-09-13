package br.com.bonamassa.driver

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import br.com.bonamassa.core.delivery.DeliveryLinks

object ExternalActions {
    private fun launch(context: Context, intent: Intent, onError: (String) -> Unit) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            onError("Nenhum aplicativo disponível para abrir esta ação.")
        } catch (_: SecurityException) {
            onError("O aparelho bloqueou esta ação. Confira as configurações.")
        }
    }

    fun route(context: Context, address: String, waze: Boolean, onError: (String) -> Unit) =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(if (waze) DeliveryLinks.waze(address) else DeliveryLinks.googleMaps(address))), onError)

    fun dial(context: Context, phone: String, onError: (String) -> Unit) {
        val number = DeliveryLinks.dialNumber(phone)
        if (number == null) { onError("Telefone não informado ou inválido."); return }
        launch(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)), onError)
    }
}
