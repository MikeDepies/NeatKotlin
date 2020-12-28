package server

import TargetServerConfig
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.ws
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import server.client.ClientRegistry

private val logger = KotlinLogging.logger { }

class ServerSession(val webSocketSession: WebSocketSession) {
    suspend fun send(message: String) {
        webSocketSession.send(Frame.Text(message))
    }
}

//Need to add connection loss handling
class ServerWebSocketManager(
    private val httpClient: HttpClient,
    private val clientRegistry: ClientRegistry,
    private val json: Json,
    private val targetServerConfig: TargetServerConfig,
    var gameServer: ServerSession?
) {
    suspend fun start() {
        try {
            logger.info { "Starting connection to game server... $targetServerConfig" }
            httpClient.ws(method = HttpMethod.Get, host = targetServerConfig.host, port = targetServerConfig.port, path = targetServerConfig.path) {
                gameServer = ServerSession(webSocketSession = this@ws)
                try {
                    logger.info { "Connection Established to game server!" }
                    while (true) {
                        val frame = incoming.receive()
                        when (val messageType = frame.messageType()) {
                            is MessageType.All -> clientRegistry.sendAll(messageType.payload)
                            is MessageType.UserGroup -> messageType.users.forEach {
                                clientRegistry[it]?.send(
                                    messageType.payload
                                )
                            }
                            is MessageType.SingleUser -> {
                                logger.info { "$messageType" }
                                clientRegistry[messageType.user]?.send(messageType.payload)
                            }
                            null -> {
                                logger.warn { "No message type matched... $frame" }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Lost connection to game server!" }
                } finally {
                    gameServer = null
                    logger.info { "Server will attempt to reconnect" }
                    delay(10_000)
                    start()
                }
            }
        } catch (e: Exception) {
            logger.error { "Failed to connect to server. Retrying in 5 seconds." }
        } finally {
            delay(5_000)
            start()
        }
    }


    private fun Frame.messageType(): MessageType? {
        return when (this) {
            is Frame.Text -> {
                val message = readText()
                logger.info { "received server message: $message" }
                val jsonObject = json.decodeFromString(JsonObject.serializer(), message)
                jsonObject.messageType()
            }
            else -> null
        }
    }

    suspend fun send(message: JsonObject) {
        val stringMessage = json.encodeToString(JsonObject.serializer(), message)
        logger.info { "Sending message to server $stringMessage" }
        gameServer?.send(stringMessage) //?: addQueuedMessage(stringMessage)
    }
}