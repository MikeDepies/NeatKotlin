package server.refactor.needed

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
import io.ktor.util.date.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.novelty.KNNNoveltyArchive
import neat.novelty.euclidean
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import server.*
import server.message.endpoints.NeatModel
import server.message.endpoints.toModel
import server.server.WebSocketManager
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.mapKeys
import kotlin.collections.mapNotNull
import kotlin.collections.mapValues
import kotlin.collections.mutableListOf
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.shuffled
import kotlin.collections.takeLast
import kotlin.collections.toMap
import kotlin.collections.toMutableMap
import kotlin.math.absoluteValue
import kotlin.random.Random


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val logger = KotlinLogging.logger { }


@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.moduleNovelty(testing: Boolean = false) {

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
    install(CallLogging) {
//        level = Level.INFO
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

    //connectAndCreateDatabase()

//    println(get<Channel<FrameUpdate>>(qualifier<FrameUpdate>()))
//    println(get<Channel<FrameOutput>>(qualifier<FrameOutput>()))
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = LocalDateTime.now().let { File("runs/run-${it.format(format)}") }
    runFolder.mkdirs()
//    get<WebSocketManager>().attachWSRoute()
//    val controller1 = get<IOController>(parameters = { DefinitionParameters(listOf(0)) })
//    val controller2 = get<IOController>(parameters = { DefinitionParameters(listOf(1)) })
//    fun IOController.simulationForController(populationSize: Int) = get<Simulation>(parameters = {
//        DefinitionParameters(
//            listOf(controllerId, populationSize)
//        )
//    })
//    val (initialPopulation, populationEvolver, adjustedFitness) = controller1.simulationForController(500)

//    val evaluationChannels = get<EvaluationChannels>()
//    val evaluationChannels2 = get<EvaluationChannels>()
//    val evaluationMessageProcessor = get<EvaluationMessageProcessor>()
//    generateFakeData(evaluationChannels)

//    val b = Json { }.decodeFromString<List<ActionBehavior>>(
//        File("population/1_noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
//    )
//    networkEvaluatorOutputBridgeLoop(evaluationMessageProcessor, listOf(controller1))

    val evaluationId = 0
    val populationSize = 200
    val mateChance = .9f
    val survivalThreshold = .1f
    val stagnation = 60

    val randomSeed: Int = 5 + evaluationId
    val addConnectionAttempts = 5
    val activationFunctions = Activation.CPPN.functions
    val random = Random(randomSeed)
    val winners = mutableListOf<ScoreAndModel>()

//    val models = loadPopulation(File("population/population.json"), 0).models
//    logger.info { "population loaded with size of: ${models.size}" }
//    val maxNodeInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it } + 1
//    val maxInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it } + 1
//    val simpleNeatExperiment = simpleNeatExperiment(
//        random, maxInnovation, maxNodeInnovation, activationFunctions,
//        addConnectionAttempts
//    )
//    var population = models.map { it.toNeatMutator() }.mapIndexed { index, neatMutator ->
//        NetworkWithId(neatMutator, UUID.randomUUID().toString())
//    }
//    val behaviors = Json { }.decodeFromString<List<MarioDiscovery>>(
//        File("population/noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
//    )
    val populationHistory = mutableListOf<List<NeatModel>>()
    val simpleNeatExperiment = simpleNeatExperiment(random, 0, 0, activationFunctions, addConnectionAttempts, 2f)
    var population = simpleNeatExperiment.generateInitialPopulation2(
        populationSize, 7, 2, activationFunctions
    ).mapIndexed { index, neatMutator ->
        NetworkWithId(neatMutator, UUID.randomUUID().toString())
    }
    var settings = Settings(0f)
    var mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
    var finishedScores = population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()
//    createTaskNetwork(population.first().toNetwork())

    val simulation = createSimulation(
        evaluationId,
        population.map { it.neatMutator },
        distanceFunction,
        shFunction,
        mateChance,
        survivalThreshold,
        stagnation
    )
    with(simulation.populationEvolver) {
        speciationController.speciate(
            population.map { it.neatMutator },
            speciesLineage,
            generation,
            standardCompatibilityTest(shFunction, distanceFunction)
        )
    }
    var scores = mutableListOf<FitnessModel<NeatMutator>>()
    var seq = population.iterator()
    var activeModel: NetworkWithId = population.first()
    val knnNoveltyArchive = KNNNoveltyArchiveWeighted(100,  40,settings.noveltyThreshold) { a, b ->
        val euclidean = euclidean(a.toVector(), b.toVector())
        euclidean
    }
//    knnNoveltyArchive.behaviors.addAll(behaviors)

    fun processPopulation(populationEvolver: PopulationEvolver) {

        if (scores.size == populationSize) {
            logger.info { "New generation ${populationEvolver.generation}" }
            val toModelScores = scores.toModelScores(
                adjustedFitnessCalculation(
                    simulation.populationEvolver.speciationController, distanceFunction, shFunction
                )
            )
            populationHistory.add(population.map { it.neatMutator.toModel() })
            population = evolve(
                populationEvolver, toModelScores, simpleNeatExperiment, population.size
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
            runFolder.resolve("winners.json").bufferedWriter().use {
                it.write(json.encodeToString(winners))
                it.flush()
            }
            runFolder.resolve("${evaluationId}_noveltyArchive.json").bufferedWriter().use {
                val json = Json { prettyPrint = true }
                it.write(json.encodeToString(knnNoveltyArchive.behaviors))
                it.flush()
            }
        }
    }

    val modelChannel = Channel<NetworkBlueprint>(40)
    val neatMutatorChannel = Channel<NetworkWithId>(Channel.UNLIMITED)
    launch {
        var lastRefill = getTimeMillis()
        while (true) {
            if (seq.hasNext()) {

                neatMutatorChannel.send(seq.next())
            } else if (neatMutatorChannel.isEmpty && modelChannel.isEmpty && !seq.hasNext() && getTimeMillis() - lastRefill > 15_000) {
                seq = finishedScores.filter { !it.value }.mapNotNull { mapIndexed[it.key] }.iterator()
                lastRefill = getTimeMillis()
            }
        }
    }
    repeat(10) {
        launch(Dispatchers.Default) {
            val createNetwork = createNetwork()
            val connectionRelationships =
                createNetwork.connectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
            val targetConnectionMapping =
                createNetwork.targetConnectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
            val calculationOrder = createNetwork.calculationOrder.map { it.id }
            val populationEvolver = simulation.populationEvolver
            while (true) {
                val network = neatMutatorChannel.receive()
                val message = try {
                    NetworkBlueprint(
                        network.id,
                        createNetwork.planes,
                        connectionRelationships,
                        targetConnectionMapping,
                        calculationOrder,
                        populationEvolver.speciationController.species(network.neatMutator).id,
                        network.neatMutator.hiddenNodes.size,
                        createNetwork.outputPlane.map { it.id },
                        createNetwork.inputPlane.map { it.id },
                        network.neatMutator.toModel(),
                        createNetwork.depth
                    )
                } catch (e: Exception) {
                    log.error(e)
                    NetworkBlueprint(

                        network.id,
                        createNetwork.planes,
                        connectionRelationships,
                        targetConnectionMapping,
                        calculationOrder,
                        populationEvolver.speciationController.species(network.neatMutator).id,
                        network.neatMutator.hiddenNodes.size,
                        createNetwork.outputPlane.map { it.id },
                        createNetwork.inputPlane.map { it.id },
                        network.neatMutator.toModel(),
                        createNetwork.depth
                    )
                }
                modelChannel.send(message)
            }
        }
    }
    val scoreChannel = Channel<MarioDiscovery>(Channel.UNLIMITED)
    routing {
        get("/model/request") {
            val model = modelChannel.receive()

            call.respond(model)
        }
//        get("/winners") {
//            val model = modelChannel.receive()
//
//            call.respond(model)
//        }
        post<DeadNetwork>("/dead") {
            val a = mapIndexed[it.id]
            if (a != null) {
                scores += FitnessModel(a.neatMutator, 0f)
                finishedScores[it.id] = true

            }
            call.respond("ok")
        }
        post<MarioDiscovery>("/score") {


            scoreChannel.send(it)
        }
        get("behaviors") {
            val numberOfBehaviors = call.parameters["n"]
            val message = if (numberOfBehaviors == null) {
                knnNoveltyArchive.behaviors
            } else knnNoveltyArchive.behaviors.takeLast(numberOfBehaviors.toInt())
            call.respond(message)
        }
        get("settings") {
            call.respond(settings)
        }
        post<Settings>("settings") {
            settings = it
            knnNoveltyArchive.noveltyThreshold = settings.noveltyThreshold
            logger.info { "$it applied" }
        }
        post<ModelsRequest>("models") {
            call.respond(populationHistory.drop(it.skip).take(it.generations))
        }


    }
    launch(Dispatchers.Default) {
        for (it in scoreChannel) {
            val populationEvolver = simulation.populationEvolver
            val b = if (knnNoveltyArchive.size > 0) {
                val addBehavior = knnNoveltyArchive.addBehavior(it)
                (if (addBehavior < knnNoveltyArchive.noveltyThreshold) 0f else addBehavior)
            } else {
                knnNoveltyArchive.addBehavior(it)
//                euclidean(toVector(it), toVector(it).map { 0f})
                it.stageParts.toFloat()
            }
            val score = b /** (it.stageParts)*///+ ((it.stageParts * 8) / (it.time)) + ((it.stage -1) + (it.world -1) * 4)  * 200f
//            knnNoveltyArchive.behaviors.add(it)

            val model = mapIndexed[it.id]?.neatMutator
            if (finishedScores[it.id] != true && model != null) {
                if (it.flags > 0) winners += ScoreAndModel(model.toModel(), it, score)
                scores += FitnessModel(model, score)
                finishedScores[it.id] = true
                val species = if (populationEvolver.speciationController.hasSpeciesFor(model)) "${
                    populationEvolver.speciationController.species((model))
                }" else "No Species"
                logger.info { "[G${populationEvolver.generation}][S${species} / ${populationEvolver.speciationController.speciesSet.size}] Model (${scores.size}) Score: $score " }
                logger.info { "$it" }
            }
            processPopulation(populationEvolver)
        }
    }
}
data class ModelsRequest(val generations : Int, val skip : Int)

