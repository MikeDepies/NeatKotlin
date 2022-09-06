package server.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import server.message.endpoints.NeatModel


data class TwitchModel(val id : String, val neatModel : NeatModel, val character : Character, val score: Float)
class TwitchBotService(private val client: HttpClient, private val url: String) {
    suspend fun sendModel(model : TwitchModel) {
//        client.post("$url/model") {
//            contentType(ContentType.Application.Json)
//            setBody(model)
//        }
    }
}