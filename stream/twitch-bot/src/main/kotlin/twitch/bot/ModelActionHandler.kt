package twitch.bot

import mu.KotlinLogging
import twitch.bot.model.CustomRewardManager
import twitch.bot.model.ModelMeta
import twitch.bot.model.ModelOwner

private val logger = KotlinLogging.logger {  }
class ModelActionHandler(private val modelStoreApi: ModelStoreApi, private val customRewardManager: CustomRewardManager) {
    suspend fun handle(
        modelAction: ModelAction
    ) {
        when (modelAction) {
            is ModelAction.ModelReceived -> {
                if (modelStoreApi.modelExists(modelAction.model)) {
                    customRewardManager.receiveModel(modelAction.model)
                } else {
                    logger.info { "Ignoring model ${modelAction.model.id}. Already exists in store." }
                }
            }

            is ModelAction.ModelRedeemed -> {
                val model = customRewardManager.redeemReward(modelAction.reward.reward.id)
                if (model != null) {
                    val modelMeta = ModelMeta(
                        ModelOwner(modelAction.reward.user.id, modelAction.reward.user.displayName),
                        model,
                        modelAction.reward.userInput ?: model.id
                    )
                    val storeModelSuccess = modelStoreApi.storeModel(modelMeta)
                    if (storeModelSuccess) {
                        customRewardManager.redeemModelSuccess(model)
                    }
                }
            }
        }
    }

}