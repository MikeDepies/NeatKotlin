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
            adjustedPopulationScore.getValue(neatMutator).adjustedFitness
        }
        speciationController.sortSpeciesByFitness(fitnessForModel)
        return modelScoreList
    }

    fun evolveNewPopulation(scoredPopulation: List<ModelScore>): List<NeatMutator> {
        val mutationEntries = mutationDictionary()
        val weightedReproduction = weightedReproduction(
            mutationEntries = mutationEntries,
            mateChance = .6f,
            survivalThreshold = .2f
        )
        return weightedReproduction(neatExperiment, speciationController, scoredPopulation)
    }

    fun mutationDictionary(): List<MutationEntry> {
        return listOf(
            .3f chanceToMutate mutateConnections,
            .3f chanceToMutate mutateAddNode,
            .2f chanceToMutate mutateAddConnection,
            .2f chanceToMutate mutatePerturbBiasConnections(),
            .21f chanceToMutate mutateToggleConnection,
            .1f chanceToMutate mutateNodeActivationFunction(),
        )
    }


}