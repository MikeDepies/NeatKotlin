package server.message.endpoints

import MessageWriter
import SessionScope
import SimpleMessageEndpoint
import get
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import mu.*
import org.koin.core.qualifier.*
import org.koin.core.scope.*

private val log = KotlinLogging.logger { }
var receivedAnyMessages = false
@Serializable
data class Timer(val timer : Float)
object AttackTimer {
    var timer = 30f
}
fun EndpointProvider.simulationEndpoints() = sequence<SimpleMessageEndpoint<*, *>> {
    registerEndpoint<MarioData, SimulationSessionScope>("simulation.frame.update") {
        val frameUpdateChannel = get<Channel<MarioData>>(qualifier("input"))
//        log.info { "New frame: ${it.data}" }
        receivedAnyMessages = true
        frameUpdateChannel.send(it.data)

    }

    registerEndpoint<Timer, SimulationSessionScope>("timer") {
        AttackTimer.timer = it.data.timer
        log.info { it.data }
    }
}

@Serializable
object NoData

class SimulationSessionScope(override val scope: Scope, override val messageWriter: MessageWriter) : SessionScope

