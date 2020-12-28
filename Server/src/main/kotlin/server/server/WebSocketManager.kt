package server.server

import ClientRegistry
import JsonPlayerMessage
import Message
import MessageEndpointRegistry
import WebSocketClient
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import server.previewMessage

private val log = KotlinLogging.logger { }

class WebSocketManager(
    private val application: Application,
    private val clientRegistry: ClientRegistry,
    private val messageEndpointRegistry: MessageEndpointRegistry,
    private val json: Json
) {
    private fun simpleMessage(frame: Frame.Text): Message? {
        return try {
            json.decodeFromString(Message.serializer(), frame.readText())
        } catch (e: Exception) {
            log.error(e) { "failed to parse message" }
            null
        }
    }

    private suspend fun DefaultWebSocketServerSession.startListenLoop(messageConsumer: suspend (JsonPlayerMessage) -> Unit) {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) {
                val simpleMessage = simpleMessage(frame)
                log.info { "SimpleMessage: $simpleMessage" }
                if (simpleMessage != null && simpleMessage is JsonPlayerMessage) {
                    messageConsumer(simpleMessage)
                } else {
                    log.warn {
                        """
                        Message could not be processed into a simpleMessage.
                        message: ${previewMessage(frame)}
                    """.trimIndent()
                    }
                }
            }
        }
    }

    fun attachWSRoute() {
        application.routing {
            this.webSocket("/ws") {
                val webSocketSession = WebSocketClient(this)
                clientRegistry += webSocketSession
                try {
                    startListenLoop(messageEndpointRegistry::execute)
                } catch (e: Exception) {
                    //Exceptions are how the client typically disconnects, does not mean an error.
                    //Though with this case, our client is just a single webservice atm, so it does probably indicate an issue
//                log.warn(e) { "Client disconnect." }
                } finally {
                    clientRegistry -= webSocketSession
                }
            }
        }
    }
}