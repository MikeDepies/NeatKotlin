import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.server.cio.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.inject
import server.client.ClientWebSocketManager
import server.ServerWebSocketManager
import server.applicationModule
import java.time.Duration


fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(DataConversion)
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }


    val client = HttpClient(CIO) {
        install(HttpTimeout) {}
        install(io.ktor.client.features.websocket.WebSockets)
        install(JsonFeature) { serializer = KotlinxSerializer() }
        install(Logging) { level = LogLevel.NONE }
    }
    val application = this
    val setupModule = org.koin.dsl.module {
        single {
            val userInfoUrl = environment.config.property("ktor.auth0.userInfoUrl").getString()
            Auth0Config(userInfoUrl)
        }
        single {
            with (environment.config) {
                val host = property("ktor.server.host").getString()
                val path = property("ktor.server.webSocket.path").getString()
                val port = property("ktor.server.port").getString().toInt()
                TargetServerConfig(host, port, path)
            }
        }
        single { application }
        single { client }
    }
    install(Koin) {

        modules(setupModule, applicationModule)
    }

    val clientWebSocketManager: ClientWebSocketManager by inject()
    val serverWebSocketManager: ServerWebSocketManager by inject()
    clientWebSocketManager.start("/ws")
    launch { serverWebSocketManager.start() }
}

data class Auth0Config(val userInfoUrl: String)
data class TargetServerConfig(val host: String, val port: Int, val path: String)