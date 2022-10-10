package server

import PopulationEvolver
import io.ktor.util.date.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.novelty.KNNNoveltyArchive
import server.local.ModelEvaluationResult
import server.local.ModelStatus
import server.message.endpoints.toModel
import java.io.File
import java.lang.Exception
import java.lang.Float.max
import java.util.*

private val log = KotlinLogging.logger { }

class EvoManager(
    val populationSize: Int,
    val populationEvolver: PopulationEvolver,
    val adjustedFitness: AdjustedFitnessCalculation,
    val evaluationId: Int,
    val runFolder: File,
    val knnNoveltyArchive: KNNNoveltyArchiveWeighted
) {
    var evolutionInProgress = false
    var population: List<NetworkWithId> = listOf()
    val modelChannel = Channel<NetworkWithId>(40)
    var bestModels = mutableListOf<ScoredModel>()
    val scoreChannel = Channel<ModelEvaluationResult>(Channel.UNLIMITED)
    var mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
    var finishedScores = population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()
    var scores = mutableListOf<FitnessModel<NeatMutator>>()
    var seq = population.iterator()
    suspend fun start(
        initialPopulation: List<NeatMutator>,
    ) = withContext(Dispatchers.Default) {

        evolutionInProgress = false
        population = initialPopulation.mapIndexed { index, neatMutator ->
            NetworkWithId(neatMutator, UUID.randomUUID().toString())
        }.shuffled()
        mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
        finishedScores = population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()
        scores = mutableListOf<FitnessModel<NeatMutator>>()
        seq = population.iterator()
        launch(Dispatchers.Default) {
            for (it in scoreChannel) {
//                val objectiveScore = modelEvaluationResult.score.totalDamageDone + modelEvaluationResult.score.kills.size * 40
                log.info { "new score recieved" }
                val behaviorScore = max(
                    0f, scoreBehavior(
                        knnNoveltyArchive, it
                    ) + /*objectiveScore  +*/ if (it.score.playerDied) -60f else 0f
                )
                while (knnNoveltyArchive.behaviors.size > 1_000_000) {
                    knnNoveltyArchive.behaviors.removeAt(0)
                }
                val networkWithId = mapIndexed[it.modelId]
                val model = networkWithId?.neatMutator
                if (finishedScores[it.modelId] != true && model != null) {
                    scores += FitnessModel(model, behaviorScore)
                    finishedScores[it.modelId] = true
                    val species = if (populationEvolver.speciationController.hasSpeciesFor(model)) "${
                        populationEvolver.speciationController.species((model))
                    }" else "No Species"
                    log.info { "[G${populationEvolver.generation}][S${species} / ${populationEvolver.speciationController.speciesSet.size}] Model (${scores.size}) Score: $behaviorScore " }
                    log.info { "$it" }
                    captureBestModel(model, behaviorScore, populationEvolver, it)
                }
                if (scores.size == populationSize) {
                    processPopulation(populationEvolver)
                }
            }
        }
        launch {
            var lastRefill = getTimeMillis()
            while (true) {
                if (seq.hasNext()) {
                    log.info { "Put model into channel" }
                    modelChannel.send(seq.next())
                } else if (modelChannel.isEmpty && !seq.hasNext() && getTimeMillis() - lastRefill > 30_000) {
                    val networkWithIds = finishedScores.filter { !it.value }.mapNotNull { mapIndexed[it.key] }
                    log.info { "Refilling model channel ${networkWithIds.size}" }
                    seq = networkWithIds.iterator()
                    lastRefill = getTimeMillis()
                }
            }
        }
    }

    fun processPopulation(populationEvolver: PopulationEvolver) {


        log.info { "New generation ${populationEvolver.generation}" }
        val toModelScores = scores.toModelScores(adjustedFitness)
        population = evolve(
            populationEvolver, toModelScores, population.size
        ).mapIndexed { index, neatMutator ->
            NetworkWithId(neatMutator, UUID.randomUUID().toString())
        }.shuffled()
        mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
        finishedScores =
            population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()

        seq = population.iterator()

        scores = mutableListOf()
        writeGenerationToDisk(population.map { it.neatMutator }, runFolder, populationEvolver, "")
        val json = Json { prettyPrint = true }
        runFolder.resolve("${evaluationId}_noveltyArchive.json").bufferedWriter().use {
            val json = Json { prettyPrint = true }
            it.write(json.encodeToString(knnNoveltyArchive.behaviors))
            it.flush()
        }

    }


    fun evolve(
        populationEvolver: PopulationEvolver, modelScores: List<ModelScore>,

        populationSize: Int
    ): List<NeatMutator> {
        populationEvolver.sortPopulationByAdjustedScore(modelScores)
        populationEvolver.updateScores(modelScores)
        var newPopulation = populationEvolver.evolveNewPopulation(modelScores)
//    populationEvolver.speciationController.speciesSet.forEach { species ->
//        val speciesPopulation = populationEvolver.speciationController.getSpeciesPopulation(species)
//        populationEvolver.speciesLineage.updateMascot(species, speciesPopulation.first())
//    }

        while (newPopulation.size < populationSize) {
            newPopulation = newPopulation + newPopulation.random(populationEvolver.neatExperiment.random).clone()
        }
        populationEvolver.speciate(newPopulation)
        if (newPopulation.size > populationSize) {
            val dropList = newPopulation.drop(populationSize)
            val speciationController = populationEvolver.speciationController

            speciationController.speciesSet.forEach { species ->
                val speciesPopulation = speciationController.getSpeciesPopulation(species)
                speciesPopulation.filter { it in dropList }.forEach { neatMutator ->

                    speciesPopulation.remove(neatMutator)
                }
            }
        }
        return newPopulation.take(populationSize)
    }

    private fun captureBestModel(
        m: NeatMutator, behaviorScore: Float, populationEvolver: PopulationEvolver, it: ModelEvaluationResult
    ) {

        val average = bestModels.map { it.score }.average()
        bestModels += ScoredModel(behaviorScore, populationEvolver.generation, m, it.modelId)
        bestModels.sortByDescending {
            it.score - (populationEvolver.generation - it.generation) * (average / 2)
        }
        if (bestModels.size > 10) {
            bestModels.removeAt(10)
        }

    }

    private fun scoreBehavior(
        knnNoveltyArchive: KNNNoveltyArchiveWeighted, it: ModelEvaluationResult
    ) = when {
        knnNoveltyArchive.size < 1 -> {
            knnNoveltyArchive.addBehavior(it.score)
            it.score.allActions.size.toFloat()
        }

        else -> knnNoveltyArchive.addBehavior(it.score)
    }

}

fun writeGenerationToDisk(
    currentPopulation: List<NeatMutator>, runFolder: File, populationEvolver: PopulationEvolver, prefix: String
) {
    val modelPopulationPersist = currentPopulation.toModel()
    val savePopulationFile = runFolder.resolve("${prefix}population.json")
    val json = Json { prettyPrint = true }
    val encodedModel = json.encodeToString(modelPopulationPersist)
    savePopulationFile.bufferedWriter().use {
        it.write(encodedModel)
        it.flush()
    }
    val manifestFile = runFolder.resolve("manifest.json")
    val manifestData = Manifest(
        populationEvolver.scoreKeeper.toModel(), populationEvolver.speciesLineage.toModel()
    )
    manifestFile.bufferedWriter().use {
        it.write(json.encodeToString(manifestData))
        it.flush()
    }
}