package neat

import neat.model.*
import kotlin.random.Random

/**
 * Random/configuration part of the neat.neat algorithm. This will utilize a Mutator that can peform the desire operation,
 * where as the experiment decides on the type of operations
 */
interface NeatExperiment {
    val activationFunctions: List<ActivationGene>
    val random: Random
    fun mutateAddConnection(neatMutator: NeatMutator)
    fun mutateAddNode(neatMutator: NeatMutator)
    fun nextInnovation(): Int
    fun crossover(parent1: FitnessModel<NeatMutator>, parent2: FitnessModel<NeatMutator>): NeatMutator
    fun nextNode(): Int
}


fun NeatExperiment.createNeatMutator(
    inputNumber: Int,
    outputNumber: Int,
    random: Random = Random,
    function: ActivationGene = Activation.identity
): NeatMutator {
    val simpleNeatMutator = simpleNeatMutator(listOf(), listOf())
    createNodes(inputNumber, NodeType.Input, Activation.identity, simpleNeatMutator)
    createNodes(outputNumber, NodeType.Output, function, simpleNeatMutator)
    connectNodes(simpleNeatMutator, random)
    return simpleNeatMutator
}

fun NeatExperiment.createNodes(
    numberOfNodes: Int, nodeType: NodeType, activationFunction: ActivationGene, neatMutator: NeatMutator
) = repeat(numberOfNodes) {
    neatMutator.addNode(NodeGene(nextNode(), nodeType, activationFunction))
}

fun NeatExperiment.newConnection(input: NodeGene, output: NodeGene, neatMutator: NeatMutator) {
    val weight = random.nextFloat()
    neatMutator.addConnection(ConnectionGene(input.node, output.node, weight, true, nextInnovation()))
}

fun NeatExperiment.connectNodes(simpleNeatMutator: NeatMutator, random: Random) {
    for (input in simpleNeatMutator.inputNodes) {
        for (output in simpleNeatMutator.outputNodes) {
            newConnection(input, output, simpleNeatMutator)
        }
    }
}
