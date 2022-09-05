package twitch.bot

import Config
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent
import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import twitch.bot.model.CustomRewardManager
import twitch.bot.model.Model
import twitch.bot.model.ModelArchive
import twitch.bot.model.TwitchRepo
import twitch.bot.plugins.*
import java.io.File

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

//fun main() {
//    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
//        configureSerialization()
//        configureSockets()
//        configureMonitoring()
//        configureHTTP()
//        configureRouting()
//    }.start(wait = true)
//}

fun Application.module() {
    configureSerialization()
    configureSockets()
    configureMonitoring()
    configureHTTP()

    val config = Json { }.decodeFromStream<Config>(File("config.json").inputStream())
    val credential = OAuth2Credential("twitch", config.twitch.accessToken)
    val twitchClient = TwitchClientBuilder.builder()
        .withEnableHelix(true)
        .withEnablePubSub(true)
        .withEnableChat(true)
        .withChatAccount(credential)
        .build()
    val modelChannel = Channel<ModelAction>(Channel.UNLIMITED)
    val modelArchive = ModelArchive(listOf(), 10)
    val broadcasterId = config.twitch.broadcasterId
    val twitchRepo = TwitchRepo(twitchClient, credential, broadcasterId)
    val customRewardManager = CustomRewardManager(twitchRepo, modelArchive, ModelRewardFactory(), mapOf())
    launch {
        for(modelAction in modelChannel) {
            when (modelAction) {
                is ModelAction.ModelReceived -> customRewardManager.receiveModel(modelAction.model)
                is ModelAction.ModelRedeemed -> customRewardManager.redeemReward(modelAction.rewardId)
            }

        }
    }
    configureRouting(modelChannel)
    twitchClient.pubSub.listenForChannelPointsRedemptionEvents(credential, broadcasterId)
    twitchClient.eventManager.onEvent(RewardRedeemedEvent::class.java) {
        val modelRedeemed = ModelAction.ModelRedeemed(it.redemption.reward.id)
        runBlocking {
            modelChannel.send(modelRedeemed)
        }
    }
}

sealed class ModelAction {
    data class ModelReceived(val model: Model) : ModelAction()
    data class ModelRedeemed(val rewardId : String) : ModelAction()

}