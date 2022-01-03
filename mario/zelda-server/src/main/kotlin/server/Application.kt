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
import io.ktor.util.date.*
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
val minSpeices = 15
val maxSpecies = 20
val speciesThresholdDelta = .1f
val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = 2f, disjointCoefficient = 1f)
var distanceFunction = cppnGeneRuler::measure
var speciesSharingDistance = 2f
var shFunction = shFunction(speciesSharingDistance)

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
    val populationSize = 500



    val mateChance = .2f
    val survivalThreshold = .2f
    val stagnation = 15

    val randomSeed: Int = 2200 + evaluationId
    val addConnectionAttempts = 5
    val activationFunctions = Activation.CPPN.functions
    val random = Random(randomSeed)

//
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
//    val behaviors = Json { }.decodeFromString<List<MarioInfo>>(
//        File("population/noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
//    )
    var settings = Settings(2f)
    val simpleNeatExperiment =
        simpleNeatExperiment(random, 0, 0, activationFunctions, addConnectionAttempts)
//    var modelIndex = 0
    var population = simpleNeatExperiment.generateInitialPopulation(
        populationSize,
        6,
        1,
        activationFunctions
    ).mapIndexed { index, neatMutator ->
        NetworkWithId(neatMutator, UUID.randomUUID().toString())
    }
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
        speciationController.speciate(population.map { it.neatMutator }, speciesLineage, generation, standardCompatibilityTest(shFunction, distanceFunction))
    }
    var scores = mutableListOf<FitnessModel<NeatMutator>>()
    var seq = population.iterator()
    var activeModel: NetworkWithId = population.first()
    val knnNoveltyArchive = KNNNoveltyArchive<ZeldaInfo>(100, settings.noveltyThreshold) { a, b ->
        euclidean(a.toVector(), b.toVector())
    }
//    knnNoveltyArchive.behaviors.addAll(behaviors)

    fun processPopulation(populationEvolver: PopulationEvolver) {

        if (scores.size == populationSize) {
            logger.info { "New generation ${populationEvolver.generation}" }
            val toModelScores = scores.toModelScores(adjustedFitnessCalculation(simulation.populationEvolver.speciationController, distanceFunction, shFunction))
            population = evolve(
                populationEvolver,
                toModelScores,
                simpleNeatExperiment,
                population.size
            ).mapIndexed { index, neatMutator ->
                NetworkWithId(neatMutator, UUID.randomUUID().toString())
            }.shuffled()
            mapIndexed = population.mapIndexed { index, neatMutator -> neatMutator.id to neatMutator }.toMap()
            finishedScores =
                population.mapIndexed { index, neatMutator -> neatMutator.id to false }.toMap().toMutableMap()

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
    val modelChannel = Channel<NetworkDescription>(40)
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
    repeat(5) {
        launch(Dispatchers.IO) {
            while (true) {
                val network = neatMutatorChannel.receive()
                val message = createTaskNetwork(network.neatMutator.toNetwork(), network.id)
//                logger.info { "added model... ${message.id}" }
                modelChannel.send(message)
            }
        }
    }
    val scoreChannel = Channel<ZeldaInfo>(Channel.UNLIMITED)
    routing {
        get("/model") {
            val model = modelChannel.receive()

            call.respond(model)
        }
        post<DeadNetwork>("/dead") {
            val a = mapIndexed[it.id]
            if (a != null) {
                scores += FitnessModel(a.neatMutator, 0f)
                finishedScores[it.id] = true

            }
            call.respond("ok")
        }
        post<ZeldaInfo>("/score") {


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
    }
    launch(Dispatchers.IO) {
        for (it in scoreChannel) {
            val populationEvolver = simulation.populationEvolver
            val score = if (knnNoveltyArchive.size > 0) {
                val addBehavior = knnNoveltyArchive.addBehavior(it)
                if (addBehavior < knnNoveltyArchive.noveltyThreshold) 0f else addBehavior // + it.dstage * 100  + it.dworld * 500 + it.x_pos / 128
            } else {
                knnNoveltyArchive.addBehavior(it)
//                euclidean(toVector(it), toVector(it).map { 0f})
                0f
            }
            val model = mapIndexed[it.id]?.neatMutator
            if (finishedScores[it.id] != true && model != null) {
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
@Serializable
data class Settings(val noveltyThreshold: Float)
data class DeadNetwork(val id: String)


data class NetworkWithId(val neatMutator: NeatMutator, val id: String)

fun ZeldaInfo.toVector() = listOf<Float>(
//hearts,
//    this.


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
    if (populationEvolver.speciationController.speciesSet.size < minSpeices) {
        if (speciesSharingDistance > speciesThresholdDelta) {
            speciesSharingDistance -= speciesThresholdDelta
        }
    }
    else if (populationEvolver.speciationController.speciesSet.size > maxSpecies) {
        speciesSharingDistance += speciesThresholdDelta
    }
    logger.info { "Species (${populationEvolver.speciationController.speciesSet.size}) Sharing Function Distance: $speciesSharingDistance" }
    shFunction = neat.shFunction(speciesSharingDistance)
    populationEvolver.speciate(newPopulation, standardCompatibilityTest(shFunction, distanceFunction))
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
//
@Serializable
data class ZeldaInfo(
    val id: String,
    val currentLevel : Int,
    val xPos : Int,
    val yPos : Int,
    val direction : String,
    val hasCandled : Boolean,
    val pulse1 : String,
    val pulse2 : String,
    val killedEnemies : Int,
    val numberOfDeaths : Int,
    val sword : String,
    val numberOfBombs : Int,
    val arrowsType : String,
    val hasBow : Boolean,
    val candleType : String,
    val hasWhistle : Boolean,
    val hasFood : Boolean,
    val potionType : String,
    val hasMagicRod : Boolean,
    val hasRaft : Boolean,
    val hasMagicBook : Boolean,
    val ringType : String,
    val hasStepLadder : Boolean,
    val hasMagicKey : Boolean,
    val hasPowerBracelet : Boolean,
    val hasLetter : Boolean,
    val isClockPossessed : Boolean,
    val rupees : Int,
    val keys : Int,
    val heartContainers : Int,
    val hearts : Float,
    val hasBoomerang : Boolean,
    val hasMagicBoomerang : Boolean,
    val hasMagicShield : Boolean,
    val maxNumberOfBombs : Int
)
//
//@Serializable
//data class MarioInfo(
//    val id: String,
//    val coins: Int,
//    val flag_get: Boolean,
//    val life: Int,
//    val score: Int,
//    val stage: Int,
//    val status: String,
//    val time: Int,
//    val world: Int,
//    val x_pos: Int,
//    val y_pos: Int,
//    val dstatus: Int,
//    val dx: Int,
//    val dy: Int,
//    val dtime: Int,
//    val dstage: Int,
//    val dworld: Int,
//    val dlife: Int,
//    val dscore: Int,
//    val dcoins: Int,
//
//)

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