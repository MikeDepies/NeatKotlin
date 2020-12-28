package setup

import neat.Activation
import neat.model.ConnectionGene
import neat.Identity
import neat.model.NeatMutator
import neat.model.NodeGene
import neat.model.NodeType
import neat.model.simpleNeatMutator
import neat.randomWeight
import kotlin.random.*

data class CrossOverCandidate(val parent1: NeatMutator, val parent2: NeatMutator)

fun createTwoRelatedGenomes(random: Random): CrossOverCandidate {
    fun weight() = randomWeight(random)
    val nodeGenes1 = listOf(
        NodeGene(1, NodeType.Input, Activation.identity),
        NodeGene(2, NodeType.Input, Activation.identity),
        NodeGene(3, NodeType.Input, Activation.identity),
        NodeGene(4, NodeType.Output, Activation.identity),
        NodeGene(5, NodeType.Hidden, Activation.identity)
    )

    val connectionGenes1 = listOf(
        ConnectionGene(1, 4, weight(), true, 1),
        ConnectionGene(2, 4, weight(), false, 2),
        ConnectionGene(3, 4, weight(), true, 3),
        ConnectionGene(2, 5, weight(), true, 4),
        ConnectionGene(5, 4, weight(), true, 5),
        ConnectionGene(1, 5, weight(), true, 8)
    )


    val nodeGenes2 = listOf(
        NodeGene(1, NodeType.Input, Activation.identity),
        NodeGene(2, NodeType.Input, Activation.identity),
        NodeGene(3, NodeType.Input, Activation.identity),
        NodeGene(4, NodeType.Output, Activation.identity),
        NodeGene(5, NodeType.Hidden, Activation.identity),
        NodeGene(6, NodeType.Hidden, Activation.identity)
    )

    val connectionGenes2 = listOf(
        ConnectionGene(1, 4, weight(), true, 1),
        ConnectionGene(2, 4, weight(), false, 2),
        ConnectionGene(3, 4, weight(), true, 3),
        ConnectionGene(2, 5, weight(), true, 4),
        ConnectionGene(5, 4, weight(), false, 5),
        ConnectionGene(5, 6, weight(), true, 6),
        ConnectionGene(6, 4, weight(), true, 7),
        ConnectionGene(3, 4, weight(), true, 9),
        ConnectionGene(1, 6, weight(), true, 10)
    )
    return CrossOverCandidate(
        simpleNeatMutator(nodeGenes1, connectionGenes1),
        simpleNeatMutator(nodeGenes2, connectionGenes2)
    )
}