package server

import Auth0Config
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.FitnessModel
import neat.novelty.KNNNoveltyArchive
import neat.novelty.levenshtein
import neat.toModelScores
import neat.toNetwork
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
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.sqrt


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val log = KotlinLogging.logger { }

data class ScoredModel(val score : Float, val generation : Int, val model : NetworkDescription, val id : String)

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
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = LocalDateTime.now().let { File("runs/run-${it.format(format)}") }
    runFolder.mkdirs()
    get<WebSocketManager>().attachWSRoute()
    val controller1 = get<IOController>(parameters = { DefinitionParameters(listOf(0)) })
    fun IOController.simulationForController(populationSize: Int) = get<Simulation>(parameters = {
        DefinitionParameters(
            listOf(controllerId, populationSize)
        )
    })

    val populationSize = 100
    val (initialPopulation, populationEvolver, adjustedFitness) = controller1.simulationForController(populationSize)
    val evaluationChannels = get<EvaluationChannels>()
    val evaluationMessageProcessor = get<EvaluationMessageProcessor>()
    networkEvaluatorOutputBridgeLoop(evaluationMessageProcessor, listOf(controller1/*, controller2*/))
    ////
//    val a = Json { }.decodeFromString<List<ActionBehavior>>(
//        ListSerializer(ActionBehavior.serializer()),
//        File("population/0_noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
//    )
    val sequenceSeparator = 2000.toChar()
    val knnNoveltyArchive = KNNNoveltyArchive<ActionBehavior>(60, 0f) { a, b ->
        val allActionDistance = levenshtein(a.allActions.actionString(), b.allActions.actionString())
        val damageDistance = levenshtein(a.damage.actionString(), b.damage.actionString())
        val killsDistance = levenshtein(a.kills.actionString(), b.kills.actionString())
        val lhs = a.recovery.joinToString("$sequenceSeparator") { it.actionString() }
        val rhs = b.recovery.joinToString("$sequenceSeparator") { it.actionString() }
        val recoveryDistance = levenshtein(
            lhs,
            rhs
        )
        sqrt(
            allActionDistance.times(3).squared() + killsDistance.times(100).squared() + damageDistance.times(10)
                .squared() + recoveryDistance.times(50).squared().toFloat()
        )
    }
    //    knnNoveltyArchive.behaviors.addAll(a)
    fun fromId(controllerId: Int): IOController = when (controllerId) {
        0 -> controller1
//        1 -> controller2
        else -> throw Exception()
    }

    var evolutionInProgress = false
    var population = initialPopulation.mapIndexed { index, neatMutator ->
        NetworkWithId(neatMutator, UUID.randomUUID().toString())
    }.shuffled()
    var modelStatusMap = population.map {
        it.id to ModelStatus(false, null, null)
    }.toMap()
    var modelMap: MutableMap<String, NetworkDescription?> = population.map {
        it.id to null
    }.toMap().toMutableMap()
    launch(Dispatchers.Default) {
        population.mapParallel {
            try {
                it to createTaskNetwork(it.neatMutator.toNetwork(), it.id)
            } catch (e: java.lang.Exception) {
                it to NetworkDescription(setOf(), setOf(), it.id, null, listOf())
            }
        }
//            .flowOn(Dispatchers.Default)
            .forEach { (networkWithId, network) ->
                val id = networkWithId.id
                val modelStatus = modelStatusMap[id]
                if (modelStatus != null) {
                    server.log.info { "Creating task network $id" }
                    modelMap[id] = network
                    modelStatus.available = true
                    modelStatus.neatMutator = networkWithId.neatMutator
                } else {
                    server.log.error { "Failed to put $id in model map..." }
                }
            }
    }
//    var modelListIndex = 0
    var modelListSize = 4
    val modelIdChannel = Channel<List<String>>(Channel.UNLIMITED)
    var windowed = population.map { it.id }.windowed(modelListSize, modelListSize, true)

    launch {
        for (w in windowed) {
            modelIdChannel.send(w)
        }
    }
//    var bestScore = 0f
    var bestModels = mutableListOf<ScoredModel>()
    val scoreChannel = Channel<ModelEvaluationResult>(Channel.UNLIMITED)
    launch(Dispatchers.Default) {
        for (it in scoreChannel) {
            val m = modelMap.get(it.modelId) ?: continue
            val modelStatus = modelStatusMap[it.modelId] ?: continue
            if (modelStatus.score != null) {
                continue
            }

            server.log.info { "new score recieved" }
            val behaviorScore = when {
                knnNoveltyArchive.size < 1 -> {
                    knnNoveltyArchive.addBehavior(it.score)
                    it.score.allActions.size.toFloat()
                }
                else -> knnNoveltyArchive.addBehavior(it.score)
            } + it.score.totalDamageDone / 5
            if (m != null && it.score.totalDamageDone > 0) {
//                bestScore = behaviorScore
                bestModels += ScoredModel(behaviorScore, populationEvolver.generation, m, it.modelId)
                bestModels.sortByDescending { it.score  - (populationEvolver.generation - it.generation) * 250}
                if (bestModels.size > 10) {
                    bestModels.removeAt(10)
                }
            }
            if (modelStatus != null && modelStatus.score == null) {
                modelStatus.score = behaviorScore
                val neatMutator = modelStatus.neatMutator
                if (neatMutator != null) {
                    val species = populationEvolver.speciationController.species(neatMutator)
                    server.log.info {
                        "[$species | ${
                            modelStatusMap.count { (id, status) ->
                                status.score != null
                            }
                        } | ${populationEvolver.generation}] Score: $behaviorScore Recoveries: ${it.score.recovery}"
                    }
                }
//                evaluationChannels.scoreChannel.send(EvaluationScore(evaluationId, it.modelId, behaviorScore, listOf()))
            }
            //Check if all models have been contributed
            val allScored = modelStatusMap.all { (id, status) ->
                status.score != null
            }
            if (allScored) {
                server.log.info { "Scores: ${bestModels.map { "${it.generation} - ${it.id} - ${it.score}" }}"  }
                evolutionInProgress = true
//                bestScore -= bestScore /10
                //evolve new population!
                val modelScores = modelStatusMap.mapNotNull { (id, status) ->
                    if (status.score != null) {
                        FitnessModel(status.neatMutator!!, status.score!!)
                    } else null
                }.toModelScores(adjustedFitness)//.filter { it.fitness > 0f }
                populationEvolver.sortPopulationByAdjustedScore(modelScores)
                populationEvolver.updateScores(modelScores)
                var newPopulation = populationEvolver.evolveNewPopulation(modelScores)

                server.log.info { "Species Count: ${populationEvolver.speciesLineage.species.size}" }
                while (newPopulation.size < populationSize) {
                    newPopulation = newPopulation + newPopulation.first().clone()
                }
                populationEvolver.speciate(newPopulation)
                if (newPopulation.size > populationSize) {
                    val diff = newPopulation.size - populationSize
                    val dropList = newPopulation.drop(diff)
                    val speciationController = populationEvolver.speciationController
                    server.log.info {
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
                population = newPopulation.take(populationSize).map { NetworkWithId(it, "${UUID.randomUUID()}") }
                modelStatusMap = population.map {
                    it.id to ModelStatus(false, null, null)
                }.toMap()
                modelMap = population.map {
                    it.id to null
                }.toMap().toMutableMap()
                windowed = population.map { it.id }.windowed(modelListSize, modelListSize, true)
                server.log.info { "setting up next generation" }
                launch {
                    for (w in windowed) {
                        server.log.info { w }
                        modelIdChannel.send(w)
                    }
                }
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


                launch(Dispatchers.Default) {
                    population.mapParallel {
                        try {
                            it to createTaskNetwork(it.neatMutator.toNetwork(), it.id)
                        } catch (e: java.lang.Exception) {
                            it to NetworkDescription(setOf(), setOf(), it.id, null, listOf())
                        }
                    }
//            .flowOn(Dispatchers.Default)
                        .forEach { (networkWithId, network) ->
                            val id = networkWithId.id
                            val modelStatus = modelStatusMap[id]
                            if (modelStatus != null) {
                                server.log.info { "Creating task network $id" }
                                modelMap[id] = network
                                modelStatus.available = true
                                modelStatus.neatMutator = networkWithId.neatMutator
                                evolutionInProgress = false
                            } else {
                                server.log.error { "Failed to put $id in model map..." }
                            }
                        }
                }

            }
        }
    }

    routing {

        post<ModelsRequest>("/models") {
            if (evolutionInProgress) {
//                server.log.info { "Evoluation still in progress" }
                call.respond(ModelsStatusResponse(false, listOf()))
            } else {
                val modelIds = modelIdChannel.poll()
                if (modelIds == null) {
                    windowed = population.filter { modelStatusMap.getValue(it.id).score == null }.map { it.id }
                        .windowed(modelListSize, modelListSize, true)
                    for (w in windowed) {
                        modelIdChannel.send(w)
                    }
//                    server.log.info { "Models not ready..." }
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
            get("/best") {
                call.respond(ArrayList(bestModels).random().model)
            }
            post<ModelRequest>("/request") { modelRequest ->
                val networkDescription = modelMap[modelRequest.modelId]
                if (networkDescription != null) {
//                    server.log.info { "Model ready?: ${modelRequest.modelId} | ${modelStatusMap.get(modelRequest.modelId)?.available ?: false}" }
                    call.respond(networkDescription)
                } else {
//                    server.log.info { "Model not ready: ${modelRequest.modelId}" }
                    call.respond(HttpStatusCode.RequestTimeout, "Model not ready")
                }
            }
            post<ModelRequest>("/check") { modelRequest ->
                val modelStatus = modelStatusMap[modelRequest.modelId]
                if (modelStatus != null) {
//                    server.log.info { "$modelStatus" }
//                    server.log.info { "Model ready: ${modelRequest.modelId} | ${modelStatus.available}" }
                    call.respond(ModelTestResult(modelStatus.available, modelStatus.score != null, true))
                } else {
//                    server.log.info { "Model Check, Status not ready: ${modelRequest.modelId}" }
                    call.respond(ModelTestResult(available = false, scored = false, valid = false))
                }
            }
            post<ModelEvaluationResult>("/score") {
//                server.log.info { "Sending Score... ${it.modelId}" }
                scoreChannel.send(it)
//                server.log.info { "Score Sent: ${it.modelId}" }
                call.respond("success")
            }
        }
    }
}

suspend fun <T, R> Iterable<T>.mapParallel(transform: (T) -> R): List<R> = coroutineScope {
    map { async { transform(it) } }.map { it.await() }
}