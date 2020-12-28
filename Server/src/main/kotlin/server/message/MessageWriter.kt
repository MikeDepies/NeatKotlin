import io.ktor.application.*
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging

val logger = KotlinLogging.logger {  }
/**
 * This service will be used to send messages back to the client(s).
 */
interface MessageWriter {
    fun <T> sendPlayerMessage(
        playerMessage: TypedPlayerMessage<T>,
        serializer: KSerializer<T>
    )

    fun <T> sendAllMessage(
        message: BroadcastMessage<T>,
        serializer: KSerializer<T>
    )

    fun <T> sendGroupMessage(
        message: TypedPlayerGroupMessage<T>,
        serializer: KSerializer<T>
    )
}

class MessageWriterImpl(
    private val clientRegistry: ClientRegistry,
    private val json: Json,
    private val application: Application
) : MessageWriter {

    override fun <T> sendPlayerMessage(
        playerMessage: TypedPlayerMessage<T>,
        serializer: KSerializer<T>
    ) {
        application.launch {
            clientRegistry.clients.forEach {
                logger.info { "Sending player message $playerMessage" }
                it.send(json.encodeToString(TypedPlayerMessage.serializer(serializer), playerMessage))
            }
        }
    }

    override fun <T> sendAllMessage(
        message: BroadcastMessage<T>,
        serializer: KSerializer<T>
    ) {
        application.launch {
            clientRegistry.clients.forEach {
                logger.info { "Sending all message $message" }
                it.send(json.encodeToString(BroadcastMessage.serializer(serializer), message))
            }
        }
    }

    override fun <T> sendGroupMessage(
        message: TypedPlayerGroupMessage<T>,
        serializer: KSerializer<T>
    ) {
        application.launch {
            clientRegistry.clients.forEach {
                logger.info { "Seding group message $message" }
                it.send(json.encodeToString(TypedPlayerGroupMessage.serializer(serializer), message))
            }
        }
    }
}

class LocalMessageWriter : MessageWriter {
    override fun <T> sendPlayerMessage(playerMessage: TypedPlayerMessage<T>, serializer: KSerializer<T>) {
        println("Sending player message $playerMessage")
    }

    override fun <T> sendAllMessage(message: BroadcastMessage<T>, serializer: KSerializer<T>) {
        println("Sending all message $message")
    }

    override fun <T> sendGroupMessage(message: TypedPlayerGroupMessage<T>, serializer: KSerializer<T>) {
        println("Sending group message $message")
    }

}
