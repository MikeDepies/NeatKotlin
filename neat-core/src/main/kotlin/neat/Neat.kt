package neat

import neat.model.NeatMutator

typealias Generations = Int

typealias EnvironmentQuery = () -> EnvironmentEntryElement
typealias EnvironmentEntryElement = Pair<List<Float>, List<Float>>

data class FitnessModel<T>(val model: T, val score: Float)

fun <T> identity(): (T) -> T = { it }


fun validatePopulation(currentPopulation: List<NeatMutator>) {
    currentPopulation.forEach { neatMutator ->
        validateNeatModel(neatMutator)
    }
}

fun validateNeatModel(neatMutator: NeatMutator) {
    neatMutator.connections.forEach { connectionGene ->
        if (neatMutator.nodes.none { connectionGene.inNode == it.node }
            || neatMutator.nodes.none { connectionGene.outNode == it.node }) {
            error("Couldn't satisfy $connectionGene from node pool ${neatMutator.nodes}")
        }
    }
}

fun List<FitnessModel<NeatMutator>>.toModelScores(adjustedFitness: AdjustedFitnessCalculation): List<ModelScore> {
    println("To Model scores neat.size: ${this.size}")
    return map { fitnessModel ->
        ModelScore(fitnessModel.model, fitnessModel.score, adjustedFitness(fitnessModel))
    }
}

