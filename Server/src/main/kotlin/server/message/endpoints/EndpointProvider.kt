package server.message.endpoints

import server.message.JsonUserMessage
import MessageAdapter
import SessionScope
import SimpleMessageEndpoint
import UserRef
import server.message.TypedUserMessage
import UserTokenResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.scope.Scope


class EndpointProvider(
    val json: Json,
    val userTokenResolver: UserTokenResolver,
    val scope: Scope
) {
    @JvmName("registerEndpointSimple")
    suspend inline fun <reified R : Any> SequenceScope<SimpleMessageEndpoint<R, SessionScope>>.registerEndpoint(
        endpoint: String,
        noinline handler: suspend SessionScope.(TypedUserMessage<R>) -> Unit
    ) {
        yield(this@EndpointProvider.registerEndpoint(endpoint, handler))
    }

    suspend inline fun <reified R : Any, reified S : SessionScope> SequenceScope<SimpleMessageEndpoint<R, S>>.registerEndpoint(
        endpoint: String,
        noinline handler: suspend S.(TypedUserMessage<R>) -> Unit
    ) {
        yield(this@EndpointProvider.registerEndpoint(endpoint, handler))
    }
}

inline fun <reified R : Any, reified S : SessionScope> EndpointProvider.registerEndpoint(
    endpoint: String,
    noinline handler: suspend S.(TypedUserMessage<R>) -> Unit
): SimpleMessageEndpoint<R, S> {
    val adapter: suspend Json.(JsonUserMessage) -> TypedUserMessage<R>? = { simpleMessage ->
        try {
            TypedUserMessage(
                UserRef(simpleMessage.userRef),
//            userTokenResolver.resolve(simpleMessage.userRef),
                simpleMessage.topic,
                Json.decodeFromJsonElement(simpleMessage.data)
            )
        }
        catch (e : Exception) {
            e.printStackTrace()
            error("failure in endpoint adaption")
        }
    }
    return SimpleMessageEndpoint(endpoint, handler, MessageAdapter(adapter), scope.get())
}
