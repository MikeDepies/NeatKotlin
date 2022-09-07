package server

import kotlinx.serialization.Serializable

@Serializable
data class UrlConfig(val twitchBot : String)
@Serializable
data class Config(val url : UrlConfig)