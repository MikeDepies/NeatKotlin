package neat.network

import neat.model.NeatMutator
import neat.model.NodeGene

typealias ComputationStrategy = () -> Unit

fun Set<NodeGene>.activate(map: Map<NodeGene, NetworkNode>) = forEach {
    val value = map.getValue(it)
    value.value += value.bias
    value.activate()
}

fun NeatMutator.getComputationStrategy(
    networkNodeMap: Map<NodeGene, NetworkNode>,
    idNodeMap: Map<Int, NodeGene>
): ComputationStrategy {
    val computationSequence = computationSequence(networkNodeMap, idNodeMap).toList()
    val outputNodeSet = outputNodes.map { networkNodeMap.getValue(it) }
    return {
        computationSequence.forEach { it() }
        outputNodeSet.forEach { it.activate() }
    }

}

fun NeatMutator.computationSequence(
    networkNodeMap: Map<NodeGene, NetworkNode>,
    idNodeMap: Map<Int, NodeGene>
): Sequence<() -> Unit> {
    return sequence {
        val activationSet = mutableSetOf<NodeGene>()
        var activeSet = inputNodes.toSet()
        val model = this@computationSequence
        fun networkNotFullyActivated() = (activationSet.size + outputNodes.size) < nodes.size && activeSet.isNotEmpty()
        while (networkNotFullyActivated()) {
            val capturedSet = activeSet
            val connections = capturedSet.flatMap { node ->
                connectionsFrom(node).filter { it.enabled }
            }

            val nextNodeMap = connections.groupBy {
                idNodeMap.getValue(it.outNode)
            }
            val fn = {
                capturedSet.activate(networkNodeMap)
                connections.forEach { connection ->
                    val inputValue = idNodeMap.getValue(connection.inNode)
                    val outValue = idNodeMap.getValue(connection.outNode)
                    val inputNode = networkNodeMap.getValue(inputValue)
                    val outputNode = networkNodeMap.getValue(outValue)
                    outputNode.value += inputNode.activatedValue * connection.weight
                }
            }
            activeSet.forEach { activationSet += it }
            activeSet = nextNodeMap.keys.filter { it !in activationSet }.toSet()
            yield(fn)
        }
    }
}


fun NeatMutator.computationSequence2(
    networkNodeMap: Map<NodeGene, NetworkNode>,
    idNodeMap: Map<Int, NodeGene>
): Sequence<() -> Unit> {

    TODO()
}