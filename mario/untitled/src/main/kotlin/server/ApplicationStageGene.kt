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
import server.message.endpoints.NeatModel
import server.message.endpoints.toModel
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


@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.moduleStageGene(testing: Boolean = false) {
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
        })
    }
    install(ContentNegotiation) {
        json(get())
    }

    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = LocalDateTime.now().let { File("runs/run-${it.format(format)}") }
    runFolder.mkdirs()
//    get<WebSocketManager>().attachWSRoute()
    val evaluationId = 0
    val populationSize = 200
    val mutationDictionary = createMutationDictionary()
    fun createPopulation(randomSeed: Int): Pair<NeatExperiment, List<MCCElement<NeatMutator>>> {
        val random = Random(randomSeed)
        val addConnectionAttempts = 5
        val activationFunctions = Activation.CPPN.functions
        val simpleNeatExperiment = simpleNeatExperiment(random, 0, 0, activationFunctions, addConnectionAttempts, 7f)
        var population = simpleNeatExperiment.generateInitialPopulation2(
            populationSize, 6, 2, activationFunctions
        ).mapIndexed { index, neatMutator ->
            MCCElement(0, neatMutator)
        }
        return simpleNeatExperiment to population
    }

    fun createEnvironmentPopulation(neatExperiment: NeatExperiment): List<MCCElement<StageTrackGene>> {
        val population = (0 until populationSize).map {
            MCCElement(0, neatExperiment.mutateAddStage(StageTrackGene(listOf(), UUID.randomUUID().toString())))
        }
        return population
    }

    val (neatExperiment, population) = createPopulation(12) //loadModels(Random(15), Activation.CPPN.functions, 5, "population/population_1.json")//createPopulation(15)
    val environmentPopulation =
        createEnvironmentPopulation(neatExperiment) //loadModels(Random(16), Activation.CPPN.functions, 5, "population/population_2.json")//createPopulation(15)
    val envOffspringFunction = environmentOffSpringFunction(
        createStageTrackMutationDictionary(
            createStageMutationDictionary()
        )
    )
    val agentOffspringFunction = offspringFunctionMCC(.5f, mutationDictionary)
    val minimalCriterion = MinimalCriterionStage(
        Random(5),
        80,
        5,
        5,
        population,
        environmentPopulation,
        populationSize,
        1f
    )

    fun offspringFunction(minimalCriterion: MinimalCriterion) = when (minimalCriterion.activePopulation) {
        PopulationType.Agent -> agentOffspringFunction
        PopulationType.Environment -> envOffspringFunction
    }

    val mccBatchChannel = Channel<MCCStageBatch>()
    val mccBatchResultChannel = Channel<MCCStageBatchResult>()
    val mccResultChannel = Channel<MCCResult>()
    val pairedAgentsChannel = Channel<PairedAgentsStage>(Channel.UNLIMITED)
    var batchNumber = 1
    launch(Dispatchers.Default) {
        while (true) {

            logger.info { "MC Step ${minimalCriterion.activePopulation} Batch $batchNumber" }
            val mccBatch = when (minimalCriterion.activePopulation) {
                PopulationType.Environment -> minimalCriterion.stepEnvironment(
                    neatExperiment,
                    batchNumber,
                    envOffspringFunction
                )

                PopulationType.Agent -> minimalCriterion.stepAgent(
                    neatExperiment,
                    batchNumber,
                    agentOffspringFunction
                )
            }
            mccBatchChannel.send(mccBatch)
            val mccBatchResult = mccBatchResultChannel.receive()
            if (batchNumber % 10 ==0) {
                writeStageGenerationToDisk(minimalCriterion.environmentPopulationQueue.map { it.data }, runFolder, batchNumber, "environment_" )
                writeGenerationToDisk(minimalCriterion.agentPopulationQueue.map { it.data }, runFolder, batchNumber, "agent_" )
            }
            logger.info { "Process batch result: ${batchNumber} (${mccBatchResult.mccMap.values.filter { it }.size} / ${mccBatchResult.pairedAgents.size})" }
            when (mccBatchResult.batchPopulationType) {
                PopulationType.Agent -> minimalCriterion.processBatchAgent(mccBatchResult)
                PopulationType.Environment -> minimalCriterion.processBatchEnvironment(mccBatchResult)
            }
            minimalCriterion.togglePopulation()
            batchNumber += 1
        }
    }
    val mccBatchMap = mutableMapOf<String, Boolean>()
    val mccResultList = mutableListOf<MCCResult>()
    var currentMccBatch = MCCStageBatch(listOf(), PopulationType.Agent)
    launch {
        for (mccBatch in mccBatchChannel) {
            mccBatchMap.clear()
            mccResultList.clear()
            currentMccBatch = mccBatch
            logger.info { "New Batch ${batchNumber}" }
            mccBatch.pairedAgents.forEach {
                when(mccBatch.batchPopulationType) {
                    PopulationType.Environment -> mccBatchMap[it.environment.data.id] = false
                    PopulationType.Agent -> mccBatchMap[it.agent.data.id.toString()] = false
                }
                pairedAgentsChannel.send(it)
            }
        }
    }
    launch {
        var count = 0
        for (mccResult in mccResultChannel) {
//            logger.info { mccBatchMap.containsKey(mccResult.id) }
            if (mccResult.id in mccBatchMap) {
                count += 1
                if (mccResult.satisfyMC) {
                    mccBatchMap[mccResult.id] = true
                    mccResultList.add(mccResult)
                }
//            logger.info { "Score $mccResult" }
//            logger.info { "remaining: ${mccBatchMap.filter { !it.value }.size}" }
                if (mccBatchMap.all { it.value } || count == currentMccBatch.pairedAgents.size) {
                    val mccBatchResult = MCCStageBatchResult(
                        currentMccBatch.pairedAgents,
                        mccResultList.associate { it.id to it.satisfyMC },
                        currentMccBatch.batchPopulationType
                    )
                    mccBatchMap.clear()
                    count = 0
                    mccBatchResultChannel.send(mccBatchResult)
                }
            }
        }
    }


    val createNetwork = createNetwork()
    val connectionRelationships =
        createNetwork.connectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
    val targetConnectionMapping =
        createNetwork.targetConnectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
    val calculationOrder = createNetwork.calculationOrder.map { it.id }
    routing {
        get("/model") {

            var pairedAgents = pairedAgentsChannel.receive()
            while (!mccBatchMap.containsKey(id(pairedAgents) ) || mccResultList.any { it.id == id(pairedAgents) }) {
                mccResultChannel.send(MCCResult(id(pairedAgents), false))
                pairedAgents = pairedAgentsChannel.receive()
            }
            val blueprint = NetworkBlueprint(
            id(pairedAgents),
                createNetwork.planes,
                connectionRelationships,
                targetConnectionMapping,
                calculationOrder,
                0,
                0,
                createNetwork.outputPlane.id,
                pairedAgents.agent.data.toModel(),
                createNetwork.depth
            )
            call.respond(PairedHyperAgentEnvironment(blueprint, pairedAgents.environment.data, pairedAgents.type))
        }

        get("/fillModels") {
            val filter = when (currentMccBatch.batchPopulationType) {
                PopulationType.Environment -> currentMccBatch.pairedAgents.filter { !mccBatchMap.getValue(it.environment.data.id) }
                PopulationType.Agent -> currentMccBatch.pairedAgents.filter { !mccBatchMap.getValue(it.agent.data.id.toString()) }
            }
            filter.forEach {
                pairedAgentsChannel.send(it)
            }
            call.respond(filter.size)
        }

        post<MCCCheck>("/stageCheck") { check ->
            call.respond(MCCResult(check.id, mccResultList.any { it.id == check.id }))
        }

        post<MCCResult>("/score") {
            mccResultChannel.send(it)
        }
    }
}

private fun id(pairedAgents: PairedAgentsStage) = when (pairedAgents.type) {
    PopulationType.Environment -> pairedAgents.environment.data.id
    PopulationType.Agent -> pairedAgents.agent.data.id.toString()
}


fun environmentOffSpringFunction(mutationEntries: List<StageTrackMutationEntry>): NeatExperiment.(StageTrackGene) -> StageTrackGene {
    return {
        newOffspringStage(this, mutationEntries, it)
    }
}

fun newOffspringStage(
    neatExperiment: NeatExperiment,
    mutationEntries: List<StageTrackMutationEntry>,
    stageTrackGene: StageTrackGene
): StageTrackGene {
    var newStageTrack = stageTrackGene.copy()
    for (entry in mutationEntries) {
        if (entry.roll(neatExperiment)) {
            newStageTrack = entry.mutation(neatExperiment, newStageTrack)
        }
    }
    return newStageTrack
}