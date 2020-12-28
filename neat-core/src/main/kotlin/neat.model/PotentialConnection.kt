package neat.model

fun PotentialConnection.alreadyConnected(_connections: List<ConnectionGene>): Boolean {
    return _connections.any { it.inNode == sourceNode && it.outNode == targetNode }
}

data class PotentialConnection(val sourceNode: Int, val targetNode: Int, val type: ConnectionType)
enum class ConnectionType {
    UniDirectional, BiDirectional
}