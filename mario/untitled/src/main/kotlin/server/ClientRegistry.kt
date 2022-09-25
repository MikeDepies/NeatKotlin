import io.ktor.http.cio.websocket.send
import io.ktor.websocket.DefaultWebSocketServerSession

/**
 * A registry that tracks what clients we can talk to.
 */
class ClientRegistry(private var _clients: List<MessageClient>) {
    val clients get() = _clients

    fun addClient(defaultWebSocketServerSession: MessageClient) {
        _clients = _clients + defaultWebSocketServerSession
    }

    fun removeClient(client: MessageClient) {
        _clients = _clients - client
    }

    operator fun plusAssign(client: MessageClient) = addClient(client)
    operator fun minusAssign(client: MessageClient) = removeClient(client)
}

interface MessageClient {
    suspend fun send(content: String)
}

class WebSocketClient(private val delegate: DefaultWebSocketServerSession) : MessageClient,
    DefaultWebSocketServerSession by delegate {
    override suspend fun send(content : String) {
        delegate.send(content)
    }

}