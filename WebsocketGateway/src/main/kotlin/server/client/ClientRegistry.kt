package server.client

import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import server.UserManager
import server.UserSession

private val log = KotlinLogging.logger {  }
class ClientRegistry(private val clientMap: MutableMap<User, UserManager>) {
    operator fun get(player: User): UserManager? {
        log.info { "Getting playerManager for $player [${player in clientMap}]" }
        return clientMap[player]
    }
    suspend fun sendAll(payload: JsonObject) {
        clientMap.values.forEach { it.send(payload) }
    }

    fun register(player: User, playerSession: UserSession) {
        if (player !in clientMap) clientMap[player] = UserManager(mutableListOf())
        val playerManager = clientMap[player]!!
        log.info { "Registering player session for player $player" }
        playerManager.addPlayerSession(playerSession)
    }

    fun unregister(player: User, playerSession: UserSession) {
        val playerManager = clientMap[player]
        playerManager?.removePlayerSession(playerSession)
    }

}

data class User(val userRef : String)