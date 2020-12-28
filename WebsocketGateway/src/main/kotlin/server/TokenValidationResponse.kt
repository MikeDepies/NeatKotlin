package server

import kotlinx.serialization.json.JsonObject

sealed class TokenValidationResponse {
    data class Success(val token: String, val userId: String, val data: JsonObject) : TokenValidationResponse()
    data class FailureAuth(val token: String) : TokenValidationResponse()
    object FailureNoToken : TokenValidationResponse()
}