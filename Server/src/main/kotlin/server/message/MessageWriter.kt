//import io.ktor.application.*
//import kotlinx.coroutines.launch
//import kotlinx.serialization.KSerializer
//import kotlinx.serialization.json.Json
//import mu.KotlinLogging
//import server.message.BroadcastMessage
//import server.message.TypedUserGroupMessage
//import server.message.TypedUserMessage
//
//val logger = KotlinLogging.logger {  }
///**
// * This service will be used to send messages back to the client(s).
// */
//interface MessageWriter {
//    fun <T> sendPlayerMessage(
//        userMessage: TypedUserMessage<T>,
//        serializer: KSerializer<T>
//    )
//
//    fun <T> sendAllMessage(
//        message: BroadcastMessage<T>,
//        serializer: KSerializer<T>
//    )
//
//    fun <T> sendGroupMessage(
//        message: TypedUserGroupMessage<T>,
//        serializer: KSerializer<T>
//    )
//}
//
//class MessageWriterImpl(
//    private val clientRegistry: ClientRegistry,
//    private val json: Json,
//    private val application: Application
//) : MessageWriter {
//
//    override fun <T> sendPlayerMessage(
//        userMessage: TypedUserMessage<T>,
//        serializer: KSerializer<T>
//    ) {
//        application.launch {
//            clientRegistry.clients.forEach {
////                logger.info { "Sending player message $userMessage" }
//                it.send(json.encodeToString(TypedUserMessage.serializer(serializer), userMessage))
//            }
//        }
//    }
//
//    override fun <T> sendAllMessage(
//        message: BroadcastMessage<T>,
//        serializer: KSerializer<T>
//    ) {
//        application.launch {
//            clientRegistry.clients.forEach {
////                logger.info { "Sending all message $message" }
//                it.send(json.encodeToString(BroadcastMessage.serializer(serializer), message))
//            }
//        }
//    }
//
//    override fun <T> sendGroupMessage(
//        message: TypedUserGroupMessage<T>,
//        serializer: KSerializer<T>
//    ) {
//        application.launch {
//            clientRegistry.clients.forEach {
////                logger.info { "Seding group message $message" }
//                it.send(json.encodeToString(TypedUserGroupMessage.serializer(serializer), message))
//            }
//        }
//    }
//}
//
//class LocalMessageWriter : MessageWriter {
//    override fun <T> sendPlayerMessage(userMessage: TypedUserMessage<T>, serializer: KSerializer<T>) {
//        println("Sending player message $userMessage")
//    }
//
//    override fun <T> sendAllMessage(message: BroadcastMessage<T>, serializer: KSerializer<T>) {
//        println("Sending all message $message")
//    }
//
//    override fun <T> sendGroupMessage(message: TypedUserGroupMessage<T>, serializer: KSerializer<T>) {
//        println("Sending group message $message")
//    }
//
//}
