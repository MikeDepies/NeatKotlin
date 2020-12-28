import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

interface MessageEndpointRegistry {
    suspend fun execute(jsonPlayerMessage: JsonPlayerMessage)
}

/***
 * The MessageEndpointRegistryImpl has a set of endpoints with information about:
 * what messages it should match, how to convert the message into the proper payload and what handler to call.
 * The endpoints are injected into this class by contributing MessageEndpointRegistrationModules.
 *
 */
class MessageEndpointRegistryImpl(
        private val endpoints: List<MessageEndpoint>,
        private val json: Json) : MessageEndpointRegistry {
    override suspend fun execute(jsonPlayerMessage: JsonPlayerMessage) {
        val messageEndpoint = endpoints.first { it.match(jsonPlayerMessage) }
        //Adapt as any since we are trusting the endpoints are configured properly.
        //This will always be true if the endpoints are constructed with the register DSL
        val playerMessage = messageEndpoint.adapt<Any>(jsonPlayerMessage)
        messageEndpoint.handle(playerMessage)
    }

    /*
    Convenience methods for dealing with different types of endpoints. For now there is just the single type.
     */
    private suspend fun <T> MessageEndpoint.adapt(message: JsonPlayerMessage): TypedPlayerMessage<T> {
        return when (this) {
            is SimpleMessageEndpoint<*, *> -> adapter.transform(json, message) as TypedPlayerMessage<T>
        }
    }

    private fun MessageEndpoint.match(message: Message): Boolean {
        return when (this) {
            is SimpleMessageEndpoint<*, *> -> message.topic == endpoint
        }
    }

    private suspend fun <T> MessageEndpoint.handle(playerMessage: TypedPlayerMessage<T>) {
        when (this) {
            is SimpleMessageEndpoint<*, *> ->
                (this as SimpleMessageEndpoint<T, SessionScope>).handler(this.sessionScope, playerMessage)
        }
    }
}