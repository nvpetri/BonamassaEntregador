package br.com.bonamassa.driver.client

import org.json.JSONArray
import org.json.JSONObject

fun objectOf(vararg values: Pair<String, Any?>) = JSONObject().apply { values.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else getString(key)
private fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> = (0 until length()).map { read(getJSONObject(it)) }

data class User(val id: String, val storeId: String, val name: String, val email: String, val phone: String,
    val role: String, val enabled: Boolean, val available: Boolean, val version: Int)
data class Session(val accessToken: String, val expiresAt: String, val user: User)
data class Address(val street: String, val number: String, val neighborhood: String, val city: String, val state: String, val postalCode: String, val reference: String,
    val complement: String = "", val noComplement: Boolean = false) {
    val route get() = "$street, $number, $neighborhood, $city - $state, $postalCode, Brasil"
}
data class Item(val name: String, val detail: String, val note: String, val quantity: Int, val components: List<Item> = emptyList())
data class Event(val action: String, val version: Int, val createdAt: String)
enum class Command(val path: String, val label: String) {
    COLLECT("collect", "Confirmar retirada"), START("start", "Iniciar entrega"),
    COMPLETE("complete", "Confirmar entrega"), ISSUE("issue", "Não consegui entregar"), RETURN("return", "Confirmar devolução")
}
data class Delivery(val id: String, val number: Int, val version: Int, val status: String, val deliveryStatus: String?,
    val customer: String, val phone: String, val address: Address?, val note: String, val items: List<Item>,
    val payment: String, val paymentRecorded: Boolean, val cashTendered: Long?, val change: Long,
    val total: Long, val driverFee: Long, val driverEarnings: Long, val recipient: String?,
    val createdAt: String, val updatedAt: String, val events: List<Event>) {
    val active get() = status !in setOf("DELIVERED", "RETURNED", "CANCELLED")
    val needsPayment get() = !paymentRecorded && total > 0
    val label get() = when (deliveryStatus) {
        "ASSIGNED" -> "A retirar"; "COLLECTED" -> "Retirado"; "ON_ROUTE" -> "Em rota"
        "RETURNING" -> "Retornar à pizzaria"; "DELIVERED" -> "Entregue"; "RETURNED" -> "Devolvido"
        else -> if (status == "CANCELLED") "Cancelado" else "Aguardando atualização"
    }
    val commands get() = when {
        status == "READY" && deliveryStatus == "ASSIGNED" -> listOf(Command.COLLECT)
        status == "READY" && deliveryStatus == "COLLECTED" -> listOf(Command.START)
        status == "OUT_FOR_DELIVERY" && deliveryStatus == "ON_ROUTE" -> listOf(Command.COMPLETE, Command.ISSUE)
        status == "RETURNING" && deliveryStatus == "RETURNING" -> listOf(Command.RETURN)
        else -> emptyList()
    }
}
data class Page(val items: List<Delivery>, val nextCursor: String?)

object Decode {
    fun user(j: JSONObject) = User(j.getString("id"), j.getString("storeId"), j.getString("name"), j.getString("email"),
        j.optString("phone", ""), j.getString("role"), j.getBoolean("enabled"), j.getBoolean("available"), j.getInt("version"))
    fun session(j: JSONObject) = Session(j.getString("accessToken"), j.getString("expiresAt"), user(j.getJSONObject("user")))
    private fun item(j: JSONObject): Item = Item(j.getString("name"), j.optString("detail", ""), j.optString("note", ""),
        j.getInt("quantity"), j.optJSONArray("components")?.objects(::item) ?: emptyList())
    fun delivery(j: JSONObject): Delivery {
        val customer = j.getJSONObject("customer")
        return Delivery(j.getString("id"), j.getInt("number"), j.getInt("version"), j.getString("status"), j.textOrNull("deliveryStatus"),
            customer.getString("name"), customer.getString("phone"), j.optJSONObject("address")?.let {
                Address(it.getString("street"), it.getString("number"), it.getString("neighborhood"), it.getString("city"), it.getString("state"), it.getString("postalCode"), it.optString("reference", ""), it.optString("complement", ""), it.optBoolean("noComplement", false))
            }, j.optString("note", ""), j.getJSONArray("items").objects(::item), j.getString("payment"), j.getBoolean("paymentRecorded"),
            if (j.isNull("cashTendered")) null else j.getLong("cashTendered"), j.getLong("change"), j.getLong("total"), j.getLong("driverFee"), j.getLong("driverEarnings"),
            j.textOrNull("recipient"), j.getString("createdAt"), j.getString("updatedAt"), j.getJSONArray("events").objects { Event(it.getString("action"), it.getInt("version"), it.getString("createdAt")) })
    }
    fun page(j: JSONObject) = Page(j.getJSONArray("items").objects(::delivery), j.textOrNull("nextCursor"))
}
