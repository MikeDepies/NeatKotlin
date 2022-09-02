package server.ndarray

import neat.model.NeatMutator
import neat.model.NodeGene
import neat.network.ComputationStrategy
import neat.network.NetworkNode
import neat.network.activate
import neat.network.computationSequence

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
){
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
    }
}