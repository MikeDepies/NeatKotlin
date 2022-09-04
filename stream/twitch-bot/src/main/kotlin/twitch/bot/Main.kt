import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.github.twitch4j.chat.events.channel.IRCMessageEvent
import com.github.twitch4j.eventsub.domain.Reward
import com.github.twitch4j.helix.domain.CustomReward
import com.github.twitch4j.helix.domain.CustomReward.CustomRewardBuilder
import com.github.twitch4j.pubsub.events.RedemptionStatusUpdateEvent
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent
import com.github.twitch4j.pubsub.events.UpdateRedemptionFinishedEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.util.UUID

@Serializable
data class Config(val twitch : TwitchBotConfig)

@Serializable
data class TwitchBotConfig(val accessToken : String, val broadcasterId : String)

fun main(args: Array<String>) {
    // chat credential
    val config = Json { }.decodeFromStream<Config>(File("config.json").inputStream())
    println(config.twitch)
    val credential = OAuth2Credential("twitch", config.twitch.accessToken)
    val twitchClient = TwitchClientBuilder.builder()
        .withEnableHelix(true)
        .withEnablePubSub(true)
        .withEnableChat(true)
        .withChatAccount(credential)
        .build()
    twitchClient.chat.joinChannel("meleeNeat")
    val broadcasterId = config.twitch.broadcasterId
    twitchClient.pubSub.listenForChannelPointsRedemptionEvents(credential, broadcasterId)
    twitchClient.chat.eventManager.onEvent(ChannelMessageEvent::class.java) { event ->
        when {
            event.message.startsWith("#link") -> {
                twitchClient.chat.sendMessage("meleeNeat", "Active Link Model: <ID>")
            }
            event.message.startsWith("#pikachu") -> {
                twitchClient.chat.sendMessage("meleeNeat", "Active Pikachu Model: <ID>")
            }
        }


    }
    twitchClient.eventManager.onEvent(RewardRedeemedEvent::class.java) {
        it.redemption.status == "UNFULFILLED"

    }
    val customReward = CustomReward.builder()
        .broadcasterId(broadcasterId)
        .cost(200)
        .title("${UUID.randomUUID()}")
        .prompt("2 Kills, 200 damage")
        .maxPerStreamSetting(CustomReward.MaxPerStreamSetting().toBuilder().maxPerStream(1).isEnabled(true).build())
        .build()
//    val createCustomReward = twitchClient.helix.createCustomReward(credential.accessToken, broadcasterId, customReward).execute()

//    twitchClient.helix.createClip(credential.accessToken, broadcasterId, false).execute().data


}