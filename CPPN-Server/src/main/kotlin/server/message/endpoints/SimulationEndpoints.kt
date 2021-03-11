package server.message.endpoints

import MessageWriter
import PopulationEvolver
import SessionScope
import kotlinx.serialization.*
import mu.*
import neat.*
import neat.model.*
import org.koin.core.scope.*

private val log = KotlinLogging.logger { }
var receivedAnyMessages = false


@Serializable
object NoData

class SimulationSessionScope(override val scope: Scope, override val messageWriter: MessageWriter) : SessionScope

data class Simulation(
    val initialPopulation: List<NeatMutator>,
    val populationEvolver: PopulationEvolver,
    val adjustedFitnessCalculation: AdjustedFitnessCalculation,
    val evaluationId: Int
)

