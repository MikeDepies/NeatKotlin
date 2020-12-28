import kotlinx.serialization.json.Json

/*

RawMessage -> PlayerResolution -> [Endpoint Resolution -> Message ValueType Resolution]

A player can only join one game at a time?
Do we want a typical messageHandler approach for communication?

Message:
    ConnectingMessage
    ConnectedMessage/
 */
/**
 * A MessageEndpoint is a datastructure describes:
 * what messages to capture (endpoint)
 * how to adapt the message payload (adapter)
 * how to handle the message (handler)
 * and a given scope to execute the handler on (sessionScope)
 */
sealed class MessageEndpoint
data class SimpleMessageEndpoint<R, S : SessionScope>(
    val endpoint: String,
    val handler: suspend S.(TypedPlayerMessage<R>) -> Unit,
    val adapter: MessageAdapter<R, JsonPlayerMessage>,
    val sessionScope: S) : MessageEndpoint()

inline class MessageAdapter<R, M : Message>(val transform: suspend Json.(M) -> TypedPlayerMessage<R>?)
