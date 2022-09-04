package twitch.bot

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import twitch.bot.plugins.*

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
    configureRouting()
}