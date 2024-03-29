package neat

import neat.model.*
import neat.mutation.Gaussian
import java.util.*
import kotlin.NoSuchElementException
import kotlin.jvm.JvmName
import kotlin.random.Random

fun simpleNeatExperiment(
    random: Random,
    innovation: Int,
    nodeInnovation: Int,
    activationFunctions: List<ActivationGene>,
    addConnectionAttempts: Int,
    weightRange: Float
): NeatExperiment {
    return SimpleNeatExperiment(innovation, nodeInnovation, activationFunctions, random, addConnectionAttempts, weightRange)
}

fun matchingGenes(
    parent1: NeatMutator,
    parent2: NeatMutator
): List<Pair<ConnectionGene, ConnectionGene>> {
    return parent1.connections.filter { c1 ->
        parent2.hasConnection(c1.innovation)
    }.map { c1 -> c1 to parent2.connection(c1.innovation) }
}


class SimpleNeatExperiment(
    private var innovation: Int,
    private var nodeInnovation: Int,
    override val activationFunctions: List<ActivationGene>,
    override val random: Random,
    val addConnectionAttempts: Int,
    val weightRange: Float
) : NeatExperiment {
    override val gaussian: Gaussian = Gaussian(random, .5)
//    private var innovation = innovation
//    private var nodeInnovation = nodeInnovation
//    override val neat.random: Random get() = neat.random

    fun connectionGene(potentialConnection: PotentialConnection): ConnectionGene {
        val (sourceNode, targetNode, type) = potentialConnection
        return when (type) {
            ConnectionType.UniDirectional -> connectionGene(sourceNode, targetNode)
            ConnectionType.BiDirectional -> when (random.nextBoolean()) {
                true -> connectionGene(sourceNode, targetNode)
                false -> connectionGene(targetNode, sourceNode)
            }
        }
    }

    private fun connectionGene(sourceNode: Int, targetNode: Int): ConnectionGene {
        return ConnectionGene(
            sourceNode,
            targetNode,
            randomWeight(random, weightRange),
            true,
            nextInnovation()
        )
    }

    override fun mutateAddConnection(neatMutator: NeatMutator) {
        val nodeMap = neatMutator.nodes.groupBy { it.nodeType }
        val connectedNodes = neatMutator.connections.map { it.inNode to it.outNode }
        val sourceList = (nodeMap[NodeType.Hidden] ?: setOf<NodeGene>()) + nodeMap.getValue(NodeType.Input)
        val targetList = (nodeMap[NodeType.Hidden] ?: setOf<NodeGene>()) + nodeMap.getValue(NodeType.Output)

        var attempts = -addConnectionAttempts
        while (attempts++ < 1) {
            val sourceNodeGene = sourceList.random(random)
            val alreadyConnected =
                neatMutator.connectionsFrom(sourceNodeGene).map { it.outNode }.mapNotNull {
                    try {
                        neatMutator.node(it)
                    } catch (e: NoSuchElementException) {
                        null
                    }
                }
            val targetPool = (targetList - sourceNodeGene) - alreadyConnected.toSet()
            if (targetPool.isNotEmpty()) {
                val targetNodeGene = targetPool.random(random)
                val sourceNode = sourceNodeGene.node
                val targetNode = targetNodeGene.node
                fun connectionDoesNotExist() =
                    !connectedNodes.contains(sourceNode to targetNode) && !connectedNodes.contains(targetNode to sourceNode)

                if (connectionDoesNotExist()) {
                    neatMutator.addConnection(connectionGene(sourceNode, targetNode))
                    break
                }

            }
        }
//        if (neat.model.neatMutator.connectableNodes.isNotEmpty()) {
//            val potentialConnection = neat.model.neatMutator.connectableNodes.neat.random(neat.random)
//            neat.model.neatMutator.addConnection(connectionGene(potentialConnection))
//        }
    }

    override fun mutateAddNode(neatMutator: NeatMutator) {
        fun getRandomConnectionGeneWithValidNodes(): ConnectionGene {
            val connections = neatMutator.connections.filter { it.enabled }
            var rConnection = connections.random(random)
            while (!neatMutator.hasNode(rConnection.inNode) || !neatMutator.hasNode(rConnection.outNode) ) {
                rConnection = connections.random(random)
            }
            return rConnection
        }

        val randomConnection = getRandomConnectionGeneWithValidNodes()
        val node = NodeGene(nextNode(), randomWeight(random, weightRange), NodeType.Hidden, activationFunctions.random(random))
        val copiedConnection = randomConnection.copy(innovation = nextInnovation(), inNode = node.node)
        val newEmptyConnection = ConnectionGene(randomConnection.inNode, node.node, 1f, true, nextInnovation())
//        println("\tMUTATE ADD NODE")
//        println("\t${neat.model.neatMutator.connections.neat.condensedString()}\t${neat.model.neatMutator.nodes.neat.condensedString()}")
        randomConnection.enabled = false
        neatMutator.apply {
            addNode(node)
            addConnection(copiedConnection)
            addConnection(newEmptyConnection)
        }
//        println("\t${neat.model.neatMutator.connections.neat.condensedString()}\t${neat.model.neatMutator.nodes.neat.condensedString()}")
    }

    override fun nextInnovation(): Int {
        return this.innovation++
    }

    override fun nextNode(): Int {
        return this.nodeInnovation++
    }

    override fun crossover(parent1: FitnessModel<NeatMutator>, parent2: FitnessModel<NeatMutator>, uuid: UUID): NeatMutator {
        val (disjoint1, disjoint2) = disjoint(parent1.model, parent2.model)
        val excess = excess(parent1.model, parent2.model)
        val matchingGenes = matchingGenes(parent1.model, parent2.model)
//        println("| Matching Genes")
//        neat.matchingGenes.forEach {
//            println(it)
//        }
        val selectedRandomGenes = matchingGenes.map { it.random(random) }
        val offSpringConnections = when {
            parent1.isLessFitThan(parent2) -> {
                (selectedRandomGenes + disjoint2 + excess.excess2).sortedBy { it.innovation }
            }
//            parent1.neat.isMoreFitThan(parent2)
            else -> {
                (selectedRandomGenes + disjoint1 + excess.excess1).sortedBy { it.innovation }
            }
//            else -> {
//                (neat.matchingGenes.map { it.neat.random(neat.random) } + (disjoint1 + disjoint2 + neat.excess).filter { neat.random.nextBoolean() }).sortedBy { it.innovation }
//            }
        }.map { it.copy() }
        val nodes = (if (parent1.isLessFitThan(parent2)) parent2.model.nodes else parent1.model.nodes).map { it.copy() }
        return simpleNeatMutator(nodes, offSpringConnections, uuid)
    }


}

fun NeatExperiment.newNode(activationFunction: ActivationGene): NodeGene {
    return NodeGene(nextNode(), randomWeight(random), NodeType.Hidden, activationFunction)
}

fun List<ConnectionGene>.condensedString(): String {
    fun enabled(connectionGene: ConnectionGene) = if (connectionGene.enabled) "" else "!"
    fun weight(it: ConnectionGene) = "= ${it.weight}"

    return joinToString("\t") { "${enabled(it)}[${it.inNode},${it.outNode}]" }
}


@JvmName("condensedNodeGeneString")
fun List<NodeGene>.condensedString(): String {
    return "[${joinToString(", ") { "${it.node}" }}]"
}

fun crossover(parent1: FitnessModel<NeatMutator>, parent2: FitnessModel<NeatMutator>, random: Random, uuid: UUID): NeatMutator {
    val (disjoint1, disjoint2) = disjoint(parent1.model, parent2.model)
    val excess = excess(parent1.model, parent2.model)
    val matchingGenes = matchingGenes(parent1.model, parent2.model)
    val selectedRandomGenes = matchingGenes.map { it.random(random) }
    val offSpringConnections = when {
        parent1.isLessFitThan(parent2) -> {
            (selectedRandomGenes + disjoint2 + excess.excess2).sortedBy { it.innovation }
        }
        else -> {
            (selectedRandomGenes + disjoint1 + excess.excess1).sortedBy { it.innovation }
        }
    }.map { it.copy() }
    val nodes = (if (parent1.isLessFitThan(parent2)) parent2.model.nodes else parent1.model.nodes).map { it.copy() }
    return simpleNeatMutator(nodes, offSpringConnections, uuid)
}
