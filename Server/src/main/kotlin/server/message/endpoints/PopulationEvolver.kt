import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.mutation.*

private val logger = KotlinLogging.logger { }

class PopulationEvolver(
    val speciationController: SpeciationController,
    val scoreKeeper: SpeciesScoreKeeper,
    var speciesLineage: SpeciesLineage,
    val neatExperiment: NeatExperiment,
    var generation: Int = 0,
    val standardCompatibilityTest: CompatibilityTest
) {
    val stagnation = 40
    fun speciate(population: List<NeatMutator>) {

        speciesLineage = SpeciesLineage(speciesLineage.species.map { speciesLineage.speciesGene(it) }.filter {
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
            mateChance = .8f,
            survivalThreshold = .2f,
            speciesScoreKeeper = scoreKeeper,
            stagnation = stagnation,
            championThreshold = 5
        )
        return weightedReproduction(neatExperiment, speciationController, scoredPopulation, generation)
    }


    fun uniformMutationRateDictionary(mutationRate: Float, mutations: List<Mutation>): List<MutationEntry> {
        return mutations.map { (mutationRate / mutations.size) chanceToMutate it }
    }

}

fun createMutationDictionary(): List<MutationEntry> {
    val connectionMutations = listOf(
        getMutateConnections(chanceToReassignWeights = .1f, perturbRange = .001f, assignRange = 8f),
        getMutateBiasConnections(.1f, .001f, 8f)
    )
    return listOf(
        .9f chanceToMutate multiMutation(connectionMutations),
        .01f chanceToMutate mutateAddNode,
        .03f chanceToMutate mutateAddConnection,
        .03f chanceToMutate mutateToggleConnection,
        .03f chanceToMutate mutateNodeActivationFunction(),
    )
}

fun mutateNodeActivationFunction(): Mutation = { neatMutator ->
    val nodeGene = (neatMutator.hiddenNodes + neatMutator.outputNodes).random(random)
    nodeGene.activationFunction = (activationFunctions - nodeGene.activationFunction).random(random)
}

fun multiMutation(mutations: List<Mutation>): Mutation = { neatMutator ->
    mutations.forEach { it(this, neatMutator) }
}