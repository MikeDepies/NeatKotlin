package server

import Auth0Config
import PopulationEvolver
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.ModelScore
import neat.model.NeatMutator
import neat.novelty.KNNNoveltyArchive
import neat.novelty.levenshtein
import org.koin.core.parameter.DefinitionParameters
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import server.local.*
import server.message.endpoints.Simulation
import server.server.WebSocketManager
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.math.sqrt


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val log = KotlinLogging.logger { }

data class ScoredModel(val score: Float, val generation: Int, val model: NetworkDescription, val id: String)

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
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
            single {
                with(environment.config) {
                    Auth0Config(
                        property("ktor.auth0.clientID").getString(),
                        property("ktor.auth0.clientSecret").getString(),
                        property("ktor.auth0.audience").getString(),
                        property("ktor.auth0.grantType").getString()
                    )
                }
            }
        })
    }
    install(ContentNegotiation) {
        json(get())
    }
    val evaluationId = 0
    val evaluationId2 = 1
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = LocalDateTime.now().let { File("runs/run-${it.format(format)}") }
    runFolder.mkdirs()
    get<WebSocketManager>().attachWSRoute()
    val a = actionBehaviors("population/0_noveltyArchive.json")
    val b = actionBehaviors("population/1_noveltyArchive.json")
    val controller1 = get<IOController>(parameters = { DefinitionParameters(listOf(evaluationId)) })
    val controller2 = get<IOController>(parameters = { DefinitionParameters(listOf(evaluationId2)) })
    fun IOController.simulationForController(populationSize: Int) = get<Simulation>(parameters = {
        DefinitionParameters(
            listOf(controllerId, populationSize)
        )
    })

    val populationSize = 200
    val knnNoveltyArchive = knnNoveltyArchive(80, behaviorMeasure(damageMultiplier = 1f, actionMultiplier = 3f, killMultiplier = 50f, recoveryMultiplier = 5f))
    val knnNoveltyArchive2 = knnNoveltyArchive(30, behaviorMeasure(damageMultiplier = 3f, actionMultiplier = 1f, killMultiplier = 50f, recoveryMultiplier = 1f))
    knnNoveltyArchive.behaviors.addAll(a)
    knnNoveltyArchive2.behaviors.addAll(b)
    val (initialPopulation, populationEvolver, adjustedFitness) = controller1.simulationForController(populationSize )
    val evoManager =
        EvoManager(populationSize, populationEvolver, adjustedFitness, evaluationId, runFolder, knnNoveltyArchive)

    val (initialPopulation2, populationEvolver2, adjustedFitness2) = controller2.simulationForController(populationSize)
    val evoManager2 =
        EvoManager(populationSize, populationEvolver2, adjustedFitness2, evaluationId2, runFolder, knnNoveltyArchive2)
    launch { evoManager.start(initialPopulation) }
    launch { evoManager2.start(initialPopulation2) }

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
                call.respond(ArrayList(evoManager.bestModels).random().model)
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
    val recoveryDistance = levenshtein(lhs, rhs
    )
    sqrt(
        allActionDistance.times(actionMultiplier).squared() + killsDistance.times(killMultiplier)
            .squared() + damageDistance.times(
            damageMultiplier
        ).squared() + recoveryDistance.times(recoveryMultiplier).squared().toFloat()
    )
}

private fun knnNoveltyArchive(k: Int, function: (ActionBehavior, ActionBehavior) -> Float) =
    KNNNoveltyArchive<ActionBehavior>(k, 0f, behaviorDistanceMeasureFunction = function)