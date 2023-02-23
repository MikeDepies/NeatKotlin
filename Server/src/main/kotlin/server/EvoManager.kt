package server

import PopulationEvolver
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
import server.message.endpoints.toModel
import java.io.File

import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.sqrt

private val log = KotlinLogging.logger { }

class EvoManager(
    val populationSize: Int,
    val populationEvolver: PopulationEvolver,
    val adjustedFitness: AdjustedFitnessCalculation,
    val evaluationId: Int,
    val runFolder: File,
    val knnNoveltyArchive: KNNNoveltyArchive<ActionBehaviorInt>
) {
    var evolutionInProgress = false
    var population: List<NeatMutator> = listOf()
    val modelChannel = Channel<NeatMutator>(Channel.UNLIMITED)
    var bestModels = mutableListOf<ScoredModel>()
    val scoreChannel = Channel<ModelEvaluationResult>(Channel.UNLIMITED)
    var mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
    var finishedScores = population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()
    var scores = mutableListOf<FitnessModel<NeatMutator>>()
    var populationScoresChannel = Channel<PopulationScoreEntry>(Channel.UNLIMITED)

    //    var seq = population.iterator()
    suspend fun start(
        initialPopulation: List<NeatMutator>,
    ) = withContext(Dispatchers.Default) {

        evolutionInProgress = false
        population = initialPopulation.shuffled()
        mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
        finishedScores = population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()
        scores = mutableListOf<FitnessModel<NeatMutator>>()
        population.forEach {
            modelChannel.send(it)
        }

        launch(Dispatchers.Default) {
            for (it in scoreChannel) {
//                val objectiveScore = it.score.totalDamageDone / 1000 + it.score.kills.size * .5
//                log.info { "new score recieved" }

                val uuid = UUID.fromString(it.modelId)
                val networkWithId = mapIndexed[uuid]
                val model = networkWithId
                if (finishedScores[uuid] != true && model != null) {
                    val scoredBehavior = scoreBehavior(
                        knnNoveltyArchive, it
                    )
                    val behaviorScore = max(0f, scoredBehavior /*+ (it.score.totalFrames + it.score.totalFramesHitstunOpponent + it.score.movement) / 60*/  /*+ (it.score.totalFrames/10) + (it.score.totalDamageDone / 10f + it.score.kills.size * 200f)*/ /*- if (it.score.playerDied) 100 else 0*/) // + (it.score.totalFrames / 60) + (it.score.totalDistanceTowardOpponent / 20) + (it.score.kills.size * 20f) + it.score.totalDamageDone / 10f
                    while (knnNoveltyArchive.behaviors.size > 200_000) {
                        knnNoveltyArchive.behaviors.removeAt(0)
                    }
                    log.info { "$it" }
                    scores += FitnessModel(model, behaviorScore)
                    finishedScores[uuid] = true
                    val species = if (populationEvolver.speciationController.hasSpeciesFor(model)) "${
                        populationEvolver.speciationController.species((model))
                    }" else "No Species"
                    log.info { "${character(evaluationId)} - [G${populationEvolver.generation}][S${species} / ${populationEvolver.speciationController.speciesSet.size}] Model (${scores.size}) Score: $behaviorScore " }

                    captureBestModel(model, behaviorScore, populationEvolver, it)
                }
                if (scores.size == populationSize) {
                    evolutionInProgress = true
//                    scores.map {
//                        FullModel(it.model.toModel(), it.)
//                    }
                    populationScoresChannel.send(PopulationScoreEntry(scores.map {
                        FullModel(
                            it.model.toModel(),
                            it.model.id.toString(),
                            populationEvolver.speciationController.species(it.model).id,
                            it.score,
                            populationEvolver.generation
                        )
                    }, populationEvolver.generation))
                    processPopulation(populationEvolver)
                    log.info { "Finished Evolution" }
                    population.forEach {
                        modelChannel.send(it)
                    }
                    evolutionInProgress = false
                }
            }
        }
    }

    fun processPopulation(populationEvolver: PopulationEvolver) {


        log.info { "New generation ${populationEvolver.generation}" }
        val toModelScores = scores.toModelScores(adjustedFitness)
        population = evolve(
            populationEvolver, toModelScores, population.size
        ).shuffled()
        log.info { "mapping to ids" }
        mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
        finishedScores =
            population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()
        log.info { "setting new population" }
//        seq = population.iterator()


        scores = mutableListOf()
        writeGenerationToDiskMCC(population.map { it }, runFolder, populationEvolver, "${evaluationId}_")
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
        populationEvolver.speciationController.speciesSet.forEach { species ->
            val speciesPopulation = populationEvolver.speciationController.getSpeciesPopulation(species)
            populationEvolver.speciesLineage.updateMascot(
                species,
                speciesPopulation.take(max(1, (speciesPopulation.size * .2).toInt())).random()
            )
        }

        while (newPopulation.size < populationSize) {
            newPopulation =
                newPopulation + newPopulation.random(populationEvolver.neatExperiment.random).clone(UUID.randomUUID())
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
        m: NeatMutator,
        behaviorScore: Float,
        populationEvolver: PopulationEvolver,
        modelEvaluationResult: ModelEvaluationResult
    ) {
        var tempBestModels: MutableList<ScoredModel> = ArrayList(bestModels)
        val average = bestModels.map { it.score }.average()
        tempBestModels += ScoredModel(
            behaviorScore,
            populationEvolver.generation,
            m,
            modelEvaluationResult.modelId,
            populationEvolver.speciationController.species(m).id
        )
        tempBestModels = tempBestModels.distinctBy { it.id }.toMutableList()
        tempBestModels.sortByDescending {
            it.score - (populationEvolver.generation - it.generation) * (average / 10)
        }
        if (tempBestModels.size > 10) {
            tempBestModels.removeAt(10)
        }
        bestModels = tempBestModels

    }

    val sequenceSeparator = 2000.toChar()
    fun stringifyActionBehavior(it: ActionBehavior): ActionStringedBehavior {
        return ActionStringedBehavior(
            it.allActions.actionString(),
            it.recovery.joinToString("$sequenceSeparator") { it.actionString() },
            it.kills.actionString(),
            it.damage.actionString(),
            it.totalDamageDone,
            it.totalDistanceTowardOpponent,
            it.playerDied
        )
    }

    fun intifyActionBehavior(it: ActionBehavior): ActionBehaviorInt {
        return ActionBehaviorInt(
            it.allActions,
            it.recovery.flatten(),
            it.kills,
            it.damage,
            it.totalDamageDone,
            it.totalDistanceTowardOpponent,
            it.playerDied,
            it.totalFramesHitstunOpponent,
            it.totalFrames.toFloat(),
            it.movement
        )
    }

    private fun scoreBehavior(
        knnNoveltyArchive: KNNNoveltyArchive<ActionBehaviorInt>, it: ModelEvaluationResult
    ) = when {
        knnNoveltyArchive.size < 1 -> {
            knnNoveltyArchive.addBehavior(intifyActionBehavior(it.score))
            it.score.allActions.size.toFloat()
        }

        else -> sqrt(knnNoveltyArchive.addBehavior(intifyActionBehavior(it.score)))
    }

    private fun scoreAllBehavior(
        knnNoveltyArchive: KNNNoveltyArchive<ActionSumBehavior>, it: ModelEvaluationResult
    ): Float {

        val behavior = it.score.let {
            ActionSumBehavior(
                it.allActions.size,
                it.recovery.size,
                it.kills,
                it.totalDamageDone,
                it.totalDistanceTowardOpponent,
                it.playerDied
            )
        }
        return when {
            knnNoveltyArchive.size < 1 -> {
                knnNoveltyArchive.addBehavior(behavior)
                behavior.allActionsCount.toFloat()
            }

            else -> knnNoveltyArchive.addBehavior(behavior)
        }
    }

}

fun writeGenerationToDiskMCC(
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
    val manifestFile = runFolder.resolve("${prefix}manifest.json")
    val manifestData = Manifest(
        populationEvolver.generation,
        populationEvolver.scoreKeeper.toModel(), populationEvolver.speciesLineage.toModel()
    )
    manifestFile.bufferedWriter().use {
        it.write(json.encodeToString(manifestData))
        it.flush()
    }
}


fun writeGenerationToDiskMCC(
    currentPopulation: List<NeatMutator>, runFolder: File, batchNumber: Int, prefix: String
) {
    val modelPopulationPersist = currentPopulation.toModel()
    val savePopulationFile = runFolder.resolve("${prefix}population.json")
    val json = Json { prettyPrint = true }
    val encodedModel = json.encodeToString(modelPopulationPersist)
    savePopulationFile.bufferedWriter().use {
        it.write(encodedModel)
        it.flush()
    }

}