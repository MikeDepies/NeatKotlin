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
    val averageSharedWeights = matchingGenes.map { (it.first.weight - it.second.weight).absoluteValue }.sum().div(matchingGenes.size)
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