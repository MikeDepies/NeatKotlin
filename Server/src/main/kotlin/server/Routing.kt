package server

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import org.jetbrains.exposed.sql.transactions.experimental.*
import server.database.*
import server.message.endpoints.*
import java.time.*

class SimulationRouting(private val application: Application) {
    fun configureRoutes(controllers: List<IOController>) = application.routing {
        get("simulations") {
            val simulationEntries = newSuspendedTransaction {
                getSimulationEntries()
            }
            call.respond(simulationEntries)
        }
        get("evaluation") {

            call.respond(evaluationContext(controllers, 0))
        }
    }

    private fun getSimulationEntries() = SimulationEntity.all().map { simulationEntity ->
        simulationEntry(simulationEntity)
    }

    private fun simulationEntry(simulationEntity: SimulationEntity): SimulationEntry {
        val id = simulationEntity.id.value
        val evaluations = simulationEntity.evaluations.map { evaluationEntity ->
            evaluationEntry(evaluationEntity, id)
        }
        return SimulationEntry(id, evaluations,
            simulationEntity.stage.let { MeleeStage.forId(it.stageId) },
            simulationEntity.startDate)
    }

    private fun evaluationEntry(
        evaluationEntity: EvaluationEntity,
        id: Int
    ): EvaluationEntry {
        val configuration = evalConfiguration(evaluationEntity.configurations.first())
        val population = evaluationEntity.populations.map { populationEntity ->
            evalPopulation(populationEntity)
        }
        val species = evaluationEntity.species.map { speciesEntity ->
            val score = speciesEntity.scoreHistory.lastOrNull()?.let {
                EvalScore(
                    it.score,
                    it.agent.id.value,
                    it.generation
                )
            }
            EvalSpecies(
                speciesEntity.speciesId,
                score,
                speciesEntity.mascot.id.value
            )
        }
        return EvaluationEntry(
            evaluationEntity.id.value, id, population, species,
            configuration
        )
    }

    private fun evalConfiguration(config: EvaluationConfigurationEntity): EvalConfiguration {
        val configurationParameters = evalConfigurationParameters(config.parameters)
        return EvalConfiguration(config.id.value,
            config.evaluation.id.value,
            configurationParameters,
            config.activationFunction.map { it.activationFunction.name },
            config.mutationDictionary.map { EvalMutation(it.chanceToMutate, it.mutation) },
            config.controllers.map {
                EvalController(
                    it.controllerId,
                    MeleeCharacter.forId(it.character.characterId)
                )
            }
        )
    }

    private fun evalConfigurationParameters(param: EvaluationConfigurationParameterEntity) =
        EvalConfigurationParameters(
            param.seed,
            param.speciesDistance,
            EvalSpeciationConfiguration(
                param.speciationDisjoint,
                param.speciationExcess,
                param.speciationAvgConnectionWeight
            ),
            param.survivalThreshold,
            param.size,
            param.mateChance
        )

    private fun evalPopulation(populationEntity: EvaluationPopulationEntity) = EvalPopulation(
        populationEntity.generation,
        populationEntity.agents.map {
            val nodes = it.nodes.map { nodeEntity ->
                NodeGeneModel(
                    nodeEntity.nodeId,
                    nodeTypeFromEntity(nodeEntity),
                    nodeEntity.activationFunction.name
                )
            }
            val connections = it.connections.map { connectionEntity ->
                ConnectionGeneModel(
                    connectionEntity.inNode,
                    connectionEntity.outNode,
                    connectionEntity.weight,
                    connectionEntity.enabled,
                    connectionEntity.innovation
                )
            }
            EvalAgent(it.id.value, it.species, nodes, connections)
        })

    private fun nodeTypeFromEntity(it: AgentNodeEntity) = when (it.type.name) {
        "hidden" -> NodeTypeModel.Hidden
        "input" -> NodeTypeModel.Input
        "output" -> NodeTypeModel.Output
        else -> error("No matching node type")
    }
}

@Serializable
data class SimulationEntry(
    val id: Int,
    val evaluations: List<EvaluationEntry>,
    val stage: MeleeStage,
    val startDate: @Serializable(InstantSerializer::class) Instant
)

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
