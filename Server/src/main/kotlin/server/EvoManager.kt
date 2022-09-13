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
    var modelStatusMap: Map<String, ModelStatus> = mapOf()
    var modelMap: MutableMap<String, NetworkBlueprint?> = mutableMapOf()
    var population: List<NetworkWithId> = listOf()
    val windower: ModelWindower = ModelWindower(1)
    var bestModels = mutableListOf<ScoredModel>()
    val scoreChannel = Channel<ModelEvaluationResult>(Channel.UNLIMITED)
    suspend fun start(
        initialPopulation: List<NeatMutator>,
    ) = withContext(Dispatchers.Default) {

        evolutionInProgress = false
        population = initialPopulation.mapIndexed { index, neatMutator ->
            NetworkWithId(neatMutator, UUID.randomUUID().toString())
        }.shuffled()

        modelStatusMap = population.map {
            it.id to ModelStatus(false, null, null)
        }.toMap()

        modelMap = population.map {
            it.id to null
        }.toMap().toMutableMap()

        launch(Dispatchers.Default) {
            createTaskNetworks()
        }
        windower.fill(population)
        launch(Dispatchers.Default) {
            for (modelEvaluationResult in scoreChannel) {
                val m = modelMap.get(modelEvaluationResult.modelId) ?: continue
                val modelStatus = modelStatusMap[modelEvaluationResult.modelId] ?: continue
                if (modelStatus.score != null) {
                    continue
                }

                val objectiveScore = modelEvaluationResult.score.totalDamageDone + modelEvaluationResult.score.kills.size * 40
//                log.info { "new score recieved" }
                val behaviorScore = max(0f, scoreBehavior(
                    knnNoveltyArchive,
                    modelEvaluationResult
                ) + /*objectiveScore  +*/ if (modelEvaluationResult.score.playerDied) -60f else 0f)
                while (knnNoveltyArchive.behaviors.size > 1_000_000) {
                    knnNoveltyArchive.behaviors.removeAt(0)
                }
                captureBestModel(m, behaviorScore, populationEvolver, modelEvaluationResult)
                updateModelStatusScore(
                    modelStatus, behaviorScore, populationEvolver, modelStatusMap, modelEvaluationResult
                )
                //Check if all models have been contributed
                val allScored = modelStatusMap.all { (id, status) -> status.score != null }
                if (allScored) {
                    log.info { "[$evaluationId] Scores: ${bestModels.map { "${it.generation} - ${it.id} - ${it.score}" }}" }
                    evolutionInProgress = true
                    val modelScores = modelStatusMap.mapNotNull { (id, status) ->
                        if (status.score != null) {
                            FitnessModel(status.neatMutator!!, status.score!!)
                        } else null
                    }.toModelScores(adjustedFitness)
                    populationEvolver.sortPopulationByAdjustedScore(modelScores)
                    populationEvolver.updateScores(modelScores)

                    var newPopulation = evolveNewPopulation(populationEvolver, modelScores, populationSize)
                    log.info { "population finished evolving..." }
                    population = newPopulation.take(populationSize).map { NetworkWithId(it, "${UUID.randomUUID()}") }
                    modelStatusMap = population.map {
                        it.id to ModelStatus(false, null, null)
                    }.toMap()
                    modelMap = population.map {
                        it.id to null
                    }.toMap().toMutableMap()
                    windower.fill(population)
                    writeData(population, populationEvolver)


                    launch(Dispatchers.Default) {
                        createTaskNetworks()
                        evolutionInProgress = false
                    }

                }
            }
        }
    }

    private fun evolveNewPopulation(
        populationEvolver: PopulationEvolver, modelScores: List<ModelScore>, populationSize: Int
    ): List<NeatMutator> {
        var newPopulation = safeEvolvePopulation(populationEvolver, modelScores)
        populationEvolver.speciationController.speciesSet.forEach { species ->
            val mascot = populationEvolver.speciationController.getSpeciesPopulation(species).random()
            populationEvolver.speciesLineage.updateMascot(species, mascot)
        }
        log.info { "[$evaluationId]  Species Count: ${populationEvolver.speciesLineage.species.size}" }
        while (newPopulation.size < populationSize) {
            newPopulation = newPopulation + newPopulation.random().clone()
        }
        populationEvolver.speciate(newPopulation)
        if (newPopulation.size > populationSize) {
            val diff = newPopulation.size - populationSize
            val dropList = newPopulation.drop(diff)
            val speciationController = populationEvolver.speciationController
            log.info {
                "Dropping ${dropList.size} models from ${
                    dropList.map { speciationController.species(it) }.distinct()
                }"
            }
            speciationController.speciesSet.forEach { species ->
                val speciesPopulation = speciationController.getSpeciesPopulation(species)
                speciesPopulation.filter { it in dropList }.forEach { neatMutator ->
                    //                    logger.info { "Removing model from $species since it has been dropped." }
                    speciesPopulation.remove(neatMutator)
                }

            }
        }
        return newPopulation
    }

    private fun updateModelStatusScore(
        modelStatus: ModelStatus,
        behaviorScore: Float,
        populationEvolver: PopulationEvolver,
        modelStatusMap: Map<String, ModelStatus>,
        it: ModelEvaluationResult
    ) {
        if (modelStatus != null && modelStatus.score == null) {
            modelStatus.score = behaviorScore
            val neatMutator = modelStatus.neatMutator
            if (neatMutator != null) {
                val species = populationEvolver.speciationController.species(neatMutator)
                log.info {
                    "[$evaluationId]  [$species | ${
                        modelStatusMap.count { (id, status) ->
                            status.score != null
                        }
                    } | ${populationEvolver.generation}] Score: $behaviorScore Recoveries: ${it.score.recovery}"
                }
            }
        }
    }

    private fun writeData(
        population: List<NetworkWithId>,
        populationEvolver: PopulationEvolver,

        ) {
        writeGenerationToDisk(
            population.map { it.neatMutator }, runFolder, populationEvolver, "${evaluationId}_"
        )

        runFolder.resolve("${evaluationId}_noveltyArchive.json").bufferedWriter().use {
            val json = Json { prettyPrint = true }
            it.write(
                json.encodeToString(
                    knnNoveltyArchive.behaviors
                )
            )
            it.flush()
        }
    }

    private fun captureBestModel(
        m: NetworkBlueprint, behaviorScore: Float, populationEvolver: PopulationEvolver, it: ModelEvaluationResult
    ) {
        if (m != null) {
            val average = bestModels.map { it.score }.average()
            val modelStatus = modelStatusMap.getValue(it.modelId)
            bestModels += ScoredModel(behaviorScore, populationEvolver.generation, m, it.modelId)
            bestModels.sortByDescending {
                it.score - (populationEvolver.generation - it.generation) * (average / 2)
            }
            if (bestModels.size > 10) {
                bestModels.removeAt(10)
            }
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

    private suspend fun createTaskNetworks(
    ) {
        val createNetwork = createNetwork()
        val targetConnectionMapping =
            createNetwork.targetConnectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
        val calculationOrder = createNetwork.calculationOrder.map { it.id }
        val connectionRelationships = createNetwork.connectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
        population.mapParallel {
            try {

                it to NetworkBlueprint(createNetwork.build(it.neatMutator.toNetwork(), 3f), it.id, createNetwork.planes,
                    connectionRelationships,
                    targetConnectionMapping,
                    calculationOrder,
                    it.neatMutator.toModel()
                )
            } catch (e: Exception) {
                it to NetworkBlueprint(
                    listOf(), it.id, createNetwork.planes,
                    connectionRelationships,
                    targetConnectionMapping,
                    calculationOrder,
                    it.neatMutator.toModel()
                )
            }
        }.forEach { (networkWithId, network) ->
            val id = networkWithId.id
            val modelStatus = modelStatusMap[id]
            if (modelStatus != null) {
//                log.info { "Creating task network $id" }
                modelMap[id] = network
                modelStatus.available = true
                modelStatus.neatMutator = networkWithId.neatMutator
            } else {
                log.error { "Failed to put $id in model map..." }
            }
        }
        println("finished new population")
    }

    private fun safeEvolvePopulation(
        populationEvolver: PopulationEvolver, modelScores: List<ModelScore>
    ): List<NeatMutator> {
        return populationEvolver.evolveNewPopulation(modelScores)

    }
}
fun writeGenerationToDisk(
    currentPopulation: List<NeatMutator>,
    runFolder: File,
    populationEvolver: PopulationEvolver,
    prefix: String
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
        populationEvolver.scoreKeeper.toModel(),
        populationEvolver.speciesLineage.toModel()
    )
    manifestFile.bufferedWriter().use {
        it.write(json.encodeToString(manifestData))
        it.flush()
    }
}