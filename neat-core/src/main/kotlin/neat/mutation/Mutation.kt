package neat.mutation

import neat.NeatExperiment
import neat.model.NeatMutator
import neat.randomWeight

const val standardWeightPerturbationRange = .02f

/**
 * A neat.neat.mutation.Mutation that can be applied to a mutator in the context of a given experiment.
 */
typealias Mutation = NeatExperiment.(NeatMutator) -> Unit

fun mutateNodeActivationFunction(): Mutation = { neatMutator ->
    val nodeGene = (neatMutator.hiddenNodes + neatMutator.outputNodes).random(random)
    nodeGene.activationFunction = (activationFunctions - nodeGene.activationFunction).random(random)
}

fun mutateNodeActivationFunctionWithConnectionInnovations(): Mutation = { neatMutator ->
    val nodeGene = (neatMutator.hiddenNodes + neatMutator.outputNodes).random(random)
    val connections = neatMutator.connectionsFrom(nodeGene) + neatMutator.connectionsTo(nodeGene)
    connections.forEach { it.innovation = nextInnovation() }
    neatMutator.modifiedConnections()
    nodeGene.activationFunction = (activationFunctions - nodeGene.activationFunction).random(random)
}

fun mutatePerturbBiasConnections(range: Float = standardWeightPerturbationRange): Mutation =
    { neatMutator ->
        (neatMutator.hiddenNodes + neatMutator.outputNodes).forEach {
            val weightPerturbation = weightPerturbation(range)
            it.bias += weightPerturbation
        }
        /*val node = neatMutator.inputNodes[biasNode]
        neatMutator.connectionsFrom(node).forEach { connectionGene ->
            val weightPerturbation = weightPerturbation(range)
            connectionGene.weight += weightPerturbation
        }*/
    }


fun getMutateConnections(chanceToReassignWeights: Float, perturbRange : Float = standardWeightPerturbationRange, assignRange : Float = 2f): Mutation = { neatMutator ->
    val c = getMutateConnectionWeight(chanceToReassignWeights, perturbRange, assignRange)
    neatMutator.connections.forEach { connectionGene ->
        c(connectionGene)
    }
}
fun getMutateBiasConnections(chanceToReassignWeights: Float, perturbRange : Float = standardWeightPerturbationRange, assignRange : Float = 2f): Mutation = { neatMutator ->
    val perturb = this.random.nextFloat() > chanceToReassignWeights
    (neatMutator.hiddenNodes + neatMutator.outputNodes).forEach {
        if (perturb) {
            val weightPerturbation = weightPerturbation(perturbRange)
            it.bias += weightPerturbation
        } else {
            it.bias = weightPerturbation(assignRange)
        }
    }
    /*val node = neatMutator.inputNodes[biasNode]
    neatMutator.connectionsFrom(node).forEach { connectionGene ->
        val weightPerturbation = weightPerturbation(range)
        connectionGene.weight += weightPerturbation
    }*/
}

val mutateAddConnection: Mutation = { mutateAddConnection(it) }
val mutateDisableConnection: Mutation = { neatMutator ->
    val activeConnections = neatMutator.connections.filter { it.enabled }
    if (activeConnections.isNotEmpty()) {
        val randomActiveConnection = activeConnections.random(random)
        randomActiveConnection.enabled = false
    }
}
val mutateToggleConnection: Mutation = { neatMutator ->
    with(neatMutator.connections.random(random)) {
        enabled = !enabled
    }
}
val mutateAddNode: Mutation = { mutateAddNode(it) }