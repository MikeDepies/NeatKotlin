package neat.mutation

import neat.model.ConnectionGene
import neat.MutationRoll
import neat.NeatExperiment
import neat.randomWeight
import neat.rollFrom

typealias ConnectionMutation = ConnectionGene.() -> Unit

fun NeatExperiment.perturbConnectionWeight(range: Float = standardWeightPerturbationRange): ConnectionMutation {
    if (range < 0) error("range [$range] must be greater than 0")
    return {
        val perturbation = weightPerturbation(range)
        weight += perturbation
    }
}

fun NeatExperiment.assignConnectionRandomWeight(range :Float = 2f): ConnectionMutation = { weight = weightPerturbation(range) }
inline fun NeatExperiment.ifElseConnectionMutation(
    crossinline mutationRoll: MutationRoll,
    crossinline onRollSuccess: ConnectionMutation,
    crossinline onRollFailure: ConnectionMutation
): ConnectionMutation = {
    if (mutationRoll()) {
        onRollSuccess()
    } else {
        onRollFailure()
    }
}

/**
 * A configuration found on the web.
 * https://github.com/GabrielTavernini/NeatJS/blob/master/src/connection.js#L12
 */
fun NeatExperiment.getMutateConnectionWeight(chanceToReassignWeights: Float, perturbRange : Float = standardWeightPerturbationRange, assignRange : Float = 2f): ConnectionMutation = ifElseConnectionMutation(
    rollFrom(chanceToReassignWeights),
    assignConnectionRandomWeight(assignRange),
    perturbConnectionWeight(range = perturbRange)
)

fun uniformWeightPerturbation(connectionMutation: ConnectionMutation): Mutation = { neatMutator ->
    neatMutator.connections.forEach { connection ->
        connectionMutation(connection)
    }
}