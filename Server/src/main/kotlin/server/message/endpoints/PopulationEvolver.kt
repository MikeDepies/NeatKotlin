import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.mutation.*
private val logger = KotlinLogging.logger {  }
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
            mateChance = .5f,
            survivalThreshold = .3f,
            speciesScoreKeeper = scoreKeeper,
            stagnation = 400
        )
        return weightedReproduction(neatExperiment, speciationController, scoredPopulation, generation)
    }



    fun uniformMutationRateDictionary(mutationRate : Float, mutations : List<Mutation>): List<MutationEntry> {
        return mutations.map { (mutationRate / mutations.size) chanceToMutate  it }
    }

}

fun mutationDictionary(): List<MutationEntry> {
    return listOf(
        .9f chanceToMutate getMutateConnections(.1f, 2.5f, 5f),
        .04f chanceToMutate mutateAddNode,
        .08f chanceToMutate mutateAddConnection,
        .9f chanceToMutate getMutateBiasConnections(.9f, 2.5f, 5f),
        .08f chanceToMutate mutateToggleConnection,
        .08f chanceToMutate mutateNodeActivationFunction(),
    )
}