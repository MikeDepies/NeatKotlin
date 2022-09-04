package twitch.bot

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import twitch.bot.plugins.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSockets()
        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting()
    }.start(wait = true)
}
