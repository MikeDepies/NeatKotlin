package neat

import kotlin.math.absoluteValue
import kotlin.random.Random

suspend fun runWeightSummationExample() {
//    val activationFunctions = listOf(SigmoidalTransferFunction, Identity)
    val dataFunction: DistanceFunction = { a, b -> compatibilityDistance(a, b, 1f, 1f, .4f) }
    val sharingFunction = shFunction(1f)
    val simpleNeatExperiment = simpleNeatExperiment(Random(0), 0, 0, baseActivationFunctions(), 0)
    val population = simpleNeatExperiment.generateInitialPopulation(100, 3, 1, Activation.sigmoidal)
    val speciationController = SpeciationController(0, )
    val speciesScoreKeeper = SpeciesScoreKeeper()
    val generationRules = GenerationRules(
        speciationController = speciationController,
        adjustedFitness = adjustedFitnessCalculation(speciationController, dataFunction, sharingFunction),
        reproductionStrategy = weightedReproduction(speciesScoreKeeper, mutationDictionary(), .41f, .7f, 15, 1),
        populationEvaluator = { population ->
            population.map { FitnessModel(it, 100f - (100 - it.connections.map { c -> c.weight }.sum()).absoluteValue) }
        }
    )
//    val neat = Neat(generationRules)
    /*{
        val species = speciesScoreKeeper.bestSpecies()
        val modelScore = speciesScoreKeeper.getModelScore(species)
        println("$species - ${modelScore?.fitness}")
    }*/
//    neat.process(100, population, speciesScoreKeeper, SpeciesLineage(listOf()), simpleNeatExperiment)
}