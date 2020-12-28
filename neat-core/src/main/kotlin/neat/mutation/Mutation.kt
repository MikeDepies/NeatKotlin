package neat.mutation

import neat.NeatExperiment
import neat.model.NeatMutator

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

fun mutatePerturbBiasConnections(biasNode: Int = 0, range: Float = standardWeightPerturbationRange): Mutation =
    { neatMutator ->
        val node = neatMutator.inputNodes[biasNode]
        neatMutator.connectionsFrom(node).forEach { connectionGene ->
            val weightPerturbation = weightPerturbation(range)
            connectionGene.weight += weightPerturbation
        }
    }


val mutateConnections: Mutation = { neatMutator ->
    neatMutator.connections.forEach { connectionGene ->
        mutateConnectionWeight(connectionGene)
    }
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