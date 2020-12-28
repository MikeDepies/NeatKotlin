package server

import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging

private val log = KotlinLogging.logger {  }
class UserManager(private val userSessions: MutableList<UserSession>) {
    suspend fun send(payload: JsonObject) {
        log.info { "sending payload $payload to ${userSessions.size} user sessions" }
        userSessions.forEach {
            it.send(payload)
        }
    }

    fun addPlayerSession(playerSession: UserSession) {
        userSessions.add(playerSession)
    }

    fun removePlayerSession(playerSession: UserSession) {
        userSessions.remove(playerSession)
    }
}