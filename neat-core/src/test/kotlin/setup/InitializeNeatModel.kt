package setup

import neat.*
import neat.model.NeatMutator
import neat.model.NodeGene
import neat.model.NodeType
import neat.model.neatMutator
import kotlin.random.*

fun initializeCyclicConnectionsNeatModel(
    random: Random, outputActivation: ActivationGene = Activation.identity, hiddenActivation: ActivationGene = Activation.identity
): NeatMutator {
    return neatMutator(1, 1, random, function = outputActivation).apply {
        val nodeGene = NodeGene(2, NodeType.Hidden, hiddenActivation)
        addNode(nodeGene)
        addConnection(
            connectNodes(
                inputNodes[0], nodeGene, randomWeight(random), 2
            )
        )
        addConnection(
            connectNodes(
                nodeGene, outputNodes[0], randomWeight(random), 3
            )
        )
        addConnection(
            connectNodes(
                nodeGene, nodeGene, randomWeight(random), 4
            )
        )
    }
}

fun initializeNeatModel(random: Random): NeatMutator {
    return neatMutator(1, 1, random).apply {
        val node = NodeGene(2, NodeType.Hidden, Activation.identity)
        val node2 = NodeGene(3, NodeType.Hidden, Activation.identity)
        addNode(node)
        addNode(node2)
        val nodeSource = inputNodes.first()
        addConnection(
            connectNodes(
                nodeSource,
                node,
                randomWeight(random),
                2
            )
        )
        addConnection(
            connectNodes(
                nodeSource,
                node2,
                randomWeight(random),
                3
            )
        )
        addConnection(
            connectNodes(
                node,
                outputNodes.last(),
                randomWeight(random),
                4
            )
        )
        addConnection(
            connectNodes(
                node2,
                outputNodes.last(),
                randomWeight(random),
                5
            )
        )

    }
}