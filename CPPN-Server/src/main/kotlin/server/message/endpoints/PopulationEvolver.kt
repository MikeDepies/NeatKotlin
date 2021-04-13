import neat.*
import neat.model.NeatMutator
import neat.mutation.*

class PopulationEvolver(
    val speciationController: SpeciationController,
    val scoreKeeper: SpeciesScoreKeeper,
    val speciesLineage: SpeciesLineage,
    val neatExperiment: NeatExperiment,
    var generation: Int = 0
) {

    fun speciate(population: List<NeatMutator>) {
        speciationController.speciate(population, speciesLineage, generation++)
    }

    fun updateScores(updatedModelScores: List<ModelScore>) {
        val map = updatedModelScores.map { speciationController.species(it.neatMutator) to it }
        scoreKeeper.updateScores(map)
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
            survivalThreshold = .4f
        )
        return weightedReproduction(neatExperiment, speciationController, scoredPopulation)
    }

    fun mutationDictionary(): List<MutationEntry> {
        return listOf(
            .8f chanceToMutate mutateConnections,
            .4f chanceToMutate mutateAddNode,
            .6f chanceToMutate mutateAddConnection,
            .2f chanceToMutate mutatePerturbBiasConnections(),
            .11f chanceToMutate mutateToggleConnection,
            .05f chanceToMutate mutateNodeActivationFunction(),
        )
    }

    fun uniformMutationRateDictionary(mutationRate : Float, mutations : List<Mutation>): List<MutationEntry> {
        return mutations.map { (mutationRate / mutations.size) chanceToMutate  it }
    }

}