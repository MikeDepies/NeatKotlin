package server

import Auth0Config
import MessageWriter
import PopulationEvolver
import UserRef
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.*
import neat.*
import neat.model.*
import neat.novelty.*
import org.jetbrains.exposed.sql.*
import org.koin.ktor.ext.*
import org.slf4j.event.*
import server.message.*
import server.message.endpoints.*
import server.server.*
import java.io.*
import java.time.*
import java.time.format.*
import java.util.*
import kotlin.random.*


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val logger = KotlinLogging.logger { }

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
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
    get<WebSocketManager>().attachWSRoute()
//    val controller1 = get<IOController>(parameters = { DefinitionParameters(listOf(0)) })
//    val controller2 = get<IOController>(parameters = { DefinitionParameters(listOf(1)) })
//    fun IOController.simulationForController(populationSize: Int) = get<Simulation>(parameters = {
//        DefinitionParameters(
//            listOf(controllerId, populationSize)
//        )
//    })
//    val (initialPopulation, populationEvolver, adjustedFitness) = controller1.simulationForController(500)

    val evaluationChannels = get<EvaluationChannels>()
    val evaluationChannels2 = get<EvaluationChannels>()
    val evaluationMessageProcessor = get<EvaluationMessageProcessor>()
//    generateFakeData(evaluationChannels)

//    val b = Json { }.decodeFromString<List<ActionBehavior>>(
//        File("population/1_noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
//    )
//    networkEvaluatorOutputBridgeLoop(evaluationMessageProcessor, listOf(controller1))

    val evaluationId = 0
    val populationSize = 100


    val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = 1f, disjointCoefficient = 1f)
    val shFunction = shFunction(.1f)
    val mateChance = .1f
    val survivalThreshold = .40f
    val stagnation = 30

    val randomSeed: Int = 2 + evaluationId
    val addConnectionAttempts = 5
    val activationFunctions = Activation.CPPN.functions
    val random = Random(randomSeed)


    val models = loadPopulation(File("population/population.json")).models
    logger.info { "population loaded with size of: ${models.size}" }
    val maxNodeInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it }
    val maxInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it }
    val simpleNeatExperiment = simpleNeatExperiment(
        random, maxInnovation, maxNodeInnovation, activationFunctions,
        addConnectionAttempts
    )
    var population = models.map { it.toNeatMutator() }.mapIndexed { index, neatMutator ->
        NetworkWithId(neatMutator, UUID.randomUUID().toString())
    }
    val behaviors = Json { }.decodeFromString<List<MarioInfo>>(
        File("population/noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
    )
//    val simpleNeatExperiment =
//        simpleNeatExperiment(random, 0, 0, activationFunctions, addConnectionAttempts)
////    var modelIndex = 0
//    var population = simpleNeatExperiment.generateInitialPopulation(
//        populationSize,
//        6,
//        1,
//        activationFunctions
//    ).mapIndexed { index, neatMutator ->
//        NetworkWithId(neatMutator, index)
//    }
    var mapIndexed = population.mapIndexed { index, neatMutator -> index to neatMutator }.toMap()
    var finishedScores = population.mapIndexed { index, neatMutator -> index to false }.toMap().toMutableMap()
//    createTaskNetwork(population.first().toNetwork())
    val distanceFunction = cppnGeneRuler::measure
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
        speciationController.speciate(population.map { it.neatMutator }, speciesLineage, generation)
    }
    var scores = mutableListOf<FitnessModel<NeatMutator>>()
    var seq = population.iterator()
    var activeModel : NetworkWithId = population.first()
    val knnNoveltyArchive = KNNNoveltyArchive<MarioInfo>(150, 0f) { a, b ->
        euclidean(toVector(a), toVector(b))
    }
    knnNoveltyArchive.behaviors.addAll(behaviors)

    fun processPopulation(populationEvolver: PopulationEvolver) {

        if (scores.size == populationSize) {
            logger.info { "New generation ${populationEvolver.generation}" }
            val toModelScores = scores.toModelScores(simulation.adjustedFitnessCalculation)
            population = evolve(populationEvolver, toModelScores, simpleNeatExperiment, population.size).mapIndexed { index, neatMutator ->
                NetworkWithId(neatMutator, UUID.randomUUID().toString())
            }
            mapIndexed = population.mapIndexed { index, neatMutator -> index to neatMutator }.toMap()
            finishedScores = population.mapIndexed { index, neatMutator -> index to false }.toMap().toMutableMap()

            seq = population.iterator()
            scores = mutableListOf()
            writeGenerationToDisk(population.map { it.neatMutator }, runFolder, populationEvolver, "")
            runFolder.resolve("${evaluationId}_noveltyArchive.json").bufferedWriter().use {
                val json = Json { prettyPrint = true }
                it.write(json.encodeToString(knnNoveltyArchive.behaviors))
                it.flush()
            }
        }
    }

    routing {
        get("/model") {
//            val a = activeModel
//            if (a != null) {
//                scores += FitnessModel(a, 0f)
//                activeModel = null
//                processPopulation(simulation.populationEvolver)
//            }
            if (!seq.hasNext()) {
                seq = finishedScores.filter { !it.value }.mapNotNull { mapIndexed[it.key] }.iterator()
            }
            if (seq.hasNext()) {
                val activeModel = seq.next()
                val message = createTaskNetwork(activeModel.neatMutator.toNetwork(), activeModel.id)
                call.respond(message)
            }
        }
        post<DeadNetwork>("/dead") {
            val a = mapIndexed[it.id]
            if (a != null) {
                scores += FitnessModel(a.neatMutator, 0f)
                finishedScores[it.id] = true

            }
            call.respond("ok")
        }
        post<MarioInfo>("/score") {
            val populationEvolver = simulation.populationEvolver
            val score = if (knnNoveltyArchive.size >0)
                knnNoveltyArchive.addBehavior(it)
            else {
                knnNoveltyArchive.addBehavior(it)
                euclidean(toVector(it), toVector(it).map { 0f})
            }
            val model = mapIndexed[it.id]!!.neatMutator
            if (finishedScores[it.id] != true) {
                scores += FitnessModel(model, score)
                finishedScores[it.id] = true
            }

            logger.info { "[G${populationEvolver.generation}] Model (${scores.size}) Score: $score " }
            logger.info { "$it" }
            processPopulation(populationEvolver)
        }
    }

}
data class DeadNetwork(val id : Int)
fun MarioInfo.statusAsNumber() = when (this.status) {
    "small" -> 0
    "tall" -> 1
    "fireball" -> 2
    else -> error("failed to handle status: $status")
}
data class NetworkWithId(val neatMutator: NeatMutator, val id : String)
fun toVector(marioInfo: MarioInfo) = listOf(
    marioInfo.x_pos / 50f,
    marioInfo.y_pos/ 8f,
    marioInfo.stage * 10f,
    marioInfo.world * 50f,
    marioInfo.statusAsNumber() * 5f,
    marioInfo.score / 100f
)//.map { it.toFloat() }
fun evolve(
    populationEvolver: PopulationEvolver,
    modelScores: List<ModelScore>,
    neatExperiment: NeatExperiment,
    populationSize: Int
): List<NeatMutator> {
    populationEvolver.sortPopulationByAdjustedScore(modelScores)
    populationEvolver.updateScores(modelScores)
    var newPopulation = populationEvolver.evolveNewPopulation(modelScores, neatExperiment)

    while (newPopulation.size < populationSize) {

        newPopulation = newPopulation + newPopulation.first().clone()
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

@Serializable
data class MarioInfo(
    val id: Int,
    val coins: Int,
    val flag_get: Boolean,
    val life: Int,
    val score: Int,
    val stage: Int,
    val status: String,
    val time: Int,
    val world: Int,
    val x_pos: Int,
    val y_pos: Int
)

fun Int.squared() = this * this

fun List<Int>.actionString() = map { it.toChar() }.joinToString("")
private fun Application.connectAndCreateDatabase() {
    launch {
        fun dbProp(propName: String) = environment.config.property("ktor.database.$propName")
        fun dbPropString(propName: String) = dbProp(propName).getString()

        Database.connect(
            url = dbPropString("url"),
            driver = dbPropString("driver"),
            user = dbPropString("user"),
            password = dbPropString("password")
        )
    }
}

fun evaluationContext(
    controllers: List<IOController>,
    evaluationId: Int
) = EvaluationContext(evaluationId, controllers.map { it.controllerId })

@Serializable
data class EvaluationContext(val evaluationId: Int, val controllers: List<Int>)

class EvaluationMessageProcessor(
    val evaluationChannels: EvaluationChannels,
    val inputChannel: ReceiveChannel<MarioData>,
    val messageWriter: MessageWriter
) {
    suspend fun processOutput(controller: IOController) {
        for (frameOutput in controller.frameOutputChannel) {
            messageWriter.sendAllMessage(
                BroadcastMessage("simulation.frame.output", frameOutput),
                MarioOutput.serializer()
            )
        }
    }

    suspend fun processPopulation() {
        for (frame in evaluationChannels.populationChannel) {
            messageWriter.sendPlayerMessage(
                userMessage = TypedUserMessage(
                    userRef = UserRef("dashboard"),
                    topic = "simulation.event.population.new",
                    data = frame
                ),
                serializer = PopulationModels.serializer()
            )

        }
    }

    suspend fun processAgentModel() {
        for (frame in evaluationChannels.agentModelChannel) {
            messageWriter.sendPlayerMessage(
                userMessage = TypedUserMessage(
                    userRef = UserRef("dashboard"),
                    topic = "simulation.event.agent.new",
                    data = frame
                ),
                serializer = AgentModel.serializer()
            )
        }
    }

    suspend fun processScores() {
        try {
            for (frame in evaluationChannels.scoreChannel) {
                messageWriter.sendPlayerMessage(
                    userMessage = TypedUserMessage(
                        userRef = UserRef("dashboard"),
                        topic = "simulation.event.score.new",
                        data = frame
                    ),
                    serializer = EvaluationScore.serializer()
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Score processor crashed..." }
        }
    }

    suspend fun processFrameData(frameUpdateChannels: List<IOController>) {
        for (frame in inputChannel) {
            //forward to evaluation and broadcast data to dashboard
            frameUpdateChannels.forEach { it.frameUpdateChannel.send(frame) }
//            evaluationChannels.player1.frameUpdateChannel.send(frame)
//            evaluationChannels.player2.frameUpdateChannel.send(frame)
            messageWriter.sendPlayerMessage(
                userMessage = TypedUserMessage(
                    userRef = UserRef("dashboard"),
                    topic = "simulation.event.frame.update",
                    data = frame
                ),
                serializer = MarioData.serializer()
            )

        }
    }
}

private fun Application.networkEvaluatorOutputBridgeLoop(
    evaluationMessageProcessor: EvaluationMessageProcessor,
    controllers: List<IOController>
) {

    controllers.forEach {
        launch { evaluationMessageProcessor.processOutput(it) }
    }
    launch { evaluationMessageProcessor.processFrameData(controllers) }
//    launch { evaluationMessageProcessor.processEvaluationClocks() }
    launch { evaluationMessageProcessor.processPopulation() }
    launch { evaluationMessageProcessor.processAgentModel() }
    launch { evaluationMessageProcessor.processScores() }
}

fun previewMessage(frame: Frame.Text): String {
    val readText = frame.readText()
    val frameLength = readText.length
    return when {
        frameLength < 101 -> readText
        else -> {
            val messagePreview = readText.take(100)
            "$messagePreview...\n[[[rest of message has been trimmed]]]"
        }
    }
}

@Serializable
data class Manifest(val scoreKeeperModel: SpeciesScoreKeeperModel, val scoreLineageModel: SpeciesLineageModel)

/*
launch(Dispatchers.IO) {
        var population = initialPopulation
        while (!receivedAnyMessages) {
            delay(100)
        }
        while (true) {
            launch(Dispatchers.IO) {
                val modelPopulationPersist = population.toModel()
                val savePopulationFile = runFolder.resolve("${populationEvolver.generation + 168}.json")
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
            val modelScores = evaluationArena.evaluatePopulation(population) { simulationFrame ->
                inAirFromKnockback(simulationFrame)
                opponentInAirFromKnockback(simulationFrame)
                processDamageDone(simulationFrame)
                processStockTaken(simulationFrame)
                processStockLoss(simulationFrame)
                if (simulationFrame.aiLoseGame) {
                    gameLostFlag = true
                    lastDamageDealt = 0f
                }
            }.toModelScores(adjustedFitness)
            populationEvolver.sortPopulationByAdjustedScore(modelScores)
            populationEvolver.updateScores(modelScores)
            var newPopulation = populationEvolver.evolveNewPopulation(modelScores)
            populationEvolver.speciate(newPopulation)
            while (newPopulation.size < population.size) {
                newPopulation = newPopulation + newPopulation.first().clone()
            }
            population = newPopulation
        }
    }
 */