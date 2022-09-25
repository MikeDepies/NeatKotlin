package twitch.bot.model

import kotlinx.serialization.Serializable

@Serializable
data class Model(val id : String, val neatModel: NeatModel)