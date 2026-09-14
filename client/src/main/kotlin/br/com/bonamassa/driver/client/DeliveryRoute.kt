package br.com.bonamassa.driver.client

import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

data class DeliveryStop(val key: String, val address: Address?, val deliveries: List<Delivery>)
val Delivery.canStartRoute get() = status == "READY" && deliveryStatus in setOf("ASSIGNED", "COLLECTED")

/** A stop is a building/address; each apartment/customer remains a distinct delivery. */
fun groupDeliveries(deliveries: List<Delivery>): List<DeliveryStop> {
    fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    return deliveries.sortedWith(compareBy<Delivery> { it.createdAt }.thenBy { it.number }.thenBy { it.id })
        .groupBy { d -> d.address?.let { a ->
            // Length prefixes avoid collisions with delimiters entered in an address.
            listOf(a.street, a.number, a.neighborhood, a.city, a.state, a.postalCode.filter(Char::isDigit))
                .map(::normalize).joinToString("") { "${it.length}:$it" }
        } ?: "missing:${d.id}" }
        .map { (key, orders) -> DeliveryStop(key, orders.first().address, orders) }
}

data class RouteLeg(val url: String, val stops: List<DeliveryStop>)
/** Mobile browsers support 3 waypoints. Split visibly; never silently drop a stop. */
fun routeLegs(stops: List<DeliveryStop>): List<RouteLeg> {
    require(stops.isNotEmpty() && stops.all { it.address != null }) { "Confirme os endereços com a pizzaria antes de abrir a rota." }
    fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
    fun url(part: List<DeliveryStop>): String {
        val destination = encode(requireNotNull(part.last().address).route)
        val points = part.dropLast(1).joinToString("|") { requireNotNull(it.address).route }
        return "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=$destination" +
            (if (points.isEmpty()) "" else "&waypoints=${encode(points)}")
    }
    val result = mutableListOf<RouteLeg>()
    var part = mutableListOf<DeliveryStop>()
    for (stop in stops) {
        if (part.isNotEmpty() && (part.size == 4 || url(part + stop).length > 2048)) {
            result += RouteLeg(url(part), part.toList()); part = mutableListOf()
        }
        part += stop
        require(url(part).length <= 2048) { "Endereço longo demais para o Maps. Abra o pedido e confira o destino." }
    }
    if (part.isNotEmpty()) result += RouteLeg(url(part), part.toList())
    return result
}
