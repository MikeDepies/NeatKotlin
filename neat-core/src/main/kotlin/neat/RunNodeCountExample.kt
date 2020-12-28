package neat

import kotlin.math.absoluteValue
import kotlin.random.Random

suspend fun runNodeCountExample() {
    val activationFunctions = baseActivationFunctions()
    val distanceFunction: DistanceFunction = { a, b -> compatibilityDistance(a, b, 1f, 1f, .4f) }
    val sharingFunction = shFunction(10f)
    val simpleNeatExperiment = simpleNeatExperiment(Random(0), 0, 0, activationFunctions)
    val population = simpleNeatExperiment.generateInitialPopulation(100, 3, 1, Activation.sigmoidal)
    val speciationController = SpeciationController(0, standardCompatibilityTest(sharingFunction, distanceFunction))
    val generationRules = GenerationRules(
        speciationController = speciationController,
        adjustedFitness = adjustedFitnessCalculation(speciationController, distanceFunction, sharingFunction),
        reproductionStrategy = weightedReproduction(mutationDictionary(), .41f, .7f),
        populationEvaluator = { population ->
            population.map { FitnessModel(it, 32f - (32 - it.nodes.size).absoluteValue) }
        }
    )
    val speciesScoreKeeper = SpeciesScoreKeeper()
    val neat = Neat(generationRules) /*{
        val species = speciesScoreKeeper.bestSpecies()
        val modelScore = speciesScoreKeeper.getModelScore(species)
        println("$species - ${modelScore?.fitness}")
    }*/
    neat.process(100, population, speciesScoreKeeper, SpeciesLineage(listOf()), simpleNeatExperiment)
    return speciesScoreKeeper.run { getModelScore(bestSpecies()) }
}