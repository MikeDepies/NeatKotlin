package twitch.bot

import Config
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mu.KotlinLogging
import twitch.bot.model.CustomRewardManager
import twitch.bot.model.ModelArchive
import twitch.bot.model.TwitchRepo
import twitch.bot.plugins.*
import java.io.File

private val logger = KotlinLogging.logger { }

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureSockets()
    configureMonitoring()
    configureHTTP()
    val client = HttpClient(CIO)
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
    val modelStoreApi = ModelStoreApi(client)
    val modelActionHandler = ModelActionHandler(modelStoreApi, customRewardManager)
    launch {
        for (modelAction in modelChannel) {
            modelActionHandler.handle(modelAction)
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