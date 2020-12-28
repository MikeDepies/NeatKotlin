package neat

import neat.model.NeatMutator
import neat.mutation.Mutation
import kotlin.math.max

fun adjustedFitnessCalculation(
    population: List<NeatMutator>,
    model: FitnessModel<NeatMutator>,
    distanceFunction: DistanceFunction,
    sharingFunction: SharingFunction
): Float {
    val sum = population.map {
        val distanceFunction1 = distanceFunction(model.model, it)
        val sharingFunction1 = sharingFunction(distanceFunction1)
        sharingFunction1.toFloat()
    }.sum()
    return if (sum == 0f) model.score else model.score / sum
}
typealias SharingFunction = (Float) -> Int

fun shFunction(deltaThreshold: Float): SharingFunction = { if (it < deltaThreshold) 1 else 0 }

typealias DistanceFunction = (NeatMutator, NeatMutator) -> Float

infix fun Float.chanceToMutate(mutation: Mutation) = MutationEntry(rollFrom(this), mutation)

/**
 * Rolls (as in probability dice sense) to see if a neat.neat.mutation can occur.
 */
typealias MutationRoll = NeatExperiment.() -> Boolean

/**
 * Pairing data structure between neat.neat.mutation condition trigger and the associated neat.neat.mutation.
 */
data class MutationEntry(val roll: MutationRoll, val mutation: Mutation)

/**
 * Helper function to generate a neat.MutationRoll based on simple chance by rolling a number between 0 and 1.
 * If the roll is less than the provided chance, the event returns true. False otherwise.
 */
fun rollFrom(chance: Float): MutationRoll = {
    (random.nextFloat() <= chance)
}

fun MutationEntry.mutate(neatExperiment: NeatExperiment, neatMutator: NeatMutator) {
    if (roll(neatExperiment)) {
        mutation(neatExperiment, neatMutator)
    }
}