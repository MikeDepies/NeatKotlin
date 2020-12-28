package server.client

import io.ktor.application.Application
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.websocket.webSocket
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.KotlinLogging
import server.ServerWebSocketManager
import server.SessionAuthenticator
import server.TokenValidationResponse
import server.UserSession
import java.lang.Exception

private val log = KotlinLogging.logger { }

class ClientWebSocketManager(
    private val application: Application,
    private val sessionAuthenticator: SessionAuthenticator,
    private val json: Json,
    private val serverWebSocketManager: ServerWebSocketManager,
    private val clientRegistry: ClientRegistry
) {
    fun start(endpoint: String) = application.routing {
        webSocket(endpoint) {
            log.info { "Received token from client" }
            val webSocketServerSession = this
            val token = sessionAuthenticator.authenticate(webSocketServerSession)
            if (token is TokenValidationResponse.Success) {
                val user = User(token.userId)
                val userSession = UserSession(webSocketServerSession, json)
                try {
                    clientRegistry.register(user, userSession)
                    val message =
                        ClientMessage("user.token", buildJsonObject { put("token", token.token) }).toServerMessage(user)
                    log.info { "Sending token message to game server: $message" }
                    sendToServer(message)
                    while (true) {
                        val clientMessage = incoming.receive().toClientMessage() ?: continue
                        log.info { "Received message from client $clientMessage" }
                        sendToServer(clientMessage.toServerMessage(user))
                    }
                } catch (e: Exception) {
                    log.warn(e) { "Connection terminated..." }
                } finally {
                    clientRegistry.unregister(user, userSession)
                }
            } else {
                close(
                    CloseReason(
                        CloseReason.Codes.PROTOCOL_ERROR,
                        "server.client.User is not recognized."
                    )
                )
            }
        }

    }

    private suspend fun sendToServer(jsonObject: JsonObject) {
        serverWebSocketManager.send(jsonObject)
    }

    private fun Frame.toClientMessage(): ClientMessage? {
        return when (this) {
            is Frame.Text -> {
                val jsonObject = json.parseToJsonElement(readText()).jsonObject
                clientMessage(jsonObject)
            }
            else -> null
        }
    }
}

private fun ClientMessage.toServerMessage(user: User): JsonObject = buildJsonObject {
    put("type", "userMessage")
    put("topic", subject)
    put("data", data)
    put("userRef", user.userRef)
}

fun clientMessage(jsonObject: JsonObject): ClientMessage {
    log.info { "Attempting to receive json message from client $jsonObject" }
    return ClientMessage(
        jsonObject["subject"]!!.jsonPrimitive.content,
        jsonObject.getOrDefault("data", buildJsonObject {}).jsonObject
    )
}

@Serializable
data class ClientMessage(val subject: String, val data: JsonObject)