package neat

import kotlin.math.absoluteValue
import kotlin.random.Random

suspend fun runNodeCountExample() {
    val activationFunctions = baseActivationFunctions()
    val distanceFunction: DistanceFunction = { a, b -> compatibilityDistance(a, b, 1f, 1f, .4f) }
    val sharingFunction = shFunction(10f)
    val simpleNeatExperiment = simpleNeatExperiment(Random(0), 0, 0, activationFunctions, 0)
    val population = simpleNeatExperiment.generateInitialPopulation(100, 3, 1, Activation.sigmoidal)
    val speciationController = SpeciationController(0, )
    val speciesScoreKeeper = SpeciesScoreKeeper()
    val generationRules = GenerationRules(
        speciationController = speciationController,
        adjustedFitness = adjustedFitnessCalculation(speciationController, distanceFunction, sharingFunction),
        reproductionStrategy = weightedReproduction(speciesScoreKeeper, mutationDictionary(), .41f, .7f, 15, 1),
        populationEvaluator = { population ->
            population.map { FitnessModel(it, 32f - (32 - it.nodes.size).absoluteValue) }
        }
    )
//    val neat = Neat(generationRules)
    /*{
        val species = speciesScoreKeeper.bestSpecies()
        val modelScore = speciesScoreKeeper.getModelScore(species)
        println("$species - ${modelScore?.fitness}")
    }*/
//    neat.process(
//        times = 100,
//        population = population,
//        speciesScoreKeeper = speciesScoreKeeper,
//        speciesLineage = SpeciesLineage(listOf()),
//        simpleNeatExperiment = simpleNeatExperiment
//    )
    return speciesScoreKeeper.run { getModelScore(bestSpecies()) }
}