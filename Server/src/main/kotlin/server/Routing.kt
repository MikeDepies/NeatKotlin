package server

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

import server.database.*
import server.message.endpoints.*
import java.time.*


@Serializable
data class EvalPopulation(val generation: Int, val agents: List<EvalAgent>)

@Serializable
data class EvalSpecies(val id: Int, val score: EvalScore?, val agentId: Int)

@Serializable
data class EvalScore(val score: Float, val agentId: Int, val generation: Int)

@Serializable
data class EvalConfiguration(
    val id: Int,
    val evaluationId: Int,
    val parameters: EvalConfigurationParameters,
    val activationFunction: List<String>,
    val mutations: List<EvalMutation>,
    val controllers: List<EvalController>
)

@Serializable
data class EvalConfigurationParameters(
    val seed: Int,
    val speciesDistance: Float,
    val speciationConfiguration: EvalSpeciationConfiguration,
    val survivalThreshold: Float,
    val populationSize: Int,
    val mateChance: Float
)

@Serializable
data class EvalSpeciationConfiguration(
    val disjoint: Float,
    val excess: Float,
    val averageConnectionWeight: Float
)

@Serializable
data class EvalController(val controllerId: Int, val character: MeleeCharacter)

@Serializable
data class EvalMutation(val chance: Float, val mutation: String)

@Serializable
data class EvalAgent(
    val id: Int,
    val speciesId: Int,
    val nodes: List<NodeGeneModel>,
    val connections: List<ConnectionGeneModel>
)
