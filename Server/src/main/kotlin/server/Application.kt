package server
import Auth0Config
import applicationModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import org.slf4j.event.Level
import server.server.WebSocketManager
import java.time.Duration


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        allowCredentials = true
        allowSameOrigin = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
    install(CallLogging) {
        level = Level.INFO
    }
    val application = this
    install(Koin) {
        modules(applicationModule, org.koin.dsl.module {
            single { application }
            single {
                Json {
                    encodeDefaults = true
                }
            }
            single {
                with(environment.config) {
                    Auth0Config(
                        property("ktor.auth0.clientID").getString(),
                        property("ktor.auth0.clientSecret").getString(),
                        property("ktor.auth0.audience").getString(),
                        property("ktor.auth0.grantType").getString()
                    )
                }
            }
        })
    }

    get<WebSocketManager>().attachWSRoute()

}

fun previewMessage(frame: Frame.Text): String {
    val readText = frame.readText()
    val frameLength = readText.length
    return when {
        frameLength < 101 -> readText
        else -> {
            val messagePreview = readText.take(100)
            "$messagePreview...\n[[[rest of message has been trimmed]]]"
        }
    }
}