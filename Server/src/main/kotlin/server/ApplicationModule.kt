package server

import AuthService
import AuthServiceAuth0
import ClientRegistry
import FrameOutput
import FrameUpdate
import MessageEndpointRegistry
import MessageEndpointRegistryImpl
import MessageWriter
import MessageWriterImpl
import PopulationEvolver
import SessionScope
import SessionScopeImpl
import UserTokenResolver
import UserTokenResolverImpl
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.features.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.*
import neat.mutation.mutateConnectionWeight
import org.koin.core.qualifier.qualifier
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.experimental.builder.single
import server.message.endpoints.*
import server.message.endpoints.NodeTypeModel.*
import server.server.WebSocketManager
import java.io.File
import kotlin.random.Random

private val log = KotlinLogging.logger { }
inline fun <reified T> Scope.getChannel(): Channel<T> =
    get(qualifier<T>())

val applicationModule = module {
    single<Channel<FrameUpdate>>(qualifier<FrameUpdate>()) { Channel() }
    single<Channel<FrameOutput>>(qualifier<FrameOutput>()) { Channel() }
    single<Channel<EvaluationScore>>(qualifier<EvaluationScore>()) { Channel() }
    single<Channel<PopulationModels>>(qualifier<PopulationModels>()) { Channel() }
    single<Channel<AgentModel>>(qualifier<AgentModel>()) { Channel() }
    single { EvaluationChannels(getChannel(), getChannel(), getChannel(), getChannel(), getChannel()) }
    single<EvaluationMessageProcessor>()
    single<MessageWriter> { MessageWriterImpl(get(), get(), get()) }
    single<SessionScope> { SessionScopeImpl(this, get()) }
    single { SimulationSessionScope(this, get()) }
    single<MessageEndpointRegistry> {
        val endpointProvider = get<EndpointProvider>()
        val endpoints = endpointProvider.run {
            simulationEndpoints()
        }.toList()
        MessageEndpointRegistryImpl(endpoints, get())
    }
    single { EndpointProvider(get(), get(), this) }
    single<UserTokenResolver> { UserTokenResolverImpl(get()) }
    single<AuthService> { AuthServiceAuth0(get(), get()) }
    single { ClientRegistry(listOf()) }
    single { WebSocketManager(get(), get(), get(), get()) }
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
            }
            install(WebSockets)
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
            install(Logging) {
                level = LogLevel.NONE
            }
        }
    }

    single { MeleeState(createEmptyFrameData()) }
    single { FrameClockFactory() }
    factory<Evaluator> { (agentId: Int) -> SimpleEvaluator(agentId, get(), 8f, get()) }
    single { EvaluationArena() }
    single { simulation(evaluationArena = get(), takeSize = 50) }
}

fun simulation(evaluationArena: EvaluationArena, randomSeed: Int = 44, takeSize: Int? = null): Simulation {
    val activationFunctions = baseActivationFunctions()//listOf(Activation.identity, Activation.sigmoidal)
    var largestCompatDistance = 0f
    val sharingFunction: (Float) -> Int = {
//        if (it > largestCompatDistance) {
////            log.info { "CompatDistance: $it" }
//            largestCompatDistance = it
//            if (it > 3f) {
//                largestCompatDistance = 0f
//            }
//        }
        shFunction(2f)(it)
    }
    val distanceFunction: (NeatMutator, NeatMutator) -> Float =
        { a, b -> compatibilityDistanceFunction(20f, 20f, 15f)(a, b) }
    val speciationController =
        SpeciationController(0, standardCompatibilityTest(sharingFunction, distanceFunction))
    val adjustedFitnessCalculation = adjustedFitnessCalculation(speciationController, distanceFunction, sharingFunction)
    fun input(inputSize: Int, useBoolean: Boolean) = inputSize + if (useBoolean) 1 else 0
    val speciesLineage = SpeciesLineage()
    val scoreKeeper = SpeciesScoreKeeper()
    val file = File("population.json")
    val random = Random(randomSeed)
    var simpleNeatExperiment = simpleNeatExperiment(random, 0, 0, activationFunctions)
    val population = if (file.exists()) {
        val string = file.bufferedReader().lineSequence().joinToString("\n")
        log.info { "Loading population from file" }
        val populationModel =
            Json {}.decodeFromString<List<NeatModel>>(string).let {
                if (takeSize == null || takeSize > it.size) it else it.shuffled(random).take(takeSize)
            }
        log.info { "population loaded with size of: ${populationModel.size}" }
        val maxNodeInnovation = populationModel.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it }
        val maxInnovation = populationModel.map { model -> model.nodes.maxOf { it.node } }.maxOf { it }
        simpleNeatExperiment = simpleNeatExperiment(random, maxInnovation, maxNodeInnovation, activationFunctions)
        populationModel.map { it.toNeatMutator() }
    } else {

        simpleNeatExperiment.generateInitialPopulation(
            20,
            input(53, true),
            9,
            Activation.sigmoidal
        )
    }

    val speciate = speciationController.speciate(population, speciesLineage, 0)
    val populationEvolver = PopulationEvolver(speciationController, scoreKeeper, speciesLineage, simpleNeatExperiment)
    return Simulation(population, evaluationArena, populationEvolver, adjustedFitnessCalculation)
}

fun NeatExperiment.generateInitialPopulationWithOneButton(
    populationSize: Int, numberOfInputNodes: Int, numberOfOutputNodes: Int, function: ActivationGene
): List<NeatMutator> {
    val neatMutator = createNeatMutator(numberOfInputNodes, numberOfOutputNodes, random, function)
    return (0 until populationSize).map {
        val clone = neatMutator.clone()
        val randomOutputNode = clone.outputNodes.random(random)
        val analogOutputNodes = listOf(4, 5, 6, 7).map { clone.outputNodes[it] }
        clone.connections
            .forEach { connectionGene ->
                if (randomOutputNode.node == connectionGene.outNode)
                    mutateConnectionWeight(connectionGene)
                else connectionGene.weight = 0f
            }

//        clone.outputNodes.forEach {
//            it.activationFunction = Activation.identity
//        }

        val connectionsFrom = clone.connectionsFrom(clone.inputNodes[0])
        connectionsFrom
            .filter { connectionGene -> connectionGene.outNode != randomOutputNode.node && analogOutputNodes.none { connectionGene.outNode == it.node } }
            .forEach { connectionGene ->
                connectionGene.weight = -1f
            }
//        connectionsFrom.filter { connectionGene ->
//            analogOutputNodes.none { connectionGene.outNode == it.node }
//        }.forEach { connectionGene ->
//            connectionGene.weight = 0f
//        }
        clone
    }
}

fun NeatExperiment.generateInitialPopulationWithOneButton2(
    populationSize: Int, numberOfInputNodes: Int, numberOfOutputNodes: Int, function: ActivationGene
): List<NeatMutator> {
    val neatMutator = createNeatMutator(numberOfInputNodes, numberOfOutputNodes, random, function)
    return (0 until populationSize).map {
        val clone = neatMutator.clone()
        val randomOutputNode = clone.outputNodes.random(random)
        val randomOutputNode2 = (clone.outputNodes - randomOutputNode).random(random)
        val randomOutputNode3 = (clone.outputNodes - randomOutputNode - randomOutputNode2).random(random)
        val analogOutputNodes = listOf(4, 5, 6, 7).map { clone.outputNodes[it] }
        val randomOutputs = listOf(randomOutputNode, randomOutputNode2, randomOutputNode3)
        clone.connections
            .forEach { connectionGene ->
                if (randomOutputs.any { connectionGene.outNode == it.node })
                    mutateConnectionWeight(connectionGene)
                else connectionGene.enabled = false
            }

//        clone.outputNodes.forEach {
//            it.activationFunction = Activation.identity
//        }

//        connectionsFrom.filter { connectionGene ->
//            analogOutputNodes.none { connectionGene.outNode == it.node }
//        }.forEach { connectionGene ->
//            connectionGene.weight = 0f
//        }
        clone
    }
}

fun PopulationModel.neatMutatorList(): List<NeatMutator> {
    return this.models.map { it.toNeatMutator() }
}

fun NeatModel.toNeatMutator() = simpleNeatMutator(nodes.map { it.nodeGene() }, connections.map { it.connectionGene() })

fun ConnectionGeneModel.connectionGene(): ConnectionGene {
    return ConnectionGene(inNode, outNode, weight, enabled, innovation)
}

fun NodeGeneModel.nodeGene(): NodeGene {
    return NodeGene(node, nodeType.nodeType(), Activation.activationMap.getValue(activationFunction))
}

fun NodeTypeModel.nodeType(): NodeType = when (this) {
    Input -> NodeType.Input
    Hidden -> NodeType.Hidden
    Output -> NodeType.Output
}

fun main() {
    log.info { sigmoidalTransferFunction(-.5f) }
}