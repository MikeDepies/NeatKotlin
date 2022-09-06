package twitch.bot

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging
import twitch.bot.model.Model

data class ModelStatus(val exists : Boolean)
private val logger = KotlinLogging.logger {  }
class ModelStoreApi(private val client: HttpClient) {
    suspend fun modelExists(model: Model): Boolean {
        val response = client.get("/model/check/${model.id}")
        if (response.status == HttpStatusCode.OK) {
            val body = response.body<ModelStatus>()
            return body.exists
        } else {
            logger.error { "Bad response: Status ${response.status}" }
        }
        return false
    }
    suspend fun storeModel(model : Model): Boolean {
        val response = client.post("/model/store") {
            setBody(model)
        }
        if (response.status == HttpStatusCode.OK) {
            return true
        } else {
            logger.error { "Bad response: Status ${response.status}" }
        }
        return false
    }
}