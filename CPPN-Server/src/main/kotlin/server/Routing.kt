package server

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import org.jetbrains.exposed.sql.transactions.experimental.*
import server.message.endpoints.*
import java.time.*



object InstantSerializer : KSerializer<Instant> {
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.ofEpochMilli(decoder.decodeLong())
    }

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }

}

@Serializable
data class EvaluationEntry(
    val id: Int,
    val simulationId: Int,
    val populations: List<EvalPopulation>,
    val species: List<EvalSpecies>,
    val configuration: EvalConfiguration
)

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
data class EvalMutation(val chance: Float, val mutation: String)

@Serializable
data class EvalAgent(
    val id: Int,
    val speciesId: Int,
    val nodes: List<NodeGeneModel>,
    val connections: List<ConnectionGeneModel>
)
