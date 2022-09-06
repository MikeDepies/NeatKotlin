//import kotlinx.serialization.json.Json
//import server.message.JsonUserMessage
//import server.message.Message
//import server.message.TypedUserMessage
//
///*
//
//RawMessage -> PlayerResolution -> [Endpoint Resolution -> server.message.Message ValueType Resolution]
//
//A player can only join one game at a time?
//Do we want a typical messageHandler approach for communication?
//
//server.message.Message:
//    ConnectingMessage
//    ConnectedMessage/
// */
///**
// * A MessageEndpoint is a datastructure describes:
// * what messages to capture (endpoint)
// * how to adapt the message payload (adapter)
// * how to handle the message (handler)
// * and a given scope to execute the handler on (sessionScope)
// */
//sealed class MessageEndpoint
//data class SimpleMessageEndpoint<R, S : SessionScope>(
//    val endpoint: String,
//    val handler: suspend S.(TypedUserMessage<R>) -> Unit,
//    val adapter: MessageAdapter<R, JsonUserMessage>,
//    val sessionScope: S) : MessageEndpoint()
//
//inline class MessageAdapter<R, M : Message>(val transform: suspend Json.(M) -> TypedUserMessage<R>?)
