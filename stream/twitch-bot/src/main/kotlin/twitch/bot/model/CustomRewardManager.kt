package twitch.bot.model

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClient
import com.github.twitch4j.helix.domain.CustomReward
import twitch.bot.ModelRewardFactory

data class TwitchRepo(val twitchClient: TwitchClient, val credential: OAuth2Credential, val broadcasterId: String)

class CustomRewardManager(
    val twitchRepo: TwitchRepo,
    val modelArchive: ModelArchive,
    val modelRewardFactory: ModelRewardFactory,
    modelRewardMap: Map<Model, CustomReward>
) {

    val modelRewardMap = modelRewardMap.toMutableMap()
    fun receiveModel(model: Model) {
        //TODO prevent duplicate recieving of models. Models that have been stored or are in the shop.
        val (twitchClient, credential, broadcasterId) = twitchRepo
        val modelReward = modelRewardFactory.createModelReward(model)
        val createCustomReward =
            twitchClient.helix.createCustomReward(credential.accessToken, broadcasterId, modelReward).execute()
        val customReward = createCustomReward.rewards.first()
        modelRewardMap[model] = customReward
        val removedModel = modelArchive.commitModel(model)
        if (removedModel != null && modelRewardMap.containsKey(removedModel)) {
            val removedModelReward = modelRewardMap.getValue(removedModel)
            twitchClient.helix.deleteCustomReward(credential.accessToken, broadcasterId, removedModelReward.id).execute()
            modelRewardMap.remove(removedModel)
        }
    }

    fun redeemReward(id: String): Model? {
        val rewardModelMap = modelRewardMap.map { it.value.id to it.key }.toMap()
        return if (rewardModelMap.containsKey(id)) {
            val model = rewardModelMap.getValue(id)
            modelArchive.removeModel(model)
            modelRewardMap.remove(model)
            model
        } else null
    }


}