import neat.*
import neat.model.NeatMutator
import neat.mutation.*

class PopulationEvolver(
    var generation: Int = 0,
    val speciationController: SpeciationController,
    val scoreKeeper: SpeciesScoreKeeper,
    var speciesLineage: SpeciesLineage,
    var weightedReproduction: NeatExperiment.(SpeciationController, List<ModelScore>, Int) -> List<NeatMutator>,
) {
    val stagnation = 60
    fun speciate(population: List<NeatMutator>, compatibilityTest: CompatibilityTest) {
        speciesLineage = SpeciesLineage(speciesLineage.species.map { speciesLineage.speciesGene(it) }.filter {
            val generationImproved = scoreKeeper.getModelScore(it.species)?.generationLastImproved ?: 0
            generation - generationImproved <= stagnation
        })
        speciationController.speciate(population, speciesLineage, generation++, compatibilityTest)
    }

    fun updateScores(updatedModelScores: List<ModelScore>) {
        val map = updatedModelScores.filter { speciationController.hasSpeciesFor(it.neatMutator) }
            .map { speciationController.species(it.neatMutator) to it }
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
        return weightedReproduction(experiment, speciationController, scoredPopulation, generation)
    }

}


fun mutateNodeActivationFunction(): Mutation = { neatMutator ->
    val nodeGene = (neatMutator.hiddenNodes + neatMutator.outputNodes).random(random)
    nodeGene.activationFunction = (activationFunctions - nodeGene.activationFunction).random(random)
}

fun createMutationDictionary(): List<MutationEntry> {
    val connectionMutations = listOf(
        getMutateConnections(chanceToReassignWeights = .1f, perturbRange = .01f, assignRange = 2f),

        getMutateBiasConnections(.1f, .01f, 2f)
    )
    return listOf(
        .9f chanceToMutate multiMutation(connectionMutations),
        .1f chanceToMutate mutateAddNode,
        .25f chanceToMutate mutateAddConnection,
        .1f chanceToMutate mutateToggleConnection,
        .05f chanceToMutate mutateNodeActivationFunction(),
    )
}

fun uniformMutationRateDictionary(mutationRate: Float, mutations: List<Mutation>): List<MutationEntry> {
    return mutations.map { (mutationRate / mutations.size) chanceToMutate it }
}

fun getMutateBiasConnections(chanceToReassignWeights: Float, perturbRange : Float = standardWeightPerturbationRange, assignRange : Float = 2f): Mutation = { neatMutator ->
    val perturb = this.random.nextFloat() > chanceToReassignWeights
    (neatMutator.hiddenNodes + neatMutator.outputNodes).forEach {
        if (perturb) {
            val weightPerturbation = weightPerturbation(perturbRange)
            it.bias += weightPerturbation
        } else {
            it.bias = random.nextDouble(-assignRange.toDouble(), assignRange.toDouble()).toFloat()
        }
    }
    /*val node = neatMutator.inputNodes[biasNode]
    neatMutator.connectionsFrom(node).forEach { connectionGene ->
        val weightPerturbation = weightPerturbation(range)
        connectionGene.weight += weightPerturbation
    }*/
}

fun getMutateConnections(chanceToReassignWeights: Float, perturbRange : Float = standardWeightPerturbationRange, assignRange : Float = 2f): Mutation = { neatMutator ->
    val c = getMutateConnectionWeight(chanceToReassignWeights, perturbRange, assignRange)
    neatMutator.connections.forEach { connectionGene ->
        c(connectionGene)
    }
}
fun NeatExperiment.assignConnectionRandomWeight(range :Float = 2f): ConnectionMutation = { weight = random.nextDouble(-range.toDouble(), range.toDouble()).toFloat() }
fun NeatExperiment.getMutateConnectionWeight(chanceToReassignWeights: Float, perturbRange : Float = standardWeightPerturbationRange, assignRange : Float = 2f): ConnectionMutation = ifElseConnectionMutation(
    rollFrom(chanceToReassignWeights),
    assignConnectionRandomWeight(assignRange),
    perturbConnectionWeight(range = perturbRange)
)

fun main() {
    val w = 10
    val h = 10
    (0 until w).forEach { x ->
        val xVal = Activation.CPPN.bipolarSigmoid.activationFunction(((x.toFloat() / w) * 2) - 1f)
        val yVal = Activation.CPPN.linear.activationFunction(((x.toFloat() / w) * 2) - 1)
        println("$xVal, $yVal")
    }
//    (0 until h).forEach { y ->
//
//
//    }
}

fun multiMutation(mutations: List<Mutation>): Mutation = { neatMutator ->
    mutations.forEach { it(this, neatMutator) }
}