import neat.*
import neat.model.NeatMutator
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class SpeciationTest {

    @Test
    fun process() {
        val activationFunctions = listOf(Activation.sigmoidal, Activation.identity)
        val mutationEntries = mutationDictionary()
        val df: DistanceFunction = { a, b -> compatibilityDistance(a, b, 1f, 1f, .4f) }
        val sharingFunction = shFunction(3f)
        val simpleNeatExperiment = simpleNeatExperiment(Random(0), 0, 0, activationFunctions, 0)
        val population = simpleNeatExperiment.generateInitialPopulation(6, 3, 1, Activation.sigmoidal)

        val speciationController = SpeciationController(0, standardCompatibilityTest(sharingFunction, df))
        val adjustedFitness = adjustedFitnessCalculation(speciationController, df, sharingFunction)

        val speciesScoreKeeper = SpeciesScoreKeeper()
        val speciesLineage = SpeciesLineage(listOf())
        speciationController.speciate(population, speciesLineage, 0)
        val times = 20
        repeat(times) { generation ->
            println("generation $generation (pop: ${speciationController.population().size}")
            val inputOutput = XORTruthTable().map { it() }
            val modelScoreList = evaluatePopulation(speciationController.population(), inputOutput)
                .toModelScores(adjustedFitness)

            sortModelsByAdjustedFitness(speciationController, modelScoreList)
            val newPopulation =
                populateNextGeneration(
                    speciationController,
                    modelScoreList,
                    mutationEntries,
                    simpleNeatExperiment,
                    1f,
                    .7f
                )
            speciationController.speciate(newPopulation, speciesLineage, generation)
            speciesScoreKeeper.updateScores(modelScoreList.map { speciationController.species(it.neatMutator) to it })
        }
        evaluateAndDisplayBestSpecies(speciesScoreKeeper)
    }

    private fun printReport(speciesReport: SpeciesReport) {
        val keys = speciesReport.speciesMap.keys
        for (species in keys) {
            val first = speciesReport.speciesMap.getValue(species).first()
            println("neat.Species ${species.id} (pop: ${speciesReport.speciesOffspringMap[species]} offspring: ${speciesReport.speciesOffspringMap[species]} topScore= {${first.fitness}, ${first.adjustedFitness}})")
            speciesReport.speciesMap.getValue(species).forEach {
                println("\t${it.neatMutator.connections.condensedString()}\t${it.neatMutator.nodes.condensedString()}")
            }
        }
    }
}


private fun sortModelsByAdjustedFitness(
    speciationController: SpeciationController,
    modelScoreList: List<ModelScore>
): List<ModelScore> {
    val adjustedPopulationScore = modelScoreList.toMap { modelScore -> modelScore.neatMutator }
    val fitnessForModel: (NeatMutator) -> Float =
        { neatMutator -> adjustedPopulationScore.getValue(neatMutator).adjustedFitness }
    speciationController.sortSpeciesByFitness(fitnessForModel)
    return modelScoreList
}

private fun evaluateAndDisplayBestSpecies(speciesScoreKeeper: SpeciesScoreKeeper) {
    val neatMutator: NeatMutator = speciesScoreKeeper.getModelScore(speciesScoreKeeper.bestSpecies())!!.neatMutator
    val data = XORTruthTable().map { it() }
    val network = neatMutator.toNetwork()
    val score = data.map {
        network.evaluate(it.first, true)
        println("Expected  : ${it.second}")
        println("Actual RAW: ${network.output()}")
        println("Actual RND: ${network.output().map { it.roundToInt().toFloat() }}")
        val roundedActual = network.output().map { it.roundToInt().toFloat() }
        if (roundedActual == it.second) 1f else 0f
    }.sum()
    println(score)
}