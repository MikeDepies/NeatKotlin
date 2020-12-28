package server.message.endpoints

import JsonPlayerMessage
import MessageAdapter
import SessionScope
import SimpleMessageEndpoint
import TypedPlayerMessage
import UserTokenResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.scope.Scope


class EndpointProvider(
    val json: Json,
    val userTokenResolver: UserTokenResolver,
    val scope: Scope
) {
    suspend inline fun <reified R : Any, reified S : SessionScope> SequenceScope<SimpleMessageEndpoint<R, S>>.registerEndpoint(
        endpoint: String,
        noinline handler: suspend S.(TypedPlayerMessage<R>) -> Unit
    ) {
        yield(this@EndpointProvider.registerEndpoint(endpoint, handler))
    }
}

inline fun <reified R : Any, reified S : SessionScope> EndpointProvider.registerEndpoint(
    endpoint: String,
    noinline handler: suspend S.(TypedPlayerMessage<R>) -> Unit
): SimpleMessageEndpoint<R, S> {
    val adapter: suspend Json.(JsonPlayerMessage) -> TypedPlayerMessage<R>? = { simpleMessage ->

        TypedPlayerMessage(
            userTokenResolver.resolve(simpleMessage.playerRef),
            simpleMessage.topic,
            Json.decodeFromJsonElement(simpleMessage.data)
        )
    }
    return SimpleMessageEndpoint(endpoint, handler, MessageAdapter(adapter), scope.get())
}
