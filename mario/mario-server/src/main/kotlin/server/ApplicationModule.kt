package server

import AuthService
import AuthServiceAuth0
import ClientRegistry
import MessageEndpointRegistry
import MessageEndpointRegistryImpl
import MessageWriter
import MessageWriterImpl
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
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import mu.*
import neat.*
import neat.model.*
import neat.mutation.*
import org.koin.core.qualifier.*
import org.koin.core.scope.*
import org.koin.dsl.*
import server.message.endpoints.*
import server.message.endpoints.NodeTypeModel.*
import server.server.*
import java.io.*
import kotlin.random.*

private val log = KotlinLogging.logger { }
inline fun <reified T> Scope.getChannel(): Channel<T> =
    get(qualifier<T>())

private var evaluationId = 0
val applicationModule = module {
    single<Channel<MarioData>>(qualifier("input")) { Channel() }
    factory<Channel<MarioData>>(qualifier<MarioData>()) { Channel(Channel.CONFLATED) }
    factory<Channel<MarioOutput>>(qualifier<MarioOutput>()) { Channel() }
    single<Channel<EvaluationScore>>(qualifier<EvaluationScore>()) { Channel() }
    single<Channel<PopulationModels>>(qualifier<PopulationModels>()) { Channel() }
    single<Channel<AgentModel>>(qualifier<AgentModel>()) { Channel() }
    factory {
        EvaluationChannels(
            getChannel(),
            getChannel(),
            getChannel()
        )
    }
    single {
        val inputChannel = get<Channel<MarioData>>(qualifier("input"))
        EvaluationMessageProcessor(get(), inputChannel, get())
    }
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

    factory { (controllerId: Int) -> IOController(controllerId, getChannel(), getChannel()) }

}




data class EvaluatorIdSet(val agentId: Int, val evaluationId: Int, val generation: Int, val controllerId: Int)

/*
val populationModel = loadPopulation(File("population/${evaluationId}_population.json"))
    val models = populationModel.models
    log.info { "population loaded with size of: ${models.size}" }
    val maxNodeInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it }
    val maxInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it }
        val simpleNeatExperiment = simpleNeatExperiment(
            random, maxInnovation, maxNodeInnovation, activationFunctions,
            addConnectionAttempts
        )
        val population = models.map { it.toNeatMutator() }
 */

data class LoadedModels(val generation: Int, val models: List<NeatModel>)

fun loadPopulation(file: File, generation: Int): LoadedModels {
    val string = file.bufferedReader().lineSequence().joinToString("\n")
    log.info { "Loading population from file ${file.path}" }
    return LoadedModels(generation, Json {}.decodeFromString<List<NeatModel>>(string))

}

fun NeatExperiment.generateInitialPopulation(
    populationSize: Int,
    numberOfInputNodes: Int,
    numberOfOutputNodes: Int,
    activationFunctions: List<ActivationGene>
): List<NeatMutator> {
    val neatMutator = createNeatMutator(numberOfInputNodes, numberOfOutputNodes, random, activationFunctions.first())
    val assignConnectionRandomWeight = assignConnectionRandomWeight()
    return (0 until populationSize).map {
        val clone = neatMutator.clone()
        clone.connections.forEach { connectionGene ->
            assignConnectionRandomWeight(connectionGene)
        }
        clone.nodes.forEach {
            it.activationFunction = activationFunctions.random(random)
        }
//        mutateAddNode(clone)
//        mutateAddNode(clone)
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
    return NodeGene(node, bias, nodeType.nodeType(), Activation.CPPN.functions.toMap { it.name }.getValue(activationFunction))
}

fun NodeTypeModel.nodeType(): NodeType = when (this) {
    Input -> NodeType.Input
    Hidden -> NodeType.Hidden
    Output -> NodeType.Output
}

fun main() {
    log.info { sigmoidalTransferFunction(-.5f) }
}