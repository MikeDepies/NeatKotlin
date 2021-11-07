import neat.*
import neat.model.NeatMutator
import neat.mutation.*

class PopulationEvolver(
    val speciationController: SpeciationController,
    val scoreKeeper: SpeciesScoreKeeper,
    val speciesLineage: SpeciesLineage,
    val neatExperiment: NeatExperiment,
    var generation: Int = 0,
    val standardCompatibilityTest: CompatibilityTest
) {

    fun speciate(population: List<NeatMutator>) {
        speciationController.speciate(population, speciesLineage, generation++, standardCompatibilityTest)
    }

    fun updateScores(updatedModelScores: List<ModelScore>) {
        val map = updatedModelScores.map { speciationController.species(it.neatMutator) to it }
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

    fun evolveNewPopulation(scoredPopulation: List<ModelScore>): List<NeatMutator> {
        val mutationEntries = mutationDictionary()/*uniformMutationRate(
            .7f, listOf(
                mutateConnections, mutateAddNode, mutateAddConnection, mutatePerturbBiasConnections(),
                mutateToggleConnection,
                mutateNodeActivationFunction(),
            )
        )*/
        val weightedReproduction = weightedReproduction(
            mutationEntries = mutationEntries,
            mateChance = .1f,
            survivalThreshold = .40f,
            speciesScoreKeeper = scoreKeeper,
            stagnation = 30
        )
        return weightedReproduction(neatExperiment, speciationController, scoredPopulation, generation)
    }



    fun uniformMutationRateDictionary(mutationRate : Float, mutations : List<Mutation>): List<MutationEntry> {
        return mutations.map { (mutationRate / mutations.size) chanceToMutate  it }
    }

}

fun mutationDictionary(): List<MutationEntry> {
    return listOf(
        .6f chanceToMutate getMutateConnections(.1f),
        .2f chanceToMutate mutateAddNode,
        .2f chanceToMutate mutateAddConnection,
        .6f chanceToMutate mutatePerturbBiasConnections(),
        .1f chanceToMutate mutateToggleConnection,
        .2f chanceToMutate mutateNodeActivationFunction(),
    )
}