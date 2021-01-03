package server.message.endpoints

import kotlinx.serialization.Serializable
import neat.model.ConnectionGene
import neat.model.NeatMutator
import neat.model.NodeGene
import neat.model.NodeType
import neat.model.NodeType.*

@Serializable
enum class NodeTypeModel {
    Input, Hidden, Output
}
@Serializable
data class NeatModel(val nodes: List<NodeGeneModel>, val connections: List<ConnectionGeneModel>)

@Serializable
data class NodeGeneModel(val node: Int, val nodeType: NodeTypeModel, val activationFunction: String)

@Serializable
data class ConnectionGeneModel(
    val inNode: Int,
    val outNode: Int,
    val weight: Float,
    val enabled: Boolean,
    val innovation: Int
)
@Serializable
data class PopulationModel(val models : List<NeatModel>)
fun List<NeatMutator>.toModel() = map { it.toModel() }
fun NeatMutator.toModel() = NeatModel(nodes.map { it.toModel() }, connections.map { it.toModel() })
fun ConnectionGene.toModel() = ConnectionGeneModel(inNode, outNode, weight, enabled, innovation)
fun NodeGene.toModel() = NodeGeneModel(node, nodeType.toModel(), activationFunction.name)
fun NodeType.toModel() = when (this) {
    Input -> NodeTypeModel.Input
    Hidden -> NodeTypeModel.Hidden
    Output -> NodeTypeModel.Output
}