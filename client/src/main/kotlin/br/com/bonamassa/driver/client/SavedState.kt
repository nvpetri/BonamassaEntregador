package br.com.bonamassa.driver.client

import org.json.JSONObject
import java.util.UUID

/** Persisted before sending. A retry always uses the original method, body and key. */
data class Pending(val method: String, val path: String, val body: String, val key: String, val ownerId: String, val storeId: String, val origin: String) {
    val availability get() = path == "/v1/driver/availability"
    val deliveryId get() = if (availability) null else path.split('/')[4]
    fun belongsTo(endpoint: Endpoint, user: User) = user.role == "DRIVER" && ownerId == user.id && storeId == user.storeId && origin == endpoint.origin
    fun validate() {
        UUID.fromString(key)
        require((method == "PATCH" && availability) || (method == "POST" && path.matches(Regex("/v1/driver/deliveries/[a-fA-F0-9-]{36}/(collect|start|complete|issue|return)"))))
        deliveryId?.let(UUID::fromString)
        require(JSONObject(body).getInt("expectedVersion") > 0)
    }
    companion object {
        fun availability(available: Boolean, endpoint: Endpoint, user: User) = Pending("PATCH", "/v1/driver/availability",
            objectOf("expectedVersion" to user.version, "available" to available).toString(), UUID.randomUUID().toString(), user.id, user.storeId, endpoint.origin)
        fun delivery(order: Delivery, command: Command, endpoint: Endpoint, user: User, recipient: String = "", paymentCollected: Boolean = false, reason: String = ""): Pending {
            require(command in order.commands) { "Atualize o pedido antes de continuar." }
            require(command != Command.COLLECT || user.available) { "Ative sua disponibilidade antes de retirar." }
            val data = objectOf("expectedVersion" to order.version)
            if (command == Command.COMPLETE) {
                require(recipient.trim().length in 1..80) { "Informe quem recebeu o pedido." }
                require(!order.needsPayment || paymentCollected) { "Confirme o recebimento do pagamento." }
                data.put("recipient", recipient.trim()).put("paymentCollected", paymentCollected)
            }
            if (command == Command.ISSUE) {
                require(reason.trim().length in 1..240) { "Informe o motivo da tentativa sem sucesso." }
                data.put("reason", reason.trim())
            }
            return Pending("POST", "/v1/driver/deliveries/${order.id}/${command.path}", data.toString(), UUID.randomUUID().toString(), user.id, user.storeId, endpoint.origin)
        }
    }
}
data class SavedState(val origin: String = "", val slug: String = "bonamassa", val account: User? = null, val session: Session? = null, val pending: Pending? = null) {
    fun signedIn(next: Session): SavedState {
        require(next.user.role == "DRIVER" && next.user.enabled)
        pending?.let { require(it.belongsTo(Endpoint(origin, slug), next.user)) { "Entre na conta original para verificar o envio pendente." } }
        return copy(account = next.user, session = next)
    }
}
object SavedCodec {
    private fun user(u: User) = objectOf("id" to u.id, "storeId" to u.storeId, "name" to u.name, "email" to u.email, "phone" to u.phone,
        "role" to u.role, "enabled" to u.enabled, "available" to u.available, "version" to u.version)
    fun encode(s: SavedState): String = objectOf("version" to 1, "origin" to s.origin, "slug" to s.slug, "account" to s.account?.let(::user),
        "session" to s.session?.let { objectOf("accessToken" to it.accessToken, "expiresAt" to it.expiresAt, "user" to user(it.user)) },
        "pending" to s.pending?.let { objectOf("method" to it.method, "path" to it.path, "body" to it.body, "key" to it.key, "ownerId" to it.ownerId, "storeId" to it.storeId, "origin" to it.origin) }).toString()
    fun decode(text: String): SavedState {
        val j = JSONObject(text)
        require(j.getInt("version") == 1) { "Versão de dados incompatível. Atualize o app." }
        val pending = j.optJSONObject("pending")?.let { Pending(it.getString("method"), it.getString("path"), it.getString("body"), it.getString("key"), it.getString("ownerId"), it.getString("storeId"), it.getString("origin")).also(Pending::validate) }
        return SavedState(j.getString("origin"), j.getString("slug"), j.optJSONObject("account")?.let(Decode::user), j.optJSONObject("session")?.let(Decode::session), pending)
    }
}
