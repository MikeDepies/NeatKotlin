package twitch.bot

import io.ktor.client.*
import twitch.bot.model.Model

class ModelStoreApi(val client: HttpClient) {
    suspend fun modelExists(model: Model): Boolean {
        return false
    }
    suspend fun storeModel(model : Model): Boolean {

        return false
    }
}