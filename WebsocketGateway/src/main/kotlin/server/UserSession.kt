package server
import io.ktor.http.cio.websocket.Frame
import io.ktor.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class UserSession(private val socketServerSession: DefaultWebSocketServerSession, private val json: Json) {
    suspend fun send(payload: JsonObject) {
        socketServerSession.send(payload.toFrame())
    }

    private fun JsonObject.toFrame(): Frame {
        return Frame.Text(
            json.encodeToString(
                JsonObject.serializer(),
                this
            )
        )
    }
}