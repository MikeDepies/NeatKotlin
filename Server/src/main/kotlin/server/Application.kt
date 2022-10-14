package server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.novelty.KNNNoveltyArchive
import neat.novelty.levenshtein
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.event.Level
import server.local.*
import server.message.endpoints.NeatModel
import server.message.endpoints.Simulation
import server.message.endpoints.toModel
import server.service.TwitchBotService
import server.service.TwitchModel
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.log
import kotlin.math.sqrt
import kotlin.random.Random


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val logger = KotlinLogging.logger { }

data class ScoredModel(val score: Float, val generation: Int, val model: NeatMutator, val id: String)
@Serializable
data class ScoredModelSerializable(val score : Float, val generation : Int, val model : NeatModel, val id: String)


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

//    val a = actionBehaviors("population/0_noveltyArchive.json").takeLast(50000)
//    val b = actionBehaviors("population/1_noveltyArchive.json").takeLast(5000)

    fun simulationForController(controllerId: Int, populationSize: Int): Simulation =
        simulationFor(controllerId, populationSize, false)

    val populationSize = 200
    val knnNoveltyArchive = knnNoveltyArchive(
        10,
        behaviorMeasure(damageMultiplier = 1f, actionMultiplier = .5f, killMultiplier = 15f, recoveryMultiplier = 1f)
    )
    val knnNoveltyArchive2 = knnNoveltyArchive(
        40,
        behaviorMeasure(damageMultiplier = 1f, actionMultiplier = 1f, killMultiplier = 15f, recoveryMultiplier = 1f)
    )
//    knnNoveltyArchive.behaviors.addAll(a)
//    knnNoveltyArchive2.behaviors.addAll(b)
    val (initialPopulation, populationEvolver, adjustedFitness) = simulationForController(0, populationSize)
    val evoManager =
        EvoManager(populationSize, populationEvolver, adjustedFitness, evaluationId, runFolder, knnNoveltyArchive)

//    val (initialPopulation2, populationEvolver2, adjustedFitness2) = simulationForController(1, populationSize)
//    val evoManager2 =
//        EvoManager(populationSize, populationEvolver2, adjustedFitness2, evaluationId2, runFolder, knnNoveltyArchive2)
    launch { evoManager.start(initialPopulation) }
//    launch { evoManager2.start(initialPopulation2) }

    routing(
        EvoControllerHandler(
            mapOf(
                evaluationId to evoManager,
//                evaluationId2 to evoManager2
            )
        )
    )
}

private fun actionBehaviors(noveltyArchiveJson: String) = Json { }.decodeFromString<List<ActionBehavior>>(
    ListSerializer(ActionBehavior.serializer()),
    File(noveltyArchiveJson).bufferedReader().lineSequence().joinToString("")
)

class EvoControllerHandler(val map: Map<Int, EvoManager>) {
    fun evoManager(controllerId: Int): EvoManager {
        return map.getValue(controllerId)
    }
}

fun character(controllerId: Int) = when (controllerId) {
    0 -> Character.Link
    1 -> Character.Pikachu
    else -> throw Exception()
}

private fun Application.routing(
    evoHandler: EvoControllerHandler,
) {
    val evaluatorSettings = EvaluatorSettings(10, 120, 12)
    val pythonConfiguration = PythonConfiguration(
        evaluatorSettings,
        ControllerConfiguration(Character.Mario, 0),
        ControllerConfiguration(Character.Fox, 5),
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
            val networkWithId = orNull ?: ArrayList(evoManager.bestModels).random()
                .let { model -> NetworkWithId(model.model, model.id) }
            val neatMutator = networkWithId.neatMutator
            val networkBlueprint = NetworkBlueprint(
                networkWithId.id,
                createNetwork.planes,
                connectionRelationships,
                targetConnectionMapping,
                calculationOrder,
                0,
                neatMutator.hiddenNodes.size,
                createNetwork.outputPlane.id,
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
                val model = ArrayList(evoManager.bestModels).random()
                val networkWithId = NetworkWithId(model.model, model.id)
                val neatMutator = networkWithId.neatMutator
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
                    networkWithId.id,
                    createNetwork.planes,
                    connectionRelationships,
                    targetConnectionMapping,
                    calculationOrder,
                    0,
                    neatMutator.hiddenNodes.size,
                    createNetwork.outputPlane.id,
                    neatMutator.toModel(),
                    createNetwork.depth,
                    true
                )
                call.respond(networkBlueprint)
            }

            post<ModelsRequest>("/bestList") {
                val evoManager = evoHandler.evoManager(it.controllerId)
                val bestModels = evoManager.bestModels.map {
                    ScoredModelSerializable(it.score, it.generation, it.model.toModel(), it.id)
                }
                call.respond(bestModels)
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
        get("configuration") {
            call.respond(pythonConfiguration)
        }
    }
}


suspend fun <T, R> Iterable<T>.mapParallel(transform: (T) -> R): List<R> = coroutineScope {
    map { async { transform(it) } }.map { it.await() }
}


private fun behaviorMeasure(
    sequenceSeparator: Char = 2000.toChar(),
    actionMultiplier: Float = 1f,
    killMultiplier: Float = 10f,
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
        ).squared() + recoveryDistance.times(recoveryMultiplier).squared().toFloat()
    )
}

private fun knnNoveltyArchive(k: Int, function: (ActionBehavior, ActionBehavior) -> Float) =
    KNNNoveltyArchive(k, 0f, behaviorDistanceMeasureFunction = function)


fun simulationFor(controllerId: Int, populationSize: Int, loadModels: Boolean): Simulation {
    val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = .1f, disjointCoefficient = 1f)
    val randomSeed: Int = 123 + controllerId
    val random = Random(randomSeed)
    val addConnectionAttempts = 5
    val shFunction = shFunction(.34f)


    val (simpleNeatExperiment, population) = if (loadModels) {
        val populationModel = loadPopulation(File("population/${controllerId}_population.json"))
        val models = populationModel.models
        logger.info { "population loaded with size of: ${models.size}" }
        val maxInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it } + 1
        val maxNodeInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it } + 1
        val simpleNeatExperiment = simpleNeatExperiment(
            random, maxInnovation, maxNodeInnovation, Activation.CPPN.functions,
            addConnectionAttempts
        )
        val population = models.map { it.toNeatMutator() }
        simpleNeatExperiment to population
    } else {
        val simpleNeatExperiment = simpleNeatExperiment(random, 0, 0, Activation.CPPN.functions, addConnectionAttempts)
        val population = simpleNeatExperiment.generateInitialPopulation2(
            populationSize,
            6,
            2,
            Activation.CPPN.functions
        )
        simpleNeatExperiment to population
    }

    val compatibilityDistanceFunction = compatibilityDistanceFunction(2f, 2f, 1f)
    val standardCompatibilityTest = standardCompatibilityTest({
        shFunction(it)
    }, { a, b ->
        cppnGeneRuler.measure(a, b)
    })
    return simulation(
        standardCompatibilityTest,
        controllerId,
        distanceFunction = { a, b ->
            cppnGeneRuler.measure(a, b)
        },
        sharingFunction = {
            shFunction(it)
        },
        speciationController = SpeciationController(0),
        simpleNeatExperiment = simpleNeatExperiment,
        population = population,
        generation = 0//if (controllerId == 0) 11612 else 11547
    )
}

fun Int.squared() = this * this
fun Float.squared() = this * this

fun List<Int>.actionString() = map { it.toChar() }.joinToString("")


@Serializable
enum class Character {
    Pikachu, Link, Bowser, CaptainFalcon, DonkeyKong, DoctorMario,
    Falco, Fox, GameAndWatch, GannonDorf,
    JigglyPuff, Kirby, Luigi, Mario, Marth, MewTwo, Nana,
    Ness, Peach, Pichu, Popo,
    Roy, Samus, Sheik, YoungLink, Yoshi, Zelda
}