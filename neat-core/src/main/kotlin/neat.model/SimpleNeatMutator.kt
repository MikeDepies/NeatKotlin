package neat.model

import neat.toMap

fun simpleNeatMutator(nodes: List<NodeGene>, connections: List<ConnectionGene>): NeatMutator {
//    return neat.model.SimpleNeatMutator(nodes.toMutableList(), connections.toMutableList())
    return SimpleNeatMutator2(
        nodes.toMap { it.node }.toMutableMap(),
        connections.toMap { it.innovation }.toMutableMap()
    )
}


data class SimpleNeatMutator2(
    val nodeMap: MutableMap<Int, NodeGene>,
    val connectionMap: MutableMap<Int, ConnectionGene>
) : NeatMutator {
    override val connections: List<ConnectionGene>
        get() = connectionMap.values.toList()
    override val nodes: List<NodeGene>
        get() = nodeMap.values.toList()

    override fun modifiedConnections() {
        val newConnectionMap = connections.toMap { it.innovation }.toMutableMap()
        connectionMap.clear()
        connectionMap.putAll(newConnectionMap)
    }
    override fun clone(): NeatMutator {
        return copy(
            nodeMap = nodeMap.values.map { it.copy() }.toMap { it.node }.toMutableMap(),
            connectionMap = connectionMap.values.map { it.copy() }.toMap { it.innovation }.toMutableMap()
//            nodes = nodes.map { it.copy() }.toMutableList(),
//            connections = connections.map { it.copy() }.toMutableList()
        )
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun node(node: Int): NodeGene {
        return nodeMap.getValue(node)
    }

    override fun hasNode(node: Int): Boolean {
        return nodeMap.containsKey(node)
    }

    override fun hasConnection(innovation: Int): Boolean {
        return connectionMap.containsKey(innovation)
    }

    override fun connection(innovation: Int): ConnectionGene {
        return connectionMap.getValue(innovation)
    }

    private fun eligibleNodes(
        _nodes: List<NodeGene>,
        sourceNode: NodeGene
    ): List<NodeGene> = _nodes.filterNot {
        val bothAreOutputs = bothAreOutputNodes(sourceNode, it)
        val bothAreInputs = bothAreInputs(sourceNode, it)
        bothAreInputs || bothAreOutputs
    }

    private fun bothAreInputs(sourceNode: NodeGene, it: NodeGene) =
        sourceNode.nodeType == NodeType.Input && it.nodeType == NodeType.Input

    private fun bothAreOutputNodes(sourceNode: NodeGene, it: NodeGene) =
        sourceNode.nodeType == NodeType.Output && it.nodeType == NodeType.Output

    override val hiddenNodes: List<NodeGene>
        get() = nodes.filter { it.nodeType == NodeType.Hidden }
    override val outputNodes: List<NodeGene>
        get() = nodes.filter { it.nodeType == NodeType.Output }
    override val inputNodes: List<NodeGene>
        get() = nodes.filter { it.nodeType == NodeType.Input }
    override val lastNode: NodeGene
        get() = nodes.last()
//    override val connectableNodes: List<PotentialConnection>
//        get() = _connectableNodes.toList()

    override fun addConnection(connectionGene: ConnectionGene) {
        if (!hasNode(connectionGene.inNode))
            error("No matching node to connect to ${connectionGene.inNode}")
        if (!hasNode(connectionGene.outNode))
            error("No matching node to connect to ${connectionGene.outNode}")
        if (connections.any { it.inNode == connectionGene.inNode && it.outNode == connectionGene.outNode })
            return
//            error("Can not add a connection gene between already connected nodes")
        connectionMap[connectionGene.innovation] = connectionGene
//        _connectableNodes -= PotentialConnection(connectionGene.inNode, connectionGene.outNode)
    }

    override fun addNode(node: NodeGene) {
        nodeMap[node.node] = node
//        eligibleNodes(nodes, node).forEach {
//            _connectableNodes.add(PotentialConnection(node.node, it.node))
//        }
    }

    override fun connectionsTo(first: NodeGene): List<ConnectionGene> {
        return connections.filter { it.outNode == first.node }
    }

    override fun connectionsFrom(first: NodeGene): List<ConnectionGene> {
        return connections.filter { it.inNode == first.node }
    }

}