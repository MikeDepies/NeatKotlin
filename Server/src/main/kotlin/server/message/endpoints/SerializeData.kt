package server.message.endpoints

import kotlinx.serialization.Serializable
import neat.*
import neat.model.ConnectionGene
import neat.model.NeatMutator
import neat.model.NodeGene
import neat.model.NodeType
import neat.model.NodeType.*
import server.toNeatMutator

@Serializable
enum class NodeTypeModel {
    Input, Hidden, Output
}

@Serializable
data class NeatModel(val nodes: List<NodeGeneModel>, val connections: List<ConnectionGeneModel>)

@Serializable
data class NodeGeneModel(val node: Int, val bias : Float, val nodeType: NodeTypeModel, val activationFunction: String)

@Serializable
data class ConnectionGeneModel(
    val inNode: Int,
    val outNode: Int,
    val weight: Float,
    val enabled: Boolean,
    val innovation: Int
)

@Serializable
data class PopulationModel(val models: List<NeatModel>)

fun List<NeatMutator>.toModel() = map { it.toModel() }
fun NeatMutator.toModel() = NeatModel(nodes.map { it.toModel() }, connections.map { it.toModel() })
fun ConnectionGene.toModel() = ConnectionGeneModel(inNode, outNode, weight, enabled, innovation)
fun NodeGene.toModel() = NodeGeneModel(node, bias, nodeType.toModel(), activationFunction.name)
fun NodeType.toModel() = when (this) {
    Input -> NodeTypeModel.Input
    Hidden -> NodeTypeModel.Hidden
    Output -> NodeTypeModel.Output
}

@Serializable
data class SpeciesGeneModel(val speciesModel: Int, val generationBorn: Int, val mascot: NeatModel)

@Serializable
data class ModelScoreModel(val agent: NeatModel, val fitness: Float, val adjustedFitness: Float)

@Serializable
data class SpeciesScoreKeeperModel(val scoreMap: Map<Int, ModelScoreModel>)

@Serializable
data class SpeciesLineageModel(val speciesMap: Map<Int, SpeciesGeneModel>)

fun SpeciesGene.toModel() = SpeciesGeneModel(species.id, generationBorn, mascot.toModel())
fun ModelScore.toModel() = ModelScoreModel(neatMutator.toModel(), fitness, adjustedFitness)
fun SpeciesScoreKeeper.toModel() =
    SpeciesScoreKeeperModel(this.speciesScoreMap.map { it.key.id to it.value.toModel() }.toMap())

fun SpeciesLineage.toModel() = SpeciesLineageModel(speciesLineageMap.map { it.key.id to it.value.toModel() }.toMap())

fun SpeciesGeneModel.toGene() = SpeciesGene(Species(speciesModel), generationBorn, mascot.toNeatMutator())
fun ModelScoreModel.toModelScore() = ModelScore(agent.toNeatMutator(), fitness, adjustedFitness)
fun SpeciesScoreKeeperModel.toScoreKeeper() =
    SpeciesScoreKeeper().also { scoreMap.map { Species(it.key) to it.value.toModelScore() } }