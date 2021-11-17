package server

import Auth0Config
import FrameOutput
import FrameUpdate
import MessageWriter
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.novelty.KNNNoveltyArchive
import neat.novelty.levenshtein
import neat.toNetwork
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.*
import org.koin.core.parameter.*
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import org.slf4j.event.Level
import server.database.*
import server.message.BroadcastMessage
import server.message.TypedUserMessage
import server.message.endpoints.*
import server.server.WebSocketManager
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val log = KotlinLogging.logger { }

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
    val controller1 = get<IOController>(parameters = { DefinitionParameters(listOf(0)) })
    val controller2 = get<IOController>(parameters = { DefinitionParameters(listOf(1)) })
    fun IOController.simulationForController(populationSize: Int) = get<Simulation>(parameters = {
        DefinitionParameters(
            listOf(controllerId, populationSize)
        )
    })
    val (initialPopulation, populationEvolver, adjustedFitness) = controller1.simulationForController(100)
    val (initialPopulation2, populationEvolver2, adjustedFitness2) = controller2.simulationForController(100)

    val evaluationChannels = get<EvaluationChannels>()
    val evaluationChannels2 = get<EvaluationChannels>()
    val evaluationMessageProcessor = get<EvaluationMessageProcessor>()
//
//    val a = Json { }.decodeFromString<List<ActionBehavior>>(
//        ListSerializer(ActionBehavior.serializer()),
//        File("population/0_noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
//    )
//    val b = Json { }.decodeFromString<List<ActionBehavior>>(
//        ListSerializer(ActionBehavior.serializer()),
//        File("population/1_noveltyArchive.json").bufferedReader().lineSequence().joinToString("")
//    )
    networkEvaluatorOutputBridgeLoop(evaluationMessageProcessor, listOf(controller1, controller2))
    val modelManager = ModelManager(mapOf(
        controller1 to EvolutionGeneration(0, mapOf(), listOf(), mutableMapOf()),
        controller2 to EvolutionGeneration(0, mapOf(), listOf(), mutableMapOf())
    ).toMutableMap())
    val sequenceSeparator = 2000.toChar()
    launch(Dispatchers.IO) {

        log.info("Start evaluation Loop!")
        evaluationLoopNoveltyHyperNeat(
            modelManager = modelManager,
            evaluationId = 0,
            initialPopulation = initialPopulation,
            populationEvolver = populationEvolver,
            adjustedFitnessCalculation = adjustedFitness,
            evaluationChannels = evaluationChannels,
            controller1,
            KNNNoveltyArchive<ActionBehavior>(60, 1f) { a, b ->
                val allActionDistance = levenshtein(a.allActions.actionString(), b.allActions.actionString())
                val damageDistance = levenshtein(a.damage.actionString(), b.damage.actionString())
                val killsDistance = levenshtein(a.kills.actionString(), b.kills.actionString())
                val recoveryDistance = levenshtein(
                    a.recovery.joinToString("$sequenceSeparator") { it.actionString() },
                    b.recovery.joinToString("$sequenceSeparator") { it.actionString() }
                )
                sqrt(
                    allActionDistance.times(3).squared() + killsDistance.times(30).squared() + damageDistance.times(2)
                        .squared() + recoveryDistance.times(10).squared().toFloat()
                )
            }
//                .also { it.behaviors.addAll(a) }
        )
    }
//
    launch(Dispatchers.IO) {

        log.info("Start evaluation Loop!")
        evaluationLoopNoveltyHyperNeat(
            modelManager = modelManager,
            evaluationId = 1,
            initialPopulation = initialPopulation2,
            populationEvolver = populationEvolver2,
            adjustedFitnessCalculation = adjustedFitness,
            evaluationChannels = evaluationChannels2,
            controller2,
            KNNNoveltyArchive<ActionBehavior>(12, 2f) { a, b ->
                val allActionDistance = levenshtein(a.allActions.actionString(), b.allActions.actionString())
                val damageDistance = levenshtein(a.damage.actionString(), b.damage.actionString())
                val killsDistance = levenshtein(a.kills.actionString(), b.kills.actionString())
                val recoveryDistance = levenshtein(
                    a.recovery.joinToString("$sequenceSeparator") { it.actionString() },
                    b.recovery.joinToString("$sequenceSeparator") { it.actionString() }
                )
                sqrt(
                    allActionDistance.times(3).squared() + killsDistance.times(30).squared() + damageDistance.times(2)
                        .squared() + recoveryDistance.times(10).squared().toFloat()
                )
            }
//                .also { it.behaviors.addAll(b) }
        )
    }
    val json = get<Json>()
    fun fromId(controllerId: Int) : IOController = when(controllerId) {
        0 -> controller1
        1 -> controller2
        else -> throw Exception()
    }
    routing {
        post<ActiveModelRequest>("/model/active") {
            call.respond(ModelRequest(it.controllerId, modelManager[fromId(it.controllerId)].activeId ?: ""))
        }
        post<ModelRequest>("/model") {
            val evolutionGeneration = modelManager[fromId(it.controllerId)]
            server.log.info { it.modelId }
//            evolutionGeneration.readiedNetworkMap[networkDescription.id] = networkDescription
            val taskNetwork = when {
                it.modelId.isEmpty() -> {
                    val networkWithId = evolutionGeneration.networkList.first()
                    evolutionGeneration.networkList[1].let {
                        val createTaskNetwork = createTaskNetwork(it.neatMutator.toNetwork(), it.id)
                        evolutionGeneration.readiedNetworkMap[createTaskNetwork.id] = createTaskNetwork
                        createTaskNetwork
                    }
                    createTaskNetwork(networkWithId.neatMutator.toNetwork(), networkWithId.id)
                }
                evolutionGeneration.readiedNetworkMap.containsKey(it.modelId) -> {
                    val indexOfFirst = evolutionGeneration.networkList.indexOfFirst { n -> it.modelId == n.id }
                    launch(Dispatchers.IO) {
                        if (indexOfFirst + 1 < evolutionGeneration.networkList.size) {
                            val networkWithId = evolutionGeneration.networkList[indexOfFirst + 1]
                            if (!evolutionGeneration.readiedNetworkMap.containsKey(networkWithId.id)) {
                                val createTaskNetwork = createTaskNetwork(networkWithId.neatMutator.toNetwork(), networkWithId.id)
                                evolutionGeneration.readiedNetworkMap[createTaskNetwork.id] = createTaskNetwork
                            }
                        }
                    }
                    val description = evolutionGeneration.readiedNetworkMap[it.modelId]!!
                    description
                }
                else -> {
                    val indexOfFirst = evolutionGeneration.networkList.indexOfFirst { n -> it.modelId == n.id }
                    server.log.info { "else statement $indexOfFirst" }
                    launch(Dispatchers.IO) {
                        if (indexOfFirst + 1 < evolutionGeneration.networkList.size)
                            evolutionGeneration.networkList[indexOfFirst + 1].let {
                                val createTaskNetwork = createTaskNetwork(it.neatMutator.toNetwork(), it.id)
                                server.log.info { "Created task network for next request" }
                                evolutionGeneration.readiedNetworkMap[createTaskNetwork.id] = createTaskNetwork
                                server.log.info { "set task network ${createTaskNetwork.id}" }
                                createTaskNetwork
                            }
                    }
                    val networkWithId = evolutionGeneration.networkMap[it.modelId]!!
                    createTaskNetwork(networkWithId.neatMutator.toNetwork(), networkWithId.id)
                }
            }

            call.respond(taskNetwork)
        }
    }
}
@Serializable
data class ActiveModelRequest(val controllerId: Int)

@Serializable
data class ModelRequest(val controllerId : Int, val modelId : String)
//data class Model(val )
class ModelManager(var controllerModelManagers : MutableMap<IOController, EvolutionGeneration>, val channel : Channel<NetworkDescription> = Channel<NetworkDescription>(5)) {
    operator fun set(ioController: IOController, evolutionGeneration: EvolutionGeneration) {
        controllerModelManagers[ioController] = evolutionGeneration
    }
    operator fun get(ioController: IOController) : EvolutionGeneration {
        return controllerModelManagers.getValue(ioController)
    }
}
data class EvolutionGeneration(val generation: Int, var networkMap : Map<String, NetworkWithId>, var networkList : List<NetworkWithId>, var readiedNetworkMap: MutableMap<String, NetworkDescription>, var activeId: String? = null)
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
        newSuspendedTransaction {
            SchemaUtils.createMissingTablesAndColumns(
                *DATABASE_TABLES.toTypedArray()
            )
        }
    }
}

fun evaluationContext(
    controllers: List<IOController>,
    evaluationId: Int
) = EvaluationContext(evaluationId, controllers.map { it.controllerId })

@Serializable
data class EvaluationContext(val evaluationId: Int, val controllers: List<Int>)
data class Controllers(val controllerList: List<IOController>)

private fun Application.generateFakeData(evaluationChannels: EvaluationChannels) {
    launch {
        while (true) {
            val element = FrameOutput(
                0,
                Random.nextBoolean(),
                Random.nextBoolean(),
                Random.nextBoolean(),
                Random.nextBoolean(),
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat(),
            )
            log.info("$element")
//            evaluationChannels.frameOutputChannel.send(
//                element
//            )
//            evaluationChannels.scoreChannel.send(EvaluationScore(-1, Random.nextFloat() * 1000, listOf()))
            delay(1000)
        }
    }
}

class EvaluationMessageProcessor(
    val evaluationChannels: EvaluationChannels,
    val inputChannel: ReceiveChannel<FrameUpdate>,
    val messageWriter: MessageWriter
) {
    suspend fun processOutput(controller: IOController) {
        for (frameOutput in controller.modelChannel) {
            messageWriter.sendAllMessage(
                BroadcastMessage("simulation.frame.output", frameOutput),
                ModelUpdate.serializer()
            )

        }
    }

    suspend fun processEvaluationClocks() {
        for (frame in evaluationChannels.clockChannel) {
            messageWriter.sendPlayerMessage(
                userMessage = TypedUserMessage(
                    userRef = UserRef("dashboard"),
                    topic = "simulation.event.clock.update",
                    data = frame
                ),
                serializer = EvaluationClocksUpdate.serializer()
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
            log.error(e) { "Score processor crashed..." }
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
                serializer = FrameUpdate.serializer()
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

fun SimulationState.opponentInAirFromKnockback(simulationFrameData: SimulationFrameData) {
    if (!opponentInAirFromKnockBack)
        opponentInAirFromKnockBack = !simulationFrameData.opponentOnGround && prevTookDamage
    if (simulationFrameData.opponentOnGround)
        opponentInAirFromKnockBack = false
}

fun SimulationState.inAirFromKnockback(simulationFrameData: SimulationFrameData) {
    if (!inAirFromKnockBack)
        inAirFromKnockBack = !simulationFrameData.aiOnGround && prevTookDamage
    if (simulationFrameData.aiOnGround)
        inAirFromKnockBack = false
    if (distanceTime != null && Duration.between(distanceTime, simulationFrameData.now).seconds > distanceTimeGain) {
        distanceTime = null
        distanceTimeGain = 0f
    }
    if (distanceTime != null && simulationFrameData.distance > linearTimeGainDistanceEnd) {
        distanceTime = null
        distanceTimeGain = 0f
    }
}

fun SimulationState.processDamageDone(simulationFrameData: SimulationFrameData) {
//    log.info { simulationFrameData.damageDoneFrame }
    if (simulationFrameData.aiDamageDoneFrame > 0) {
        damageDoneTime = simulationFrameData.now

        val damage = if (gameLostFlag) 0f.also {
            gameLostFlag = false
        } else simulationFrameData.aiDamageDoneFrame

        val secondsAiPlay = Duration.between(agentStart, simulationFrameData.now).seconds
        log.info { "Damage at $secondsAiPlay seconds" }
        log.info {
            "DamageAccumulation: $cumulativeDamageDealt + $damage -> (${cumulativeDamageDealt + damage})"
        }
        currentStockDamageDealt += damage
        cumulativeDamageDealt += damage
        if (secondsAiPlay < 0) {
            bonusGraceDamageApplied = true
            val gracePeriodHitBonus = gracePeriodHitBonus(
                t = secondsAiPlay.toFloat(),
                gracePeriod = 2f,
                bonus = 10
            )
            log.info { "Grace period damage bonus: $cumulativeDamageDealt + $gracePeriodHitBonus -> (${cumulativeDamageDealt + gracePeriodHitBonus})" }
            cumulativeDamageDealt += gracePeriodHitBonus
        }
    }
}

fun SimulationState.processStockTaken(simulationFrameData: SimulationFrameData) {
    if (simulationFrameData.aiTookStock) {
        if (lastOpponentPercent < 100f && currentStockDamageDealt > 0) {
            val earlyKillBonus =
                (((100f - lastOpponentPercent) / max(1f, currentStockDamageDealt)) * 12)
            log.info {
                """
                        PercentCap: 100
                        LastEnemyPercent: $lastOpponentPercent
                        currentDamageDealt: $currentStockDamageDealt
                        Early Kill: $cumulativeDamageDealt + $earlyKillBonus -> (${cumulativeDamageDealt + earlyKillBonus})
                    """.trimIndent()
            }
            cumulativeDamageDealt += earlyKillBonus
            earlyKillBonusApplied = true
        } else log.info { "Kill but percent was over 100." }
        val doubledDamage = cumulativeDamageDealt * 2
        log.info {
            """
                            Stock taken: $lastOpponentStock -> ${simulationFrameData.opponentStockFrame} (${simulationFrameData.aiTookStock})
                            Damage: $lastDamageDealt -> ${simulationFrameData.aiDamageDoneFrame}
                            CumulativeDamage: $cumulativeDamageDealt -> $doubledDamage
                        """.trimIndent()
        }

        cumulativeDamageDealt = doubledDamage
        stockTakenTime = simulationFrameData.now

    }
}

fun SimulationState.processStockLoss(simulationFrameData: SimulationFrameData) {
    if (lastAiStock != simulationFrameData.aiStockCount) {
        log.info { "Stocks not equal! $lastAiStock -> ${simulationFrameData.aiStockCount}" }
    }
    if (simulationFrameData.aiLostStock)
        log.info { "Stock was lost: $cumulativeDamageDealt > 0 (${cumulativeDamageDealt > 0}) - ${simulationFrameData.aiLostStock} - ${simulationFrameData.aiStockLostButNotGame}" }
    if (simulationFrameData.aiLostStock && cumulativeDamageDealt > 0) {
        damageTimeFrame -= .25f
        timeGainMax -= 2f
        val sqrt = if (inAirFromKnockBack) (cumulativeDamageDealt) / 8 else sqrt(cumulativeDamageDealt)
        log.info {
            """
                diedFromKnockBack: $inAirFromKnockBack
                Stock Lost: $lastAiStock -> ${simulationFrameData.aiStockCount} (${simulationFrameData.aiStockLostButNotGame})
                CumulativeDamage: $cumulativeDamageDealt -> $sqrt
            """.trimIndent()
        }
        if (stockLostTime != null && Duration.between(simulationFrameData.now, stockLostTime).seconds < 4) {
            log.info { "Double quick death... be gone" }
            doubleDeathQuick = true
        }
        stockLostTime = simulationFrameData.now
        cumulativeDamageDealt = sqrt
        currentStockDamageDealt = 0f
    }
}

fun gracePeriodHitBonus(t: Float, gracePeriod: Float, bonus: Int = 20): Float {
    return bonus * (1 - (t / gracePeriod))
}

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