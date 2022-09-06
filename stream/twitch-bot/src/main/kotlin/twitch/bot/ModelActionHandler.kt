package twitch.bot

import mu.KotlinLogging
import twitch.bot.model.CustomRewardManager
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
                val model = customRewardManager.redeemReward(modelAction.rewardId)
                if (model != null) {
                    val storeModelSuccess = modelStoreApi.storeModel(model)
                    if (storeModelSuccess) {
                        customRewardManager.redeemModelSuccess(model)
                    }
                }
            }
        }
    }

}