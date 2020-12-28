import server.message.endpoints.EndpointProvider

fun EndpointProvider.scheduleEndpoints() = sequence<SimpleMessageEndpoint<*, *>> {

}