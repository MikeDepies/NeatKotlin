package server.message.endpoints

import FrameOutput
import FrameUpdate
import MessageWriter
import PopulationEvolver
import SessionScope
import SimpleMessageEndpoint
import get
import kotlinx.serialization.*
import mu.*
import neat.*
import neat.model.*
import org.koin.core.scope.*
import server.message.*

private val log = KotlinLogging.logger { }
var receivedAnyMessages = false
fun EndpointProvider.simulationEndpoints() = sequence<SimpleMessageEndpoint<*, *>> {
    registerEndpoint<FrameUpdate, SimulationSessionScope>("simulation.frame.update") {
        val evaluationArena = get<EvaluationArena>()
        evaluationArena.processFrame(it.data)?.let { frameOutput ->
            messageWriter.sendAllMessage(
                BroadcastMessage("simulation.frame.output", frameOutput),
                FrameOutput.serializer()
            )
        }
        receivedAnyMessages = true
    }

    registerEndpoint<NoData, SimulationSessionScope>("simulation.reset.game") {
        get<EvaluationArena>().resetEvaluation()
    }


    registerEndpoint<NoData, SimulationSessionScope>("simulation.pause") {
        val evaluationArena = get<EvaluationArena>()
        evaluationArena.pause()
    }

    registerEndpoint<NoData, SimulationSessionScope>("simulation.resume") {
        val evaluationArena = get<EvaluationArena>()
        evaluationArena.resetEvaluation()
        evaluationArena.resume()
    }
}

@Serializable
object NoData

class SimulationSessionScope(override val scope: Scope, override val messageWriter: MessageWriter) : SessionScope

data class Simulation(
    val initialPopulation: List<NeatMutator>,
    val evaluationArena: EvaluationArena,
    val populationEvolver: PopulationEvolver,
    val adjustedFitnessCalculation: AdjustedFitnessCalculation
)

