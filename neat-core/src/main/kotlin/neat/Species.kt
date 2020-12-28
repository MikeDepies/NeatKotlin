package neat

import neat.model.NeatMutator
import kotlin.math.max

data class Species(val id: Int)


data class Offspring(val offspring: Int, val skim: Double)
class SpeciesReport(
    val speciesOffspringMap: Map<Species, Int>,
    val overallAverageFitness: Double,
    val speciesMap: SpeciesScoredMap
)

class OffspringCounter(val skim: Double, val y1: Float = 1f) {
    fun countOffspring(expectedOffsprings: Collection<Float>): Offspring {
        var offspring = 0
        var newSkim = skim
        expectedOffsprings.forEach { expectedOffSpring ->
            val nTemp = expectedOffSpring.div(y1).toInt()
            offspring += nTemp
            newSkim += expectedOffSpring - (nTemp * y1)
            if (newSkim >= 1f) {
                offspring += 1
                newSkim -= 1f
            }
        }
        return Offspring(offspring, newSkim)
    }
}

fun Collection<Float>.countOffspring(skim: Double, y1: Float = 1f): Offspring {
    var offspring = 0
    var newSkim = skim
    this.forEach { expectedOffSpring ->
        val nTemp = expectedOffSpring.div(y1).toInt()
        offspring += nTemp
        newSkim += expectedOffSpring - (nTemp * y1)
        if (newSkim >= 1f) {
            offspring += 1
            newSkim -= 1f
        }
    }
    return Offspring(offspring, newSkim)
}

typealias ExpectedOffSpring = Pair<NeatMutator, Float>

data class ModelScore(val neatMutator: NeatMutator, val fitness: Float, val adjustedFitness: Float)
typealias CompatibilityTest = (NeatMutator, NeatMutator) -> Boolean
typealias SpeciesScoredMap = Map<Species, Collection<ModelScore>>
typealias SpeciesMap = Map<Species, Collection<NeatMutator>>
typealias SpeciesFitnessFunction = (Species) -> Float

fun speciesAverageFitness(speciesScoredMap: SpeciesScoredMap): SpeciesFitnessFunction =
    { species -> speciesScoredMap.getValue(species).map { it.adjustedFitness }.average().toFloat() }

fun speciesTopFitness(speciesScoredMap: SpeciesScoredMap): SpeciesFitnessFunction =
    { species -> speciesScoredMap.getValue(species).first().adjustedFitness }

typealias OffspringReportFunction = SpeciationController.(List<ModelScore>) -> SpeciesReport

fun SpeciationController.calculateSpeciesReport(
    modelScoreList: List<ModelScore>,
    overallAverageFitness: Double
): SpeciesReport {

    val speciesOffspringMap = mutableMapOf<Species, Int>()
    val expectedOffspringMap =
        modelScoreList.map { it.neatMutator to it.adjustedFitness / (overallAverageFitness.toFloat()) }.toMap()
    var skim = 0.0
    var totalOffspring = 0
    for (species in speciesSet) {

        val speciesPopulation = getSpeciesPopulation(species)
        val map = speciesPopulation.map { expectedOffspringMap.getValue(it) }
        val countOffspring =
            map.countOffspring(skim)
        skim = countOffspring.skim
        totalOffspring += countOffspring.offspring
        speciesOffspringMap[species] = countOffspring.offspring
//        println("$species - ${neat.countOffspring.offspring}")
    }
    if (totalOffspring < modelScoreList.size) {
        val species = speciesSet.random()
        speciesOffspringMap[species] = speciesOffspringMap.getValue(species) + 1
    }

    return SpeciesReport(
        speciesOffspringMap.toMap(),
        overallAverageFitness,
        speciesMap(modelScoreList.toMap { it.neatMutator })
    )
}

private fun SpeciationController.speciesMap(modelScoreMap: Map<NeatMutator, ModelScore>): SpeciesScoredMap {
    fun speciesPopulation(species: Species): List<ModelScore> {
        return getSpeciesPopulation(species).map { neatMutator ->
            modelScoreMap.getValue(neatMutator)
        }
    }

    return speciesSet.map { species ->
        species to speciesPopulation(species)
    }.toMap()
}


class SpeciesOffspringCalculator(
    val expectedOffspringMap: Map<NeatMutator, Float>,
    val speciesOffspringMap: MutableMap<Species, Int>
) {
    fun getOffspring(neatMutator: NeatMutator) = expectedOffspringMap.getValue(neatMutator)
    fun getSpeciesOffspring(species: Species) = speciesOffspringMap.getValue(species)
}

fun SpeciesOffspringCalculator.totalOffspring(speciesSet: Set<Species>): Int {
    return totalOffspring(
        speciesSet,
        expectedOffspringMap,
        speciesOffspringMap
    )
}

fun totalOffspring(
    speciesSet: Set<Species>,
    expectedOffspringMap: Map<NeatMutator, Float>,
    speciesOffspringMap: MutableMap<Species, Int>
): Int {
    var skim = 0.0
    var totalOffspring = 0
    for (species in speciesSet) {
        val countOffspring = expectedOffspringMap.values.countOffspring(skim)
        skim = countOffspring.skim
        totalOffspring += countOffspring.offspring
        speciesOffspringMap[species] = countOffspring.offspring
    }
    return totalOffspring
}


fun populateNextGeneration(
    speciationController: SpeciationController,
    modelScoreList: List<ModelScore>,
    mutationEntries: List<MutationEntry>,
    simpleNeatExperiment: NeatExperiment,
    mateChance: Float,
    survivalThreshold: Float
): List<NeatMutator> {
    return speciationController.reproduce(
        simpleNeatExperiment,
        speciationController.speciesReport(modelScoreList),
        offspringFunction(mateChance, mutationEntries),
        survivalThreshold
    ).values.flatten()
}


fun SpeciationController.reproduce(
    neatExperiment: NeatExperiment,
    speciesReport: SpeciesReport,
    offspringFunction: OffspringFunction,
    survivalThreshold: Float
): SpeciesMap {
    return speciesSet.map { species ->
        val speciesPopulation = speciesReport.speciesMap.getValue(species).let { it.take(max(1, (it.size * survivalThreshold).toInt()) ) }
        val offspring = speciesReport.speciesOffspringMap.getValue(species)
        val newGenerationPopulation = (0 until offspring).map {
            offspringFunction(neatExperiment, speciesPopulation)
        }
        species to newGenerationPopulation
    }.toMap()
}

fun offspringFunction(chance: Float, mutationEntries: List<MutationEntry>): OffspringFunction {
    val probabilityToMate = rollFrom(chance)
    return { modelScoreList ->
        newOffspring(
            probabilityToMate,
            this,
            modelScoreList
        ).mutateModel(mutationEntries, this)
    }
}

fun SpeciationController.speciesReport(modelScoreList: List<ModelScore>): SpeciesReport {
    val overallAverageFitness = modelScoreList.map { modelScore -> modelScore.adjustedFitness }.average()
    return calculateSpeciesReport(modelScoreList, overallAverageFitness)

}


private fun newOffspring(
    probabilityToMate: MutationRoll,
    neatExperiment: NeatExperiment,
    speciesPopulation: Collection<ModelScore>
): NeatMutator {
    return when {
        probabilityToMate(neatExperiment) && speciesPopulation.size > 1 -> {
            val randomParent1 = speciesPopulation.random(neatExperiment.random)
            val randomParent2 = (speciesPopulation - randomParent1).random(neatExperiment.random)
//            neat.validateNeatModel(randomParent1.neat.model.neatMutator)
//            neat.validateNeatModel(randomParent2.neat.model.neatMutator)
            neatExperiment.crossover(
                FitnessModel(randomParent1.neatMutator, randomParent1.adjustedFitness),
                FitnessModel(randomParent2.neatMutator, randomParent2.adjustedFitness)
            )
//                .also {
//                    try {
//                        neat.validateNeatModel(it)
//                    } catch (e: Exception) {
//                        println("Parent 1 - ${randomParent1.adjustedFitness}: ${randomParent1.neat.model.neatMutator}")
//                        println("Parent 2 - ${randomParent2.adjustedFitness}: ${randomParent2.neat.model.neatMutator}")
//                        throw(e)
//                    }
//                }
        }
        else -> speciesPopulation.random(neatExperiment.random).neatMutator.clone()//.also { println("clone") }
    }
}

fun NeatMutator.mutateModel(mutationEntries: List<MutationEntry>, neatExperiment: NeatExperiment): NeatMutator {
    mutationEntries.forEach { mutationEntry ->
        mutationEntry.mutate(neatExperiment, this)
    }
    return this
}
typealias OffspringFunction = NeatExperiment.(Collection<ModelScore>) -> NeatMutator