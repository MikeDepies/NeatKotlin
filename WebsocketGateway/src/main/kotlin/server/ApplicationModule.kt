package server

import Auth0Config
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import server.client.ClientRegistry
import server.client.ClientWebSocketManager

val applicationModule = module {
    single { Json { encodeDefaults = true } }
    single { ClientRegistry(mutableMapOf()) }
    single { ServerWebSocketManager(get(), get(), get(), get(), null) }
    single { ClientWebSocketManager(get(), get(), get(), get(), get()) }
    single {
//        println(getProperty("authUrl"))
        SessionAuthenticator(get(), get(), get<Auth0Config>().userInfoUrl)
    }
}