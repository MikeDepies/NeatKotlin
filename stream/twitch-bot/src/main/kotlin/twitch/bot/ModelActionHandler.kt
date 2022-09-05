package twitch.bot

import twitch.bot.model.CustomRewardManager

class ModelActionHandler(val modelStoreApi: ModelStoreApi, val customRewardManager: CustomRewardManager) {
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