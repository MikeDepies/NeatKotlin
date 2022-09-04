package server.model

import kotlinx.serialization.Serializable


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