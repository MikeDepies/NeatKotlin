package server

import PopulationEvolver
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.novelty.NoveltyArchive
import neat.novelty.levenshtein
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.event.Level
import server.local.*
import server.message.endpoints.Simulation
import server.message.endpoints.toModel

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.streams.toList


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val logger = KotlinLogging.logger { }

data class ScoredModel(val score: Float, val generation: Int, val model: NetworkBlueprint, val id: String)


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
    install(CallLogging) {
        level = Level.INFO
    }
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
//    val a = actionBehaviors("population/0_noveltyArchive.json").takeLast(5000)
//    val b = actionBehaviors("population/1_noveltyArchive.json").takeLast(5000)

    fun simulationForController(controllerId: Int, populationSize: Int): Simulation =
        simulationFor(controllerId, populationSize, false)

    val populationSize = 200
    val knnNoveltyArchive = knnNoveltyArchive(
        40,
        behaviorMeasure(damageMultiplier = 1f, actionMultiplier = 5f, killMultiplier = 50f, recoveryMultiplier = 20f)
    )
    val knnNoveltyArchive2 = knnNoveltyArchive(
        40,
        behaviorMeasure(damageMultiplier = 1f, actionMultiplier = 5f, killMultiplier = 50f, recoveryMultiplier = 20f)
    )
//    knnNoveltyArchive.behaviors.addAll(a)
//    knnNoveltyArchive2.behaviors.addAll(b)
    val (initialPopulation, populationEvolver, adjustedFitness) = simulationForController(0, populationSize)
    val evoManager =
        EvoManager(populationSize, populationEvolver, adjustedFitness, evaluationId, runFolder, knnNoveltyArchive)

    val (initialPopulation2, populationEvolver2, adjustedFitness2) = simulationForController(1, populationSize)
    val evoManager2 =
        EvoManager(populationSize, populationEvolver2, adjustedFitness2, evaluationId2, runFolder, knnNoveltyArchive2)
    launch { evoManager.start(initialPopulation) }
    launch { evoManager2.start(initialPopulation2) }
    initialPopulation.first().toModel()
    routing(
        EvoControllerHandler(
            mapOf(
                evaluationId to evoManager,
                evaluationId2 to evoManager2
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

private fun Application.routing(
    evoHandler: EvoControllerHandler,
) {
    routing {
        post<ModelsRequest>("/models") {
            val evoManager = evoHandler.evoManager(it.controllerId)
            if (evoManager.evolutionInProgress) {
//                server.log.info { "Evo in progress" }
                call.respond(ModelsStatusResponse(false, listOf()))
            } else {
                val modelIds = evoManager.windower.poll()
                if (modelIds == null) {
                    evoManager.windower.fill(evoManager.population.filter { evoManager.modelStatusMap.getValue(it.id).score == null })
                    call.respond(ModelsStatusResponse(false, listOf()))
                } else {
//                    server.log.info { "Models Ready ${modelIds}" }
                    val modelsStatusResponse = ModelsStatusResponse(
                        true, modelIds
                    )
                    call.respond(modelsStatusResponse)
                }
            }
        }
        route("/model") {
            post<ModelsRequest>("/generation") {
                val evoManager = evoHandler.evoManager(it.controllerId)

                call.respond(evoManager.populationEvolver.generation)
            }
            post<ModelsRequest>("/best") {
                val evoManager = evoHandler.evoManager(it.controllerId)

                val model = ArrayList(evoManager.bestModels).random().model
                model.id
                call.respond(model)
            }
            post<ModelRequest>("/request") { modelRequest ->
                val evoManager = evoHandler.evoManager(modelRequest.controllerId)
                val networkDescription = evoManager.modelMap[modelRequest.modelId]
                if (networkDescription != null) {
                    call.respond(networkDescription)
                } else {
                    call.respond(HttpStatusCode.RequestTimeout, "Model not ready")
                }
            }
            post<ModelRequest>("/check") { modelRequest ->
                val evoManager = evoHandler.evoManager(modelRequest.controllerId)
                val modelStatus = evoManager.modelStatusMap[modelRequest.modelId]
                if (modelStatus != null) {
                    call.respond(ModelTestResult(modelStatus.available, modelStatus.score != null, true))
                } else {
                    call.respond(ModelTestResult(available = false, scored = false, valid = false))
                }
            }
            post<ModelEvaluationResult>("/score") {
                val evoManager = evoHandler.evoManager(it.controllerId)
                evoManager.scoreChannel.send(it)
                call.respond("success")
            }
        }
    }
}

private fun safeEvolvePopulation(
    populationEvolver: PopulationEvolver, modelScores: List<ModelScore>
): List<NeatMutator> {
    return try {
        populationEvolver.evolveNewPopulation(modelScores)
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        throw e
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
    KNNNoveltyArchiveWeighted(k, 0f, behaviorDistanceMeasureFunction = function)


fun simulationFor(controllerId: Int, populationSize: Int, loadModels: Boolean): Simulation {
    val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = .5f, disjointCoefficient = 1f)
    val randomSeed: Int = 123 + controllerId
    val random = Random(randomSeed)
    val addConnectionAttempts = 5
    val shFunction = shFunction(.44f)


    val (simpleNeatExperiment, population) = if (loadModels) {
        val populationModel = loadPopulation(File("population/${controllerId}_population.json"))
        val models = populationModel.models
        logger.info { "population loaded with size of: ${models.size}" }
        val maxNodeInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it } + 1
        val maxInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it } + 1
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
        generation = if (controllerId == 0) 11612 else 11547
    )
}

class KNNNoveltyArchiveWeighted(
    var k: Int,
    var noveltyThreshold: Float,
    val behaviorFilter: (ActionBehavior, ActionBehavior) -> Boolean = { _, _ -> true },
    val behaviorDistanceMeasureFunction: (ActionBehavior, ActionBehavior) -> Float
) :
    NoveltyArchive<ActionBehavior> {
    override val behaviors = mutableListOf<ActionBehavior>()
    override val size: Int
        get() = behaviors.size
    var maxBehavior = ActionBehavior(listOf(), listOf(), listOf(), listOf(), 0.1f, 0f, false)
    override fun addBehavior(behavior: ActionBehavior): Float {
        val distance = measure(behavior)
        if (distance > noveltyThreshold || size == 0)
            behaviors += behavior
        return distance
    }

    override fun measure(behavior: ActionBehavior): Float {
        val value = valueForBehavior(behavior)
        if (value > valueForBehavior(maxBehavior))
            maxBehavior = behavior

        val n = k + value
        logger.info { "K = $n" }
        val distance = behaviors.parallelStream().filter { behaviorFilter(behavior, it) }
            .map { behaviorDistanceMeasureFunction(behavior, it) }
            .sorted().toList().take(n.toInt()).average()
            .toFloat()
        return if (distance.isNaN()) 0f else distance
    }

    private fun valueForBehavior(behavior: ActionBehavior) =
        behavior.totalDamageDone / 5 + (behavior.kills.size * 30) + (behavior.totalDistanceTowardOpponent / 20)
}