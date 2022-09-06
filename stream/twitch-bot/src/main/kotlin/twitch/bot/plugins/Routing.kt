package twitch.bot.plugins

import io.ktor.server.routing.*
import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import twitch.bot.ModelAction
import twitch.bot.model.Model
import twitch.bot.model.ModelMeta

private val logger = KotlinLogging.logger {  }
fun Application.configureRouting(modelChannel : Channel<ModelAction>) {

    routing {
        post<Model>("/model") {
            logger.info { "Received model ${it.id}" }
            modelChannel.send(ModelAction.ModelReceived(it))
        }
    }
}