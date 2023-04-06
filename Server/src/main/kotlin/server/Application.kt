package server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.novelty.KNNNoveltyArchive
import neat.novelty.levenshtein
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import server.local.*
import server.message.endpoints.*
import server.service.TwitchBotService
import server.service.TwitchModel
import server.util.levenshteinInt
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.sqrt
import kotlin.random.Random


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private val logger = KotlinLogging.logger { }

data class ScoredModel(val score: Float, val generation: Int, val model: NeatMutator, val id: String, val species: Int)

@Serializable
data class ScoredModelSerializable(val score: Float, val generation: Int, val species: Int, val id: String)


@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
        allowSameOrigin = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
//    install(CallLogging) {
//        level = Level.INFO
//    }
    val application = this
    install(Koin) {
        modules(applicationModule, org.koin.dsl.module {
            single { application }
            single {
                Json {
                    encodeDefaults = true
                }
            }
        })
    }
    val jsonService by inject<Json>()
    install(ContentNegotiation) {
        json(jsonService)
    }

    val evaluationId = 0
    val evaluationId2 = 1
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = LocalDateTime.now().let { File("runs/run-${it.format(format)}") }
    runFolder.mkdirs()
    val sequenceSeparator: Char = 2000.toChar()
//    val a = actionBehaviors("population/0_noveltyArchive.json").takeLast(100_000)
    /*.map {
        ActionStringedBehavior(
            it.allActions.actionString(),
            it.recovery.joinToString("$sequenceSeparator") { it.actionString() },
            it.kills.actionString(),
            it.damage.actionString(),
            it.totalDamageDone,
            it.totalDistanceTowardOpponent,
            it.playerDied
        ) }*/
//    val b = actionBehaviors("population/1_noveltyArchive.json")

    fun simulationForController(controllerId: Int, populationSize: Int, load: Boolean): Simulation =
        simulationFor(controllerId, populationSize, load)

    val populationSize = 500
    val knnNoveltyArchive = knnNoveltyArchive(
        10,
        behaviorMeasureInt(
            damageMultiplier = 2f,
            actionMultiplier = 1f,
            killMultiplier = 300f,
            recoveryMultiplier = 12f
        )
    )
    val knnNoveltyArchive2 = knnNoveltyArchive(
        10,
        behaviorMeasureInt(
            damageMultiplier = 2f,
            actionMultiplier = 1f,
            killMultiplier = 100f,
            recoveryMultiplier = 8f
        )
    )
//    knnNoveltyArchive.behaviors.addAll(actionBehaviors("population/0_noveltyArchive.json"))
//    knnNoveltyArchive2.behaviors.addAll(b)
    val (initialPopulation, populationEvolver, adjustedFitness) = simulationForController(
        controllerId = 0,
        populationSize = populationSize,
        load = false
    )
    val evoManager =
        EvoManager(populationSize, populationEvolver, adjustedFitness, evaluationId, runFolder, knnNoveltyArchive)
    logger.info { initialPopulation.distinctBy { it.id }.size }
    val (initialPopulation2, populationEvolver2, adjustedFitness2) = simulationForController(
        1,
        populationSize,
        false
    )
    val evoManager2 =
        EvoManager(populationSize, populationEvolver2, adjustedFitness2, evaluationId2, runFolder, knnNoveltyArchive2)
    launch { evoManager.start(initialPopulation) }
    launch { evoManager2.start(initialPopulation2) }
    val dashboardManager = DashboardManager(
        evaluationId, StreamStats(
            0, 0, 0, 0
        )
    )
    val dashboardManager2 = DashboardManager(
        evaluationId2, StreamStats(
            0, 0, 0, 0
        )
    )
    dashboardLoop(dashboardManager, evoManager.populationScoresChannel)
    dashboardLoop(dashboardManager2, evoManager2.populationScoresChannel)
    routing(
        EvoControllerHandler(
            mapOf(
                evaluationId to evoManager,
                evaluationId2 to evoManager2
            ), mapOf(
                evaluationId to dashboardManager,
                evaluationId2 to dashboardManager2
            )
        )
    )
}

fun Application.dashboardLoop(
    dashboardManager: DashboardManager,
    scoreEntryChannel: Channel<PopulationScoreEntry>
) {
    launch {
        for (scoreList in scoreEntryChannel) {
            dashboardManager.scores.add(scoreList)
            if (dashboardManager.scores.size > 100) {
                dashboardManager.scores.removeAt(0)
            }
            dashboardManager.scoreMap =
                dashboardManager.scores.flatMap { it.scoreList }.associateBy { it.id }
        }
    }
}

private fun actionBehaviors(noveltyArchiveJson: String) = Json { }.decodeFromString(
    ListSerializer(ActionBehaviorInt.serializer()),
    File(noveltyArchiveJson).bufferedReader().lineSequence().joinToString("")
)

class EvoControllerHandler(val map: Map<Int, EvoManager>, val dashboardManagerMap: Map<Int, DashboardManager>) {
    fun evoManager(controllerId: Int): EvoManager {
        return map.getValue(controllerId)
    }

    fun dashboardManager(controllerId: Int): DashboardManager {
        return dashboardManagerMap.getValue(controllerId)
    }
}

fun character(controllerId: Int) = when (controllerId) {
    0 -> Character.DoctorMario
    1 -> Character.Falco
    else -> throw Exception()
}

private fun Application.routing(
    evoHandler: EvoControllerHandler,
) {
    val evaluatorSettings = EvaluatorSettings(15, 120, 12)
    val pythonConfiguration = PythonConfiguration(
        evaluatorSettings,
        ControllerConfiguration(Character.DoctorMario, 0),
        ControllerConfiguration(Character.Fox, 4),
        MeleeStage.FinalDestination
    )
    val twitchBotService by inject<TwitchBotService>()
    var lastModel1: TwitchModel? = null
    var lastModel2: TwitchModel? = null
    fun modelFor(controllerId: Int) = when (controllerId) {
        0 -> lastModel1
        1 -> lastModel2
        else -> throw Exception()
    }


    routing {
        val createNetwork = createNetwork()
        val connectionRelationships =
            createNetwork.connectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
        val targetConnectionMapping =
            createNetwork.targetConnectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
        val calculationOrder = createNetwork.calculationOrder.map { it.id }
        suspend fun createBlueprint(evoManager: EvoManager): NetworkBlueprint {
            val orNull = if (evoManager.evolutionInProgress) null else evoManager.modelChannel.tryReceive().getOrNull()
//            if (orNull == null)
//                logger.info { "modelChannel is null? ${evoManager.modelChannel.isEmpty}" }
            val networkWithId = orNull ?: ArrayList(evoManager.bestModels).random().let { it.model }
            val neatMutator = networkWithId
            val networkBlueprint = NetworkBlueprint(
                networkWithId.id.toString(),
                createNetwork.planes,
                connectionRelationships,
                targetConnectionMapping,
                calculationOrder,
                0,
                neatMutator.hiddenNodes.size,
                createNetwork.outputPlane.map { it.id},
                createNetwork.inputPlane.map { it.id },
                neatMutator.toModel(),
                createNetwork.depth,
                orNull == null
            )
            return networkBlueprint
        }

        route("/model") {
            post<ModelsRequest>("/generation") {
                val evoManager = evoHandler.evoManager(it.controllerId)

                call.respond(evoManager.populationEvolver.generation)
            }
            post<ModelsRequest>("/best") {
                val evoManager = evoHandler.evoManager(it.controllerId)
                val model = ArrayList(evoManager.bestModels).random().model


//                val neatModel = evoManager.modelStatusMap.getValue(model.id)
//                val twitchModel = TwitchModel(
//                    model.id,
//                    neatModel.neatMutator!!.toModel(),
//                    character(it.controllerId),
//                    neatModel.score ?: 0f
//                )
//                val modelFor = modelFor(it.controllerId)
//                if (modelFor != null)
//                    twitchBotService.sendModel(modelFor)
//                if (it.controllerId == 0) {
//                    lastModel1 = twitchModel
//                } else {
//                    lastModel2 = twitchModel
//                }
                val networkBlueprint = NetworkBlueprint(
                    model.id.toString(),
                    createNetwork.planes,
                    connectionRelationships,
                    targetConnectionMapping,
                    calculationOrder,
                    0,
                    model.hiddenNodes.size,
                    createNetwork.outputPlane.map { it.id},
                    createNetwork.inputPlane.map { it.id },
                    model.toModel(),
                    createNetwork.depth,
                    true
                )
                call.respond(networkBlueprint)
            }



            post<ModelsRequest>("/next") {
                val evoManager = evoHandler.evoManager(it.controllerId)
                val networkBlueprint = createBlueprint(evoManager)

//                logger.info { "Created blueprint ${networkBlueprint.id}" }
                call.respond(networkBlueprint)
            }



            post<ModelEvaluationResult>("/score") {
//                logger.info { "GOT SCORE $it" }
                val evoManager = evoHandler.evoManager(it.controllerId)
                evoManager.scoreChannel.send(it)
                call.respond("success")
            }

            post<ModelsRequest>("/refill") { request ->
                val evoManager = evoHandler.evoManager(request.controllerId)
                val networkWithIdList =
                    evoManager.finishedScores.filter { !it.value }.mapNotNull { evoManager.mapIndexed[it.key] }
                launch {
                    networkWithIdList.forEach {
                        evoManager.modelChannel.send(it)
                    }
                }
                call.respond("success (${networkWithIdList.size}")
            }


        }
        get("/configuration") {
            call.respond(pythonConfiguration)
        }

        route("/dashboard") {
            route("/stream") {
                post<ModelsRequest>("/addWin") {
                    val dashboardManager = evoHandler.dashboardManager(it.controllerId)
                    dashboardManager.wins += 1
                }
                post<ModelsRequest>("/addLoss") {
                    val dashboardManager = evoHandler.dashboardManager(it.controllerId)
                    dashboardManager.losses += 1
                }
                post<ModelsRequest>("/addKill") {
                    val dashboardManager = evoHandler.dashboardManager(it.controllerId)
                    dashboardManager.kills += 1
                }
                post<ModelsRequest>("/addDeath") {
                    val dashboardManager = evoHandler.dashboardManager(it.controllerId)
                    dashboardManager.deaths += 1
                }
                post<ModelRequest>("/updateModelId") {
                    val dashboardManager = evoHandler.dashboardManager(it.controllerId)
                    dashboardManager.modelId = it.modelId
                }
                post<ModelsRequest>("/stats") {
                    val dashboardManager = evoHandler.dashboardManager(it.controllerId)
                    call.respond(dashboardManager.statsFrame())
                }
                post<ModelsRequest>("/activeModel") {
                    val dashboardManager = evoHandler.dashboardManager(it.controllerId)
                    call.respond(ActiveModelId(dashboardManager.modelId))
                }
                post<ModelsRequest>("/generationStatus") {
                    val evoManager = evoHandler.evoManager(it.controllerId)
                    val amountComplete =
                        evoManager.finishedScores.filter { !it.value }.mapNotNull { evoManager.mapIndexed[it.key] }
                    call.respond(GenerationStatus(amountComplete.size))
                }
                post<ModelsRequest>("/populationSize") {
                    val evoManager = evoHandler.evoManager(it.controllerId)
                    call.respond(PopulationSize(evoManager.populationSize))
                }
            }
            post<ModelsRequest>("/bestList") { modelsRequest ->
                val evoManager = evoHandler.evoManager(modelsRequest.controllerId)
                val bestModels = evoManager.bestModels.map { scoredModel ->
                    ScoredModelSerializable(
                        scoredModel.score, scoredModel.generation, scoredModel.species, scoredModel.id
                    )
                }
                call.respond(bestModels)
            }
            post<ModelRequest>("/model") { modelsRequest ->
                val evoManager = evoHandler.evoManager(modelsRequest.controllerId)
                val dashboard = evoHandler.dashboardManager(modelsRequest.controllerId)
                var scoredModel = evoManager.bestModels.find { it.id == modelsRequest.modelId }?.let {
                    FullModel(
                        it.model.toModel(),
                        modelsRequest.modelId,
                        it.species,
                        it.score,
                        it.generation
                    )
                }
                if (scoredModel == null) {
                    scoredModel = evoManager.scores.find { it.model.id.toString() == modelsRequest.modelId }?.let {
                        FullModel(
                            it.model.toModel(),
                            modelsRequest.modelId,
                            evoManager.populationEvolver.speciationController.species(it.model).id,
                            it.score,
                            evoManager.populationEvolver.generation
                        )
                    }
//                    evoManager.population.find { it.id.toString() == modelsRequest.modelId }
                }
                if (scoredModel == null) {
                    scoredModel = dashboard.scoreMap.get(modelsRequest.modelId)
                }
                if (scoredModel != null) {
                    call.respond(
                        scoredModel
                    )
                } else {
                    call.respond(NoData)
                }
            }

            post<ModelsRequestFromGeneration>("/populationSpecies") { modelsRequest ->
                val evoManager = evoHandler.evoManager(modelsRequest.controllerId)
                val dashboard = evoHandler.dashboardManager(modelsRequest.controllerId)
                val species =
                    evoManager.population.map { evoManager.populationEvolver.speciationController.species(it) }
                        .groupBy { it.id }.mapValues { it.value.size }

                val speciesForPopulationList =
                    mutableListOf(SpeciesForPopulation(species, evoManager.populationEvolver.generation))
                if (modelsRequest.generation < evoManager.populationEvolver.generation) {
                    val historicalSpeciesPopulations =
                        dashboard.scores.filter { it.generation >= modelsRequest.generation }.map {
                            SpeciesForPopulation(
                                it.scoreList.groupBy(keySelector = { it.species }).mapValues { it.value.size },
                                it.generation
                            )
                        }
                    speciesForPopulationList.addAll(historicalSpeciesPopulations)
                }
                call.respond(speciesForPopulationList)
            }

        }
    }
}

@Serializable
data class PopulationSize(val populationSize: Int)

@Serializable
data class GenerationStatus(val amountComplete: Int) {

}

sealed class StatOperation {
    object AddWin : StatOperation()
    object AddLoss : StatOperation()
    object AddKill : StatOperation()
    object AddDeath : StatOperation()
}

@Serializable
data class SpeciesForPopulation(val map: Map<Int, Int>, val generation: Int)

data class PopulationScoreEntry(val scoreList: List<FullModel>, val generation: Int)

@Serializable
data class StreamStats(val wins: Int, val losses: Int, val kills: Int, val deaths: Int)
class DashboardManager(val controllerId: Int, stats: StreamStats = StreamStats(0, 0, 0, 0)) {
    var wins: Int = stats.wins
    var losses: Int = stats.losses
    var kills: Int = stats.kills
    var deaths: Int = stats.deaths
    var modelId: String = ""
    val scores = mutableListOf<PopulationScoreEntry>()
    var scoreMap = mapOf<String, FullModel>()
    fun statsFrame() = StreamStats(wins, losses, kills, deaths)
}

@Serializable
data class ActiveModelId(val id: String)

@Serializable
object NoData

@Serializable
data class FullModel(val neatModel: NeatModel, val id: String, val species: Int, val score: Float, val generation: Int)


suspend fun <T, R> Iterable<T>.mapParallel(transform: (T) -> R): List<R> = coroutineScope {
    map { async { transform(it) } }.map { it.await() }
}


private fun behaviorMeasure(
    sequenceSeparator: Char = 2000.toChar(),
    actionMultiplier: Float = 1f,
    killMultiplier: Float = 50f,
    damageMultiplier: Float = 2f,
    recoveryMultiplier: Float = 5f
) = { a: ActionBehavior, b: ActionBehavior ->
    val allActionDistance = levenshtein(a.allActions.actionString(), b.allActions.actionString())
    val damageDistance = levenshtein(a.damage.actionString(), b.damage.actionString())
    val killsDistance = levenshtein(a.kills.actionString(), b.kills.actionString())
    val lhs = a.recovery.joinToString("$sequenceSeparator") { it.actionString() }
    val rhs = b.recovery.joinToString("$sequenceSeparator") { it.actionString() }
//    val minLen = lhs.length
    val recoveryDistance = levenshtein(
        lhs, rhs
    )
    sqrt(
        allActionDistance.times(actionMultiplier).squared() + killsDistance.times(killMultiplier)
            .squared() + damageDistance.times(
            damageMultiplier
        ).squared() + recoveryDistance.times(recoveryMultiplier).squared()
            .toFloat() + (a.totalDistanceTowardOpponent - b.totalDistanceTowardOpponent).div(20).squared()
    )
}

//
//private fun behaviorMeasurePreStringed(
//    sequenceSeparator: Char = 2000.toChar(),
//    actionMultiplier: Float = 1f,
//    killMultiplier: Float = 50f,
//    damageMultiplier: Float = 2f,
//    recoveryMultiplier: Float = 5f
//) = { a: ActionStringedBehavior, b: ActionStringedBehavior ->
//    val allActionDistance = levenshtein(a.allActions, b.allActions)
//    val damageDistance = levenshtein(a.damage, b.damage)
//    val killsDistance = levenshtein(a.kills, b.kills)
//    val lhs = a.recovery
//    val rhs = b.recovery
////    val minLen = lhs.length
//    val recoveryDistance = levenshtein(
//        lhs, rhs
//    )
//
//    allActionDistance.times(actionMultiplier).squared() + killsDistance.times(killMultiplier)
//        .squared() + damageDistance.times(
//        damageMultiplier
//    ).squared() + recoveryDistance.times(recoveryMultiplier).squared()
//}


private fun behaviorMeasureInt(
    sequenceSeparator: Char = 2000.toChar(),
    actionMultiplier: Float = 1f,
    killMultiplier: Float = 50f,
    damageMultiplier: Float = 2f,
    recoveryMultiplier: Float = 5f
) = { a: ActionBehaviorInt, b: ActionBehaviorInt ->
    val allActionDistance = levenshteinInt(a.allActions, b.allActions)
    val damageDistance = levenshteinInt(a.damage, b.damage)
    val killsDistance = levenshteinInt(a.kills, b.kills)
    val lhs = a.recovery
    val rhs = b.recovery
//    val minLen = lhs.length
    val recoveryDistance = levenshteinInt(
        lhs, rhs
    )
    //
//        .squared() + (a.totalDamageDone - b.totalDamageDone).squared() + (a.totalDistanceTowardOpponent - b.totalDistanceTowardOpponent).div(
//        20
//    ).squared()
    val all = allActionDistance.times(actionMultiplier).squared()
    val kills = killsDistance.times(killMultiplier)
        .squared()
    val damage = damageDistance.times(
        damageMultiplier
    ).squared()
    val recovery = recoveryDistance.times(recoveryMultiplier)
        .squared()
//    val damageDone = (a.totalDamageDone - b.totalDamageDone).squared()
//    val totalDistanceToward = (a.totalDistanceTowardOpponent - b.totalDistanceTowardOpponent).div(
//        20f
//    ).squared()
    val totalFramesHitstun = (a.totalFramesHitstunOpponent - b.totalFramesHitstunOpponent).div(10).squared()
    val movement = (a.movement - b.movement).squared()
    (all + kills + damage  + recovery  + totalFramesHitstun + movement)
}
//
//
//private fun behaviorMeasure2(
//    sequenceSeparator: Char = 2000.toChar(),
//    actionMultiplier: Float = 1f,
//    killMultiplier: Float = 50f,
//    damageMultiplier: Float = 2f,
//    recoveryMultiplier: Float = 5f
//) = { a: ActionSumBehavior, b: ActionSumBehavior ->
////    val allActionDistance = levenshtein(a.allActions.actionString(), b.allActions.actionString())
////    val damageDistance = levenshtein(a.damage.actionString(), b.damage.actionString())
//    val killsDistance = levenshtein(a.kills.actionString(), b.kills.actionString())
//
//    sqrt(
//        killsDistance.times(killMultiplier)
//            .squared() + (a.recoveryCount - b.recoveryCount).times(recoveryMultiplier).squared() + (a.totalDistanceTowardOpponent - b.totalDistanceTowardOpponent)
//        .squared() + (a.totalDamageDone - b.totalDamageDone).div(10)
//        .squared() + (a.allActionsCount - b.allActionsCount).squared()
//    )
//}

private fun knnNoveltyArchive(k: Int, function: (ActionBehaviorInt, ActionBehaviorInt) -> Float) =
    KNNNoveltyArchive(k, 0f, behaviorDistanceMeasureFunction = function)


fun simulationFor(controllerId: Int, populationSize: Int, loadModels: Boolean): Simulation {
    val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = 1f, disjointCoefficient = 1f)
    val randomSeed: Int = 112 + controllerId
    val random = Random(randomSeed)
    val addConnectionAttempts = 5
    val shFunction = shFunction(.3f)


    val (simpleNeatExperiment, population, manifest) = if (loadModels) {
        val json = Json {}
        val manifest = json.decodeFromStream<Manifest>(File("population/${controllerId}_manifest.json").inputStream())
        val populationModel = loadPopulation(File("population/${controllerId}_population.json"), json)
        val models = populationModel.models
        logger.info { "population loaded with size of: ${models.size}" }
        val maxInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it } + 1
        val maxNodeInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it } + 1
        val simpleNeatExperiment = simpleNeatExperiment(
            random, maxInnovation, maxNodeInnovation, Activation.CPPN.functions, addConnectionAttempts, 2f
        )

        val population = models.map { it.toNeatMutator() }
        SimulationStart(simpleNeatExperiment, population, manifest)
    } else {
        val simpleNeatExperiment =
            simpleNeatExperiment(random, 0, 0, Activation.CPPN.functions, addConnectionAttempts, 2f)
        val population = simpleNeatExperiment.generateInitialPopulation2(
            populationSize, 7, 2, Activation.CPPN.functions
        )
        SimulationStart(
            simpleNeatExperiment,
            population,
            Manifest(0, SpeciesScoreKeeperModel(mapOf()), SpeciesLineageModel(mapOf()))
        )
    }

    val compatibilityDistanceFunction = compatibilityDistanceFunction(2f, 2f, 1f)
    val standardCompatibilityTest = standardCompatibilityTest({
        shFunction(it)
    }, { a, b ->
        cppnGeneRuler.measure(a, b)
    })
    val speciesId = (manifest.scoreLineageModel.speciesMap.keys.maxOrNull() ?: -1) + 1
    return simulation(
        standardCompatibilityTest,
        controllerId,
        distanceFunction = { a, b ->
            cppnGeneRuler.measure(a, b)
        },
        sharingFunction = {
            shFunction(it)
        },
        speciationController = SpeciationController(speciesId),
        simpleNeatExperiment = simpleNeatExperiment,
        population = population.map { it.clone(UUID.randomUUID()) },
        generation = manifest.generation,
        manifest = manifest
    )
}

fun Int.squared() = this * this
fun Float.squared() = this * this

fun List<Int>.actionString() = map { it.toChar() }.joinToString("")

data class SimulationStart(
    val neatExperiment: NeatExperiment, val population: List<NeatMutator>, val manifest: Manifest
)

@Serializable
enum class Character {
    Pikachu, Link, Bowser, CaptainFalcon, DonkeyKong, DoctorMario, Falco, Fox, GameAndWatch, GannonDorf, JigglyPuff, Kirby, Luigi, Mario, Marth, MewTwo, Nana, Ness, Peach, Pichu, Popo, Roy, Samus, Sheik, YoungLink, Yoshi, Zelda
}
