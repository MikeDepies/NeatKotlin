import neat.*
import neat.model.NeatMutator
import neat.mutation.*

class PopulationEvolver(
    var generation: Int = 0,
    val speciationController: SpeciationController,
    val scoreKeeper: SpeciesScoreKeeper,
    val speciesLineage: SpeciesLineage,
    var weightedReproduction: NeatExperiment.(SpeciationController, List<ModelScore>, Int) -> List<NeatMutator>,
) {

    fun speciate(population: List<NeatMutator>, compatibilityTest: (NeatMutator, NeatMutator) -> Boolean) {
        speciationController.speciate(population, speciesLineage, generation++, compatibilityTest)
    }

    fun updateScores(updatedModelScores: List<ModelScore>) {
        val map = updatedModelScores.filter { speciationController.hasSpeciesFor(it.neatMutator) }.map { speciationController.species(it.neatMutator) to it }
        scoreKeeper.updateScores(map, generation)
    }

    fun sortPopulationByAdjustedScore(
        modelScoreList: List<ModelScore>
    ): List<ModelScore> {
        val adjustedPopulationScore = modelScoreList.toMap { modelScore -> modelScore.neatMutator }
        val fitnessForModel: (NeatMutator) -> Float = { neatMutator ->
            try {
                adjustedPopulationScore.getValue(neatMutator).adjustedFitness
            } catch (e: Exception) {
                logger.warn { "Issue with adjusted fitness" }
                0f
            }
        }
        speciationController.sortSpeciesByFitness(fitnessForModel)
        return modelScoreList
    }

    fun evolveNewPopulation(scoredPopulation: List<ModelScore>, experiment: NeatExperiment): List<NeatMutator> {
        return if ((generation / 20) % 2 == 0) weightedReproduction(experiment, speciationController, scoredPopulation, generation) else weightedReproduction(experiment, speciationController, scoredPopulation, generation)
    }

}


fun createMutationDictionary(): List<MutationEntry> {
    return listOf(
        .4f chanceToMutate getMutateConnections(.1f, 1f),
        .4f chanceToMutate mutateAddNode,
        .4f chanceToMutate mutateAddConnection,
        .4f chanceToMutate mutatePerturbBiasConnections(1f),
        .1f chanceToMutate mutateToggleConnection,
        .2f chanceToMutate mutateNodeActivationFunction(),
    )
}


fun createMutationDictionary2(): List<MutationEntry> {
    return listOf(
        .8f chanceToMutate getMutateConnections(.1f),
        .1f chanceToMutate mutateAddNode,
        .1f chanceToMutate mutateAddConnection,
        .8f chanceToMutate mutatePerturbBiasConnections(),
        .1f chanceToMutate mutateToggleConnection,
        .5f chanceToMutate mutateNodeActivationFunction(),
    )
}

fun uniformMutationRateDictionary(mutationRate : Float, mutations : List<Mutation>): List<MutationEntry> {
    return mutations.map { (mutationRate / mutations.size) chanceToMutate  it }
}
