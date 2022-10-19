package neat.model

import neat.*
import java.util.UUID
import kotlin.random.Random

interface NeatMutator {
    val id : UUID
    val nodes: List<NodeGene>
    val connections: List<ConnectionGene>
    val hiddenNodes: List<NodeGene>
    val outputNodes: List<NodeGene>
    val inputNodes: List<NodeGene>
//    val connectableNodes: List<PotentialConnection>

    val lastNode: NodeGene
    fun addConnection(connectionGene: ConnectionGene)
    fun addNode(node: NodeGene)
    fun connectionsTo(first: NodeGene): List<ConnectionGene>
    fun connectionsFrom(first: NodeGene): List<ConnectionGene>
    fun clone(uuid : UUID) : NeatMutator

    fun node(node : Int) : NodeGene
    fun hasNode(node : Int) : Boolean
    fun hasConnection(innovation: Int) : Boolean
    fun connection(innovation : Int) : ConnectionGene
    fun modifiedConnections()
}

fun neatMutator(
    inputNumber: Int,
    outputNumber: Int,
    random: Random = Random,
    function: ActivationGene = Activation.identity,
    uuid: UUID
): NeatMutator {
    val simpleNeatMutator = simpleNeatMutator(listOf(), listOf(), uuid)
    var nodeNumber = 0
    var innovation = 0
    repeat(inputNumber) {
        simpleNeatMutator.addNode(NodeGene(nodeNumber++, randomWeight(random), NodeType.Input, Activation.identity))
    }
    repeat(outputNumber) {
        simpleNeatMutator.addNode(NodeGene(nodeNumber++, randomWeight(random), NodeType.Output, function))
    }
    for (input in simpleNeatMutator.inputNodes) {
        for (output in simpleNeatMutator.outputNodes) {
            val weight = randomWeight(random)
            simpleNeatMutator.addConnection(
                ConnectionGene(
                    input.node,
                    output.node,
                    weight,
                    true,
                    innovation++
                )
            )
        }
    }
    return simpleNeatMutator
}