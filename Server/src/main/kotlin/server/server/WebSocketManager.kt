//package server.server
//
//import ClientRegistry
//import server.message.JsonUserMessage
//import server.message.Message
//import MessageEndpointRegistry
//import WebSocketClient
//import io.ktor.application.*
//import io.ktor.http.cio.websocket.*
//import io.ktor.routing.*
//import io.ktor.util.*
//import io.ktor.websocket.*
//import kotlinx.serialization.json.Json
//import mu.KotlinLogging
//import server.previewMessage
//
//private val log = KotlinLogging.logger { }
//
//class WebSocketManager(
//    private val application: Application,
//    private val clientRegistry: ClientRegistry,
//    private val messageEndpointRegistry: MessageEndpointRegistry,
//    private val json: Json
//) {
//    private fun simpleMessage(frame: Frame.Text): Message? {
//        return try {
//            json.decodeFromString(Message.serializer(), frame.readText())
//        } catch (e: Exception) {
//            log.error(e) { "failed to parse message" }
//            null
//        }
//    }
//
//    private suspend fun DefaultWebSocketServerSession.startListenLoop(messageConsumer: suspend (JsonUserMessage) -> Unit) {
//        while (true) {
//            val frame = incoming.receive()
//            if (frame is Frame.Text) {
//                val simpleMessage = simpleMessage(frame)
//                log.trace { "server.message.SimpleMessage: $simpleMessage" }
//                if (simpleMessage != null && simpleMessage is JsonUserMessage) {
//                    messageConsumer(simpleMessage)
//                } else {
//                    log.warn {
//                        """
//                        server.message.Message could not be processed into a simpleMessage.
//                        message: ${previewMessage(frame)}
//                    """.trimIndent()
//                    }
//                }
//            }
//        }
//    }
//
//    fun attachWSRoute() {
//        application.routing {
//            this.webSocket("/ws") {
//                val webSocketSession = WebSocketClient(this)
//                clientRegistry += webSocketSession
//                try {
//                    startListenLoop(messageEndpointRegistry::execute)
//                } catch (e: Exception) {
//                    log.error(e)
//                    //Exceptions are how the client typically disconnects, does not mean an error.
//                    //Though with this case, our client is just a single webservice atm, so it does probably indicate an issue
////                server.message.endpoints.server.message.endpoints.log.warn(e) { "Client disconnect." }
//                } finally {
//                    clientRegistry -= webSocketSession
//                }
//            }
//        }
//    }
//}