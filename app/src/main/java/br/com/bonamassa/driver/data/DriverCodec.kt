package br.com.bonamassa.driver.data

import br.com.bonamassa.core.delivery.*
import org.json.JSONArray
import org.json.JSONObject

object DriverCodec {
    private fun json(block: JSONObject.() -> Unit) = JSONObject().apply(block)
    private fun <T> array(items: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(encode(it)) } }
    private fun <T> JSONArray.objects(decode: (JSONObject) -> T) = (0 until length()).map { decode(getJSONObject(it)) }
    private fun JSONObject.integer(key: String): Long {
        val value = get(key)
        require(value is Number) { "Campo numérico inválido: $key" }
        return requireNotNull(value.toString().toLongOrNull()) { "Campo inteiro inválido: $key" }
    }
    private fun JSONObject.intValue(key: String): Int = integer(key).also {
        require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
    }.toInt()

    fun encode(state: DriverState): String {
        DeliveryRules.validate(state)
        return json {
            put("schema", 1)
            put("name", state.name)
            put("available", state.available)
            put("deliveries", array(state.deliveries) { d -> json {
                put("id", d.id); put("number", d.orderNumber); put("customer", d.customer); put("phone", d.phone)
                put("address", d.address); put("reference", d.reference)
                put("total", d.orderTotal); put("fee", d.driverFee); put("payment", d.payment.name); put("changeFor", d.changeFor)
                put("status", d.status.name); put("receiver", d.receiver); put("paymentCollected", d.paymentCollected)
                put("issue", d.issue?.name ?: ""); put("issueNote", d.issueNote)
                put("items", array(d.items) { item -> json {
                    put("title", item.title); put("details", item.details); put("quantity", item.quantity)
                    put("unitPrice", item.unitPrice); put("note", item.note)
                } })
                put("events", array(d.events) { event -> json {
                    put("status", event.status.name); put("at", event.at); put("note", event.note)
                } })
            } })
        }.toString()
    }

    fun decode(text: String): DriverState {
        require(text.length <= 2_000_000) { "Arquivo de dados muito grande." }
        val root = JSONObject(text)
        require(root.intValue("schema") == 1) { "Versão dos dados não suportada. Atualize o aplicativo." }
        val list = root.getJSONArray("deliveries")
        require(list.length() <= 200)
        return DeliveryRules.validate(DriverState(
            name = root.getString("name"), available = root.getBoolean("available"),
            deliveries = list.objects { d -> Delivery(
                id = d.getString("id"), orderNumber = d.intValue("number"), customer = d.getString("customer"), phone = d.getString("phone"),
                address = d.getString("address"), reference = d.getString("reference"),
                items = d.getJSONArray("items").objects { item -> DeliveryItem(
                    item.getString("title"), item.getString("details"), item.intValue("quantity"), item.integer("unitPrice"), item.getString("note")
                ) },
                orderTotal = d.integer("total"), driverFee = d.integer("fee"), payment = CollectionMethod.valueOf(d.getString("payment")),
                changeFor = d.integer("changeFor"), status = DeliveryStatus.valueOf(d.getString("status")),
                events = d.getJSONArray("events").objects { e -> DeliveryEvent(DeliveryStatus.valueOf(e.getString("status")), e.integer("at"), e.getString("note")) },
                receiver = d.getString("receiver"), paymentCollected = d.getBoolean("paymentCollected"),
                issue = d.getString("issue").ifBlank { null }?.let(DeliveryIssue::valueOf), issueNote = d.getString("issueNote")
            ) }
        ))
    }
}
