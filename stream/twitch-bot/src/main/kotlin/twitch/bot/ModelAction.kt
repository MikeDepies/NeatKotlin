package twitch.bot

import com.github.twitch4j.pubsub.domain.ChannelPointsRedemption
import twitch.bot.model.Model

sealed class ModelAction {
    data class ModelReceived(val model: Model) : ModelAction()
    data class ModelRedeemed(val reward : ChannelPointsRedemption) : ModelAction()

}