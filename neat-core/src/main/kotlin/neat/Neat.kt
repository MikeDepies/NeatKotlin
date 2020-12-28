package neat

import neat.model.NeatMutator

typealias Generations = Int

typealias EnvironmentQuery = () -> EnvironmentEntryElement
typealias EnvironmentEntryElement = Pair<List<Float>, List<Float>>

data class FitnessModel<T>(val model: T, val score: Float)

fun <T> identity(): (T) -> T = { it }


//fun neat.SpeciationController.population() =
//    speciesSet.flatMap { getSpeciesPopulation(it) }

class Neat(
    private val generationRules: GenerationRules
) {
    val generationFinishedHandlers = mutableListOf<suspend (SpeciesMap, Int) -> Unit>()
    val modelScoresHandlers = mutableListOf<(List<ModelScore>, Int) -> Unit>()
    suspend fun process(
        times: Int,
        population: List<NeatMutator>,
        speciesScoreKeeper: SpeciesScoreKeeper,
        speciesLineage: SpeciesLineage,
        simpleNeatExperiment: NeatExperiment
    ) {
        val (speciationController, adjustedFitness, reproductionStrategy, populationEvaluator) = generationRules
        var currentPopulation = population
        speciationController.speciate(currentPopulation, speciesLineage, 0)
        repeat(times) { generation ->
            println("Generation $generation Population: ${currentPopulation.size} NumberOfSpecies: ${speciationController.speciesSet.size} TotalSpeciesCount: ${speciesLineage.species.lastOrNull()}")
            val modelScoreList =
                populationEvaluator(currentPopulation).toModelScores(adjustedFitness)
            sortModelsByAdjustedFitness(speciationController, modelScoreList)
            modelScoresHandlers.forEach { it(modelScoreList, generation) }
            speciesScoreKeeper.updateScores(modelScoreList.map { speciationController.species(it.neatMutator) to it })
            val newPopulation = reproductionStrategy(simpleNeatExperiment, speciationController, modelScoreList)
            val speciesMap = speciationController.speciate(newPopulation, speciesLineage, generation)
            println("speciesMapSize: ${speciesMap.values.flatten().size} newPopulationSize: ${newPopulation.size} controllerSize: ${speciationController.population().size}")
            generationFinishedHandlers.forEach { it(speciesMap, generation) }
            currentPopulation = newPopulation
        }
    }

    private fun sortModelsByAdjustedFitness(
        speciationController: SpeciationController,
        modelScoreList: List<ModelScore>
    ): List<ModelScore> {
        val adjustedPopulationScore = modelScoreList.toMap { modelScore -> modelScore.neatMutator }
        val fitnessForModel: (NeatMutator) -> Float = { neatMutator ->
            adjustedPopulationScore.getValue(neatMutator).adjustedFitness
        }
        speciationController.sortSpeciesByFitness(fitnessForModel)
        return modelScoreList
    }

}

fun validatePopulation(currentPopulation: List<NeatMutator>) {
    currentPopulation.forEach { neatMutator ->
        validateNeatModel(neatMutator)
    }
}

fun validateNeatModel(neatMutator: NeatMutator) {
    neatMutator.connections.forEach { connectionGene ->
        if (neatMutator.nodes.none { connectionGene.inNode == it.node }
            || neatMutator.nodes.none { connectionGene.outNode == it.node }) {
            error("Couldn't satisfy $connectionGene from node pool ${neatMutator.nodes}")
        }
    }
}

fun List<FitnessModel<NeatMutator>>.toModelScores(adjustedFitness: AdjustedFitnessCalculation): List<ModelScore> {
    println("To Model scores neat.size: ${this.size}")
    return map { fitnessModel ->
        ModelScore(fitnessModel.model, fitnessModel.score, adjustedFitness(fitnessModel))
    }
}

