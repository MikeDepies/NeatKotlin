package twitch.bot

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import twitch.bot.model.Model
import twitch.bot.model.ModelMeta


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
    suspend fun storeModel(model : ModelMeta): Boolean {
        val response = client.post("/model/store") {
            contentType(ContentType.Application.Json)
            setBody(model)
        }
        if (response.status == HttpStatusCode.OK) {
            return true
        } else {
            logger.error { "Bad response: Status ${response.status}" }
        }
        return false
    }
    suspend fun getModelIdsForOwner(ownerId : String): List<ModelDescription> {
        val response = client.get("/modeldescriptions/$ownerId")
        return if (response.status == HttpStatusCode.OK) {
            val body = response.body<List<ModelDescription>>()
            body
        } else {
            logger.error { "Bad response: Status ${response.status}" }
            listOf()
        }
    }
}
@Serializable
data class ModelStatus(val exists : Boolean)
@Serializable
data class ModelDescription(val modelId: String, val modelName : String, val modelCharacter : Character)