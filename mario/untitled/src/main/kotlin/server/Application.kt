package server

import Auth0Config
import createMutationDictionary
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
import kotlinx.serialization.json.*
import mu.*
import neat.*
import neat.model.*
import org.koin.ktor.ext.*
import server.mcc.*
import server.server.*
import java.io.*
import java.time.*
import java.time.format.*
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.random.Random


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val logger = KotlinLogging.logger { }


fun compatibilityDistanceFunction(c1: Float, c2: Float, c3: Float): DistanceFunction = { a, b ->
    compatibilityDistance(a, b, c1, c2, c3)
}

class CPPNGeneRuler(val weightCoefficient: Float = .5f, val disjointCoefficient: Float = 1f, val normalize: Int) {
    fun measure(parent1: NeatMutator, parent2: NeatMutator): Float {
        return nodeDistance(parent1, parent2) + connectionDistance(parent1, parent2)
    }

    private fun connectionDistance(parent1: NeatMutator, parent2: NeatMutator): Float {
        val connectionDisjoint =
            parent1.connections.count { !parent2.hasConnection(it.innovation) } + parent2.connections.count {
                !parent1.hasConnection(
                    it.innovation
                )
            }
        val connectionDistance = parent2.connections.filter { parent1.hasConnection(it.innovation) }
            .map { (it.weight - parent2.connection(it.innovation).weight).absoluteValue * weightCoefficient }.sum()
        val max = max(
            parent1.connections.size, parent2.connections.size
        )
        return (connectionDistance + connectionDisjoint * disjointCoefficient) / (if (max < normalize) 1 else max)
    }

    private fun nodeDistance(parent1: NeatMutator, parent2: NeatMutator): Float {
        val nodeDisjoint =
            parent1.nodes.count { !parent2.hasNode(it.node) } + parent2.nodes.count { !parent1.hasNode(it.node) }
        val nodeDistance = 0f + parent2.nodes.filter { parent1.hasNode(it.node) }.map {
            val node = parent1.node(it.node)
            val activationFunctionDistance = if (node.activationFunction == it.activationFunction) 0f else 1f
            val biasDistance = (it.bias - node.bias).absoluteValue
            val distance = biasDistance + activationFunctionDistance
            distance * weightCoefficient
        }.sum()
        val max = max(parent1.nodes.size, parent2.nodes.size)
        return (nodeDistance + nodeDisjoint * disjointCoefficient) / (if (max < normalize) 1 else max)
    }


}


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
    val evaluationId = 0
    val populationSize = 50
    val mutationDictionary = createMutationDictionary()
    fun createPopulation(randomSeed: Int): Pair<NeatExperiment, List<NetworkWithId>> {
        val random = Random(randomSeed)
        val addConnectionAttempts = 5
        val activationFunctions = Activation.CPPN.functions
        val simpleNeatExperiment = simpleNeatExperiment(random, 0, 0, activationFunctions, addConnectionAttempts)
        var population = simpleNeatExperiment.generateInitialPopulation2(
            populationSize, 6, 2, activationFunctions
        ).mapIndexed { index, neatMutator ->
            NetworkWithId(neatMutator, UUID.randomUUID().toString(), 0)
        }
        return simpleNeatExperiment to population
    }

    val (neatExperiment, population) = loadModels(Random(15), Activation.CPPN.functions, 5, "population/population_1.json")//createPopulation(15)
    val (neatExperiment2, population2) = loadModels(Random(16), Activation.CPPN.functions, 5, "population/population_2.json")//createPopulation(15)
    val envOffspringFunction = offspringFunction(.5f, mutationDictionary)
    val agentOffspringFunction = offspringFunction(.5f, mutationDictionary)
    val minimalCriterion = MinimalCriterion(
        Random(1), 40, 10, 5, population, population, 100
    )

    fun neatExperiment(minimalCriterion: MinimalCriterion) = when (minimalCriterion.activePopulation) {
        PopulationType.Agent -> neatExperiment
        PopulationType.Environment -> neatExperiment2
    }

    fun offspringFunction(minimalCriterion: MinimalCriterion) = when (minimalCriterion.activePopulation) {
        PopulationType.Agent -> agentOffspringFunction
        PopulationType.Environment -> envOffspringFunction
    }

    val mccBatchChannel = Channel<MCCBatch>()
    val mccBatchResultChannel = Channel<MCCBatchResult>()
    val mccResultChannel = Channel<MCCResult>()
    val pairedAgentsChannel = Channel<PairedAgents>(Channel.UNLIMITED)
    var batchNumber = 1
    launch(Dispatchers.Default) {
        while (true) {

            logger.info { "MC Step ${minimalCriterion.activePopulation} Batch $batchNumber" }
            val mccBatch = minimalCriterion.step(neatExperiment(minimalCriterion), batchNumber, offspringFunction(minimalCriterion))
            mccBatchChannel.send(mccBatch)
            val mccBatchResult = mccBatchResultChannel.receive()
            if (batchNumber % 10 ==0) {
                writeGenerationToDisk(minimalCriterion.environmentPopulationQueue.map { it.neatMutator }, runFolder, batchNumber, "environment_" )
                writeGenerationToDisk(minimalCriterion.agentPopulationQueue.map { it.neatMutator }, runFolder, batchNumber, "agent_" )
            }
            logger.info { "Process batch result: ${batchNumber} (${mccBatchResult.mccMap.values.filter { it }.size} / ${mccBatchResult.pairedAgents.size})" }
            minimalCriterion.processBatch(mccBatchResult)
            minimalCriterion.togglePopulation()
            batchNumber+=1
        }
    }
    val mccBatchMap = mutableMapOf<String, Boolean>()
    val mccResultList = mutableListOf<MCCResult>()
    var currentMccBatch = MCCBatch(listOf(), PopulationType.Agent)
    launch {
        for (mccBatch in mccBatchChannel) {
            mccBatchMap.clear()
            mccResultList.clear()
            currentMccBatch = mccBatch
            logger.info { "New Batch ${batchNumber}" }
            mccBatch.pairedAgents.forEach {
                pairedAgentsChannel.send(it)
                mccBatchMap[it.child.id] = false
            }
        }
    }
    launch {
        for (mccResult in mccResultChannel) {
            mccBatchMap[mccResult.id] = true
            mccResultList.add(mccResult)
            if (mccBatchMap.all { it.value }) {
                val mccBatchResult = MCCBatchResult(
                    currentMccBatch.pairedAgents,
                    mccResultList.map { it.id to it.satisfyMC }.toMap(),
                    currentMccBatch.batchPopulationType
                )
                mccBatchResultChannel.send(mccBatchResult)
            }
        }
    }
    val taskNetworkBuilder = createNetwork()
    val networkBlueprintBuilder = NetworkBlueprintBuilder(taskNetworkBuilder)

    routing {
        get("/model") {
            val pairedAgents = pairedAgentsChannel.receive()
            val childConnections = taskNetworkBuilder.build(pairedAgents.child.neatMutator.toNetwork(), 3f)
            val agentConnections = taskNetworkBuilder.build(pairedAgents.agent.neatMutator.toNetwork(), 3f)
            val childNetworkBlueprint = networkBlueprintBuilder.networkBlueprint(childConnections, pairedAgents)
            val agentNetworkBlueprint = networkBlueprintBuilder.networkBlueprint(agentConnections, pairedAgents)
            val pairedNetworkBlueprints = PairedNetworkBlueprints(childNetworkBlueprint, agentNetworkBlueprint)
            call.respond(pairedNetworkBlueprints)
        }

        get("/fillModels") {
            currentMccBatch.pairedAgents.filter { !mccBatchMap.getValue(it.child.id) }.forEach {
                pairedAgentsChannel.send(it)
            }
        }

        post<MCCResult>("/score") {
            mccResultChannel.send(it)
        }
    }
}

@Serializable
data class PairedNetworkBlueprints(val child: NetworkBlueprint, val agent: NetworkBlueprint)

class NetworkBlueprintBuilder(private val taskNetworkBuilder: TaskNetworkBuilder) {
    private val connectionRelationships =
        taskNetworkBuilder.connectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
    private val targetConnectionMapping =
        taskNetworkBuilder.targetConnectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
    private val calculationOrder = taskNetworkBuilder.calculationOrder.map { it.id }

    fun networkBlueprint(
        connections: List<ConnectionLocation>, pairedAgents: PairedAgents
    ) = NetworkBlueprint(
        connections,
        pairedAgents.child.id,
        taskNetworkBuilder.planes,
        connectionRelationships,
        targetConnectionMapping,
        calculationOrder,
        0,
        0,
        taskNetworkBuilder.outputPlane.id
    )
}

@Serializable
data class MCCResult(val id: String, val satisfyMC: Boolean)
data class LoadedPopulation(val populationModels: List<NetworkWithId>, val simpleNeatExperiment: NeatExperiment)

fun loadModels(random: Random, activationFunctions: List<ActivationGene>, addConnectionAttempts: Int, path: String): Pair<NeatExperiment, List<NetworkWithId>> {

    val models = loadPopulation(File(path), 0).models
    logger.info { "population loaded with size of: ${models.size}" }
    val maxNodeInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it } + 1
    val maxInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it } + 1
    val simpleNeatExperiment = simpleNeatExperiment(
        random, maxInnovation, maxNodeInnovation, activationFunctions,
        addConnectionAttempts
    )
    var population = models.map { it.toNeatMutator() }.mapIndexed { index, neatMutator ->
        NetworkWithId(neatMutator, UUID.randomUUID().toString(), 0)
    }
    return simpleNeatExperiment to population
}