package server.message.endpoints

import kotlinx.serialization.Serializable

@Serializable
data class MutationDefinition(val name: String, val percentChance: Float)