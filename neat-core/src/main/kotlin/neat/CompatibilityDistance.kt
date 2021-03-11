package neat

import neat.model.NeatMutator
import kotlin.math.*

fun compatibilityDistance(
    parent1: NeatMutator,
    parent2: NeatMutator,
    excessWeight: Float,
    disjointWeight: Float,
    weightDeltaWeight: Float,
    normalizationThreshold: Int = 20
): Float {
    val excessCount = excess(parent1, parent2).size()
    val disjointCount = disjoint(parent1, parent2).run { disjoint1 + disjoint2 }.size
    val matchingGenes = matchingGenes(parent1, parent2)
    val averageSharedWeights =
        matchingGenes.map { (it.first.weight - it.second.weight).absoluteValue }.sum().div(matchingGenes.size)
    val maxGenes = max(parent1.connections.size, parent2.connections.size)
    val numberOfGenes = if (maxGenes < normalizationThreshold) 1 else maxGenes

    return compatibilityDifference(
        excessCount,
        disjointCount,
        averageSharedWeights,
        numberOfGenes,
        excessWeight,
        disjointWeight,
        weightDeltaWeight
    )
}

class CPPNGeneRuler(val weightCoefficient: Float = .5f, val disjointCoefficient: Float =1f) {
    fun measure(parent1: NeatMutator, parent2: NeatMutator): Float {
        return nodeDistance(parent1, parent2) + connectionDistance(parent1, parent2)
    }

    private fun connectionDistance(parent1: NeatMutator, parent2: NeatMutator): Float {
        val connectionDisjoint =
            parent1.connections.count { !parent2.hasConnection(it.innovation) } + parent2.connections.count {
                !parent1.hasConnection(
                    it.innovation
                )
            }
        val connectionDistance = parent2.connections.filter { parent1.hasConnection(it.innovation) }
            .map { (it.weight - parent2.connection(it.innovation).weight).absoluteValue * weightCoefficient }.sum()
        return (connectionDistance + connectionDisjoint * disjointCoefficient) / max(
            parent1.connections.size,
            parent2.connections.size
        )
    }

    private fun nodeDistance(parent1: NeatMutator, parent2: NeatMutator): Float {
        val nodeDisjoint =
            parent1.nodes.count { !parent2.hasNode(it.node) } + parent2.nodes.count { !parent1.hasNode(it.node) }
        val nodeDistance = 0f + parent2.nodes.filter { parent1.hasNode(it.node) }.map {
            val node = parent1.node(it.node)
            val activationFunctionDistance = if (node.activationFunction == it.activationFunction) 0f else 1f
            val biasDistance = (it.bias - node.bias).absoluteValue
            val distance = biasDistance + activationFunctionDistance
            distance * weightCoefficient
        }.sum()
        return (nodeDistance + nodeDisjoint * disjointCoefficient) / max(parent1.nodes.size, parent2.nodes.size)
    }


}