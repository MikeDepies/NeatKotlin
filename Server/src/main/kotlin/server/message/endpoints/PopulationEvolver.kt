import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.mutation.*
private val logger = KotlinLogging.logger {  }
class PopulationEvolver(
    val speciationController: SpeciationController,
    val scoreKeeper: SpeciesScoreKeeper,
    var speciesLineage: SpeciesLineage,
    val neatExperiment: NeatExperiment,
    var generation: Int = 0,
    val standardCompatibilityTest: CompatibilityTest
) {
    val stagnation = 15
    fun speciate(population: List<NeatMutator>) {

        speciesLineage = SpeciesLineage(speciesLineage.species.map {speciesLineage.speciesGene(it)}.filter {
            val generationImproved = scoreKeeper.getModelScore(it.species)?.generationLastImproved ?: 0
            generation - generationImproved <= stagnation
        })
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
        val mutationEntries = createMutationDictionary()
        val weightedReproduction = weightedReproduction(
            mutationEntries = mutationEntries,
            mateChance = .70f,
            survivalThreshold = .4f,
            speciesScoreKeeper = scoreKeeper,
            stagnation = stagnation,
            championThreshold = 5
        )
        return weightedReproduction(neatExperiment, speciationController, scoredPopulation, generation)
    }



    fun uniformMutationRateDictionary(mutationRate : Float, mutations : List<Mutation>): List<MutationEntry> {
        return mutations.map { (mutationRate / mutations.size) chanceToMutate  it }
    }

}

fun createMutationDictionary(): List<MutationEntry> {

    return listOf(
        .9f chanceToMutate getMutateConnections(.1f, .02f, 4f),
        .001f chanceToMutate mutateAddNode,
        .1f chanceToMutate mutateAddConnection,
        .9f chanceToMutate getMutateBiasConnections(.1f, .02f, 4f),
        .01f chanceToMutate mutateToggleConnection,
        .01f chanceToMutate mutateNodeActivationFunction(),
    )
}

fun mutateNodeActivationFunction(): Mutation = { neatMutator ->
    val nodeGene = (neatMutator.hiddenNodes + neatMutator.outputNodes[0]).random(random)
    nodeGene.activationFunction = (activationFunctions - nodeGene.activationFunction).random(random)
}