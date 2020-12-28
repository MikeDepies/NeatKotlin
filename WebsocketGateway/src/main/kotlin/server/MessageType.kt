package server

import kotlinx.serialization.json.*
import server.client.User

/**
 * ALL ->
 * PlayerGroup ->
 * Player.
 */

sealed class MessageType {
    data class All(val payload: JsonObject) : MessageType()
    data class UserGroup(val users: List<User>, val payload: JsonObject) : MessageType()
    data class SingleUser(val user: User, val payload: JsonObject) : MessageType()
}

fun JsonObject.messageType(): MessageType {
    val players = get("users")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.map { User(it) }
    val player = get("userRef")?.jsonPrimitive?.content?.let { User(it) }
    val subject = get("subject")?.jsonPrimitive?.content
    val data = get("data") ?: get("data")?.jsonArray
    val payload = buildJsonObject {
        put("subject", subject)
        put("data", data!!)
    }

    return when {
        players != null -> MessageType.UserGroup(players, payload)
        player != null -> MessageType.SingleUser(player, payload)
        else -> MessageType.All(payload)
    }
}