package server


import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import server.plugins.*
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
//        configureRouting()
        val client = KMongo.createClient().coroutine
        val database = client.getDatabase("stream-models")
        val databaseHelper = DatabaseHelper(database)
        val modelRoutes = ModelRoutes(databaseHelper)
        modelRoutes.configureRouting(this)
}