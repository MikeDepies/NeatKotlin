package server

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import mu.*
import neat.*
import neat.model.*
import org.koin.ktor.ext.*
import server.mcc.*
import server.mcc.smash.PopulationType
import server.message.endpoints.NeatModel
import server.message.endpoints.toModel
import java.io.*
import java.time.*
import java.time.format.*
import java.util.*
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
            MCCElement(-1, neatMutator)
        }
        return simpleNeatExperiment to population
    }

    fun createEnvironmentPopulation(neatExperiment: NeatExperiment): List<MCCElement<StageTrackGene>> {
        val population = (0 until populationSize).map {
            MCCElement(
                0, neatExperiment.mutateAddStage(
                    StageTrackGene(listOf(), UUID.randomUUID().toString()),
                    400
                )
            )
        }
        return population
    }
//createPopulation(12)

    val (neatExperiment, population) = loadModelsMCC(Random(12), "population/agent.json")
    //createPopulation(112) /**/
    val environmentPopulation = loadModelsMCCStage("population/environment.json")
    //createEnvironmentPopulation(neatExperiment)//loadModelsMCCStage("population/environment.json")
//        createEnvironmentPopulation(neatExperiment) //loadModels(Random(16), Activation.CPPN.functions, 5, "population/population_2.json")//createPopulation(15)
    val envOffspringFunction = environmentOffSpringFunction(
        createStageTrackMutationDictionary(
            createStageMutationDictionary()
        )
    )
    val agentOffspringFunction = offspringFunctionMCC(.1f, mutationDictionary)
    val minimalCriterion = MinimalCriterionStage(
        Random(5),
        100,
        10,
        5,
        population,
        environmentPopulation,
        populationSize,
        1f,
        .1f
    )

    fun offspringFunction(minimalCriterion: MinimalCriterion) = when (minimalCriterion.activePopulation) {
        PopulationType.Agent -> agentOffspringFunction
        PopulationType.Environment -> envOffspringFunction
    }

    val mccBatchChannel = Channel<MCCStageBatch>()
    val mccBatchResultChannel = Channel<MCCStageBatchResult>()
    val mccResultChannel = Channel<MCCResult>()
    val pairedAgentsChannel = Channel<PairedAgentsStage>(Channel.UNLIMITED)
    var batchNumber = 843
    var initialization = false
    launch(Dispatchers.Default) {
        var agentBatchReceived = true
        var environmentBatchReceived = true
        while (true) {

            if (agentBatchReceived && environmentBatchReceived) {
                logger.info { "MC Step Batch $batchNumber" }
                if (initialization) {
                    initialization = minimalCriterion.agentPopulationQueue.any { it.age < 0 }
                    if (!initialization) minimalCriterion.agentSampleSize = .1f
                }
                if (!initialization) {
                    val mccBatch = minimalCriterion.stepEnvironment(
                        neatExperiment,
                        batchNumber,
                        envOffspringFunction
                    )
                    mccBatchChannel.send(mccBatch)
                    environmentBatchReceived = false
                }

                val agentBatch = minimalCriterion.stepAgent(
                    neatExperiment,
                    batchNumber,
                    agentOffspringFunction
                )
                agentBatchReceived = false



                mccBatchChannel.send(agentBatch)
            }
            val mccBatchResult = mccBatchResultChannel.receive()
//            if (batchNumber % 10 ==0) {

//            }
            logger.info { "Process batch result: ${batchNumber} (${mccBatchResult.mccMap.values.filter { it }.size} / ${mccBatchResult.pairedAgents.size})" }
            when (mccBatchResult.batchPopulationType) {
                PopulationType.Agent -> {
                    minimalCriterion.processBatchAgent(mccBatchResult)
                    agentBatchReceived = true
                }

                PopulationType.Environment -> {
                    minimalCriterion.processBatchEnvironment(mccBatchResult)
                    environmentBatchReceived = true
                }
            }
            if (agentBatchReceived && environmentBatchReceived) {
                if (!initialization) {
                    writeStageGenerationToDisk(
                        minimalCriterion.environmentPopulationQueue.map { it.data },
                        runFolder,
                        batchNumber,
                        "environment_"
                    )
                    writeGenerationToDisk(
                        minimalCriterion.agentPopulationQueue.map { it.data },
                        runFolder,
                        batchNumber,
                        "agent_"
                    )
                    writeResourceUsageGenerationToDisk(
                        ResourceUsage(minimalCriterion.environmentPopulationResourceMap),
                        runFolder,
                        batchNumber
                    )

                    batchNumber += 1
                }

            }
        }
    }
//    val mccBatchMap = mutableMapOf<String, Boolean>()
    val agentMccBatchMap = mutableMapOf<String, Boolean>()
    val agentMccBatchDeadMap = mutableMapOf<String, Int>()
    val environmentMccBatchMap = mutableMapOf<String, Boolean>()
//    val mccResultList = mutableListOf<MCCResult>()
    val agentMccResultList = mutableListOf<MCCResult>()
    val environmentMccResultList = mutableListOf<MCCResult>()
//    var currentMccBatch = MCCStageBatch(listOf(), PopulationType.Agent)
    var agentMccBatch = MCCStageBatch(listOf(), PopulationType.Agent)
    var environmentMccBatch = MCCStageBatch(listOf(), PopulationType.Environment)
    launch {
        for (mccBatch in mccBatchChannel) {
//            logger.info { mccBatch.batchPopulationType }
            when (mccBatch.batchPopulationType) {
                PopulationType.Agent -> {
                    agentMccBatchMap.clear()
                    agentMccBatchDeadMap.clear()
                    agentMccResultList.clear()
                    agentMccBatch = mccBatch
                    mccBatch.pairedAgents.shuffled().forEach {
//                        logger.info { mccBatch.pairedAgents.size }
                        agentMccBatchMap[it.agent.data.id.toString()] = false
                        agentMccBatchDeadMap[it.agent.data.id.toString()] = 0
                        pairedAgentsChannel.send(it)
                    }
                }

                PopulationType.Environment -> {
                    environmentMccBatchMap.clear()
                    environmentMccResultList.clear()
                    environmentMccBatch = mccBatch
                    mccBatch.pairedAgents.shuffled().forEach {
//                        logger.info { mccBatch.pairedAgents.size }
                        environmentMccBatchMap[it.environment.data.id] = false
                        pairedAgentsChannel.send(it)
                    }
                }
            }
//            mccResultList.clear()
//            currentMccBatch = mccBatch
//            when (mccBatch.batchPopulationType) {
//                PopulationType.Environment -> environmentMccBatch = mccBatch
//                PopulationType.Agent -> agentMccBatch = mccBatch
//            }
//            logger.info { "New Batch ${batchNumber}" }
//            mccBatch.pairedAgents.forEach {
//                when(mccBatch.batchPopulationType) {
//                    PopulationType.Environment -> mccBatchMap[it.environment.data.id] = false
//                    PopulationType.Agent -> mccBatchMap[it.agent.data.id.toString()] = false
//                }
//                pairedAgentsChannel.send(it)
//            }
        }
    }
//    launch {
//
//        var agentCount = 0
//        var environmentCount = 0
//        for (mccResult in mccResultChannel) {
////            logger.info { mccBatchMap.containsKey(mccResult.id) }
//            val mccBatchMap = if (mccResult.id in agentMccBatchMap) agentMccBatchMap else environmentMccBatchMap
//            val mccResultList = if (mccResult.id in agentMccBatchMap) agentMccResultList else environmentMccResultList
//            val currentMccBatch = if (mccResult.id in agentMccBatchMap) agentMccBatch else environmentMccBatch
//            if (mccResult.id in mccBatchMap) {
//                if (mccResult.id in agentMccBatchMap && mccResult.dead) {
//                    agentMccBatchDeadMap[mccResult.id] = (agentMccBatchDeadMap[mccResult.id] ?: 0) + 1
//                }
//                if (mccResult.id in agentMccBatchMap) agentCount++ else environmentCount++
//                val count = if (mccResult.id in agentMccBatchMap) agentCount else environmentCount
//                if (mccResult.satisfyMC) {
//                    mccBatchMap[mccResult.id] = true
//                    mccResultList.add(mccResult)
//                }
////            logger.info { "Score $mccResult" }
////            logger.info { "remaining: ${mccBatchMap.filter { !it.value }.size}" }
//                if (mccBatchMap.all { it.value } || count == currentMccBatch.pairedAgents.size) {
//                    val mccBatchResult = MCCStageBatchResult(
//                        currentMccBatch.pairedAgents,
//                        mccResultList.associate { it.id to it.satisfyMC },
//                        currentMccBatch.batchPopulationType
//                    )
//                    if (mccResult.id in agentMccBatchMap) agentCount = 0 else environmentCount = 0
//                    mccBatchMap.clear()
//                    mccBatchResultChannel.send(mccBatchResult)
//                }
//            }
//        }
//    }
//
//
//    val createNetwork = createNetwork()
//    val connectionRelationships =
//        createNetwork.connectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
//    val targetConnectionMapping =
//        createNetwork.targetConnectionMapping.mapKeys { it.key.id }.mapValues { it.value.map { it.id } }
//    val calculationOrder = createNetwork.calculationOrder.map { it.id }
//    fun map(pairedAgents: PairedAgentsStage) = when (pairedAgents.type) {
//        PopulationType.Agent -> agentMccBatchMap
//        PopulationType.Environment -> environmentMccBatchMap
//    }
//
//    fun isDead(pairedAgents: PairedAgentsStage): Boolean {
//
//        return when (pairedAgents.type) {
//            PopulationType.Agent -> (agentMccBatchDeadMap[pairedAgents.agent.data.id.toString()] ?: 0) >= 1
//            PopulationType.Environment -> false
//        }
//    }
//
//    routing {
//        get("/model") {
//
//            var pairedAgents = pairedAgentsChannel.receive()
////            logger.info { "Paired Agent Pulled: ${pairedAgents.type}" }
//            var mccResultArrayList = ArrayList(
//                when (pairedAgents.type) {
//                    PopulationType.Environment -> environmentMccResultList
//                    PopulationType.Agent -> agentMccResultList
//                }
//            )
//            while (!map(pairedAgents).containsKey(id(pairedAgents)) || mccResultArrayList.any {
//                    it.id == id(pairedAgents) || isDead(
//                        pairedAgents
//                    )
//                }
//            ) {
//                mccResultChannel.send(MCCResult(id(pairedAgents), false, false))
//                pairedAgents = pairedAgentsChannel.receive()
////                logger.info { "Paired Agent Pulled: ${pairedAgents.type}" }
//                mccResultArrayList = ArrayList(
//                    when (pairedAgents.type) {
//                        PopulationType.Environment -> environmentMccResultList
//                        PopulationType.Agent -> agentMccResultList
//                    }
//                )
//            }
//            val blueprint = NetworkBlueprint(
//                id(pairedAgents),
//                createNetwork.planes,
//                connectionRelationships,
//                targetConnectionMapping,
//                calculationOrder,
//                0,
//                0,
//                createNetwork.outputPlane.id,
//                pairedAgents.agent.data.toModel(),
//                createNetwork.depth
//            )
//            call.respond(PairedHyperAgentEnvironment(blueprint, pairedAgents.environment.data, pairedAgents.type))
//        }
//
//        get("/fillModels") {
//            val filter =
//                environmentMccBatch.pairedAgents.filter {
//                    !(environmentMccBatchMap.get(it.environment.data.id) ?: true)
//                } + agentMccBatch.pairedAgents.filter {
//                    !(agentMccBatchMap.get(
//                        it.agent.data.id.toString()
//                    ) ?: true)
//
//                }
//            filter.forEach {
//                pairedAgentsChannel.send(it)
//            }
//            call.respond(filter.size)
//        }
//
////        post<MCCCheck>("/stageCheck") { check ->
////            call.respond(MCCResult(check.id, mccResultList.any { it.id == check.id }))
////        }
//
//        post<MCCResult>("/score") {
//            mccResultChannel.send(it)
//        }
//        get("currentBatch") {
//
//        }
//    }
}

//@Serializable
//data class PairedAgentEnvironment(
//    val type: PopulationType,
//    val agent: NeatModel?,
//    val environment: StageTrackGene,
//    val satisfied: Boolean
//)
//
//@Serializable
//data class CurrentBatch(val items: List<PairedAgentEnvironment>)

//private fun id(pairedAgents: PairedAgentsStage) = when (pairedAgents.type) {
//    PopulationType.Environment -> pairedAgents.environment.data.id
//    PopulationType.Agent -> pairedAgents.agent.data.id.toString()
//}


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
    var newStageTrack = stageTrackGene.copy(id = UUID.randomUUID().toString())
    for (entry in mutationEntries) {
        if (entry.roll(neatExperiment)) {
            newStageTrack = entry.mutation(neatExperiment, newStageTrack)
        }
    }
    return newStageTrack
}

fun loadModelsMCC(random: Random, path: String): Pair<NeatExperiment, List<MCCElement<NeatMutator>>> {
    val activationFunctions: List<ActivationGene> = Activation.CPPN.functions
    val addConnectionAttempts = 5
    val models = loadPopulation(File(path), 0).models
    logger.info { "population loaded with size of: ${models.size}" }
    val maxInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it } + 1
    val maxNodeInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it } + 1
    val simpleNeatExperiment = simpleNeatExperiment(
        random, maxInnovation, maxNodeInnovation, activationFunctions,
        addConnectionAttempts, 7f
    )
    var population = models.map { it.toNeatMutator() }.mapIndexed { index, neatMutator ->
        MCCElement(0, neatMutator)
    }
    return simpleNeatExperiment to population
}


fun loadModelsMCCStage(path: String): List<MCCElement<StageTrackGene>> {

    val models = loadStagePopulation(File(path), 0)
    logger.info { "population loaded with size of: ${models.size}" }

    var population = models.mapIndexed { index, stageTrackGene ->
        MCCElement(0, stageTrackGene)
    }
    return population
}

fun loadStagePopulation(file: File, generation: Int): List<StageTrackGene> {
    val string = file.bufferedReader().lineSequence().joinToString("\n")
    logger.info { "Loading population from file ${file.path}" }
    return Json {}.decodeFromString<List<StageTrackGene>>(string)

}