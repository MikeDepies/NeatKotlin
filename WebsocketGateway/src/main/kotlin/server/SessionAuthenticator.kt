package server

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import util.safeCast

private val logger = KotlinLogging.logger { }

class SessionAuthenticator(private val json: Json, private val httpClient: HttpClient, private val userInfoUrl: String) {
    private suspend fun HttpClient.getUserInfo(token: String?): JsonObject? = try {
        logger.info { "Requesting user info... $token"}
        get<JsonObject> {
            url(userInfoUrl)
            header("Authorization", "Bearer $token")
        }
    } catch (e: Exception) {
        logger.error(e) { "Failed to handle user token" }
        null
    }

    suspend fun authenticate(
        defaultWebSocketServerSession: DefaultWebSocketServerSession
    ): TokenValidationResponse {
        return defaultWebSocketServerSession.incoming.receive().token(json).let { token ->
            val response = httpClient.getUserInfo(token)
            when {
                response != null && token != null -> {
                    logger.trace { "data" + json.encodeToString(JsonObject.serializer(), response) }
                    val userId = response["sub"]!!.jsonPrimitive.content
                    TokenValidationResponse.Success(token, userId, response)
                }
                token != null -> TokenValidationResponse.FailureAuth(token)
                else -> TokenValidationResponse.FailureNoToken
            }.also { logger.warn { it } }
        }
    }

    private fun Frame.token(json: Json): String? {
        return safeCast<Frame.Text>()?.token(json)
    }

    private fun Frame.Text.token(json: Json): String {
        val payload = json.parseToJsonElement(readText()).jsonObject
        return payload["token"]!!.jsonPrimitive.content
    }
}

