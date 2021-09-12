package server.message.endpoints

import FrameUpdate
import MessageWriter
import PopulationEvolver
import SessionScope
import SimpleMessageEndpoint
import get
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import mu.*
import neat.*
import neat.model.*
import org.koin.core.qualifier.*
import org.koin.core.scope.*

private val log = KotlinLogging.logger { }
var receivedAnyMessages = false
@Serializable
data class Timer(val timer : Float)
object AttackTimer {
    var timer = 30f
    var maxTime = 30f
}
fun EndpointProvider.simulationEndpoints() = sequence<SimpleMessageEndpoint<*, *>> {
    registerEndpoint<FrameUpdate, SimulationSessionScope>("simulation.frame.update") {
        val frameUpdateChannel = get<Channel<FrameUpdate>>(qualifier("input"))
//        log.info { "New frame: ${it.data}" }
        receivedAnyMessages = true
        frameUpdateChannel.send(it.data)

    }

    registerEndpoint<NoData, SimulationSessionScope>("simulation.reset.game") {
//        get<EvaluationArena>().resetEvaluation()
    }


    registerEndpoint<Timer, SimulationSessionScope>("timer") {
        AttackTimer.timer = it.data.timer
        log.info { it.data }
    }

    registerEndpoint<Timer, SimulationSessionScope>("maxTimer") {
        AttackTimer.maxTime = it.data.timer
        log.info { it.data }
    }


    registerEndpoint<NoData, SimulationSessionScope>("simulation.pause") {
//        val evaluationArena = get<EvaluationArena>()
//        evaluationArena.pause()
    }

    registerEndpoint<NoData, SimulationSessionScope>("simulation.resume") {
//        val evaluationArena = get<EvaluationArena>()
//        evaluationArena.resetEvaluation()
//        evaluationArena.resume()
    }
}

@Serializable
object NoData

class SimulationSessionScope(override val scope: Scope, override val messageWriter: MessageWriter) : SessionScope

data class Simulation(
    val initialPopulation: List<NeatMutator>,
    val populationEvolver: PopulationEvolver,
    val adjustedFitnessCalculation: AdjustedFitnessCalculation,
    val evaluationId: Int
)

