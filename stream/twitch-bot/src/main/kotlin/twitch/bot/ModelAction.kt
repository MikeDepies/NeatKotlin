package twitch.bot

import twitch.bot.model.Model

sealed class ModelAction {
    data class ModelReceived(val model: Model) : ModelAction()
    data class ModelRedeemed(val rewardId : String) : ModelAction()

}