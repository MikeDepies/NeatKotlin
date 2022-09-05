package twitch.bot

import com.github.twitch4j.TwitchClient
import com.github.twitch4j.helix.domain.CustomReward
import twitch.bot.model.Model

class ModelRewardFactory(/*val twitchClient: TwitchClient*/) {
    fun createModelReward(model: Model): CustomReward {
        return CustomReward.builder()
            .title("${model.character} ${model.id}")
            .prompt("Become an owner of ${model.character}. They had a score of ${model.score}.")
            .cost((model.score * 10).toInt())
            .maxPerStreamSetting(CustomReward.MaxPerStreamSetting.builder().maxPerStream(1).isEnabled(true).build())
            .build()
    }

}