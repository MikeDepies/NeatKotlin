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
import server.refactor.needed.EvaluationMessageProcessor
import server.server.*
import java.io.*
import java.util.*
import kotlin.random.*
import kotlin.random.Random

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


    single { ClientRegistry(listOf()) }

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


fun NeatExperiment.createNeatMutator2(
    inputNumber: Int,
    outputNumber: Int,
    random: Random = Random,
    function: ActivationGene = Activation.identity
): NeatMutator {
    val simpleNeatMutator = simpleNeatMutator(listOf(), listOf(), UUID.randomUUID())
    createNodes(inputNumber, 0f, NodeType.Input, Activation.identity, simpleNeatMutator)
    createNodes(outputNumber, randomWeight(random), NodeType.Output, function, simpleNeatMutator)
    connectNodes2(simpleNeatMutator)
    return simpleNeatMutator
}


fun NeatExperiment.connectNodes2(simpleNeatMutator: NeatMutator) {
    for (input in simpleNeatMutator.inputNodes) {
        newConnection(input, simpleNeatMutator.outputNodes[0], simpleNeatMutator)
//        newConnection(input, simpleNeatMutator.outputNodes[1], simpleNeatMutator)
    }
}
fun NeatExperiment.generateInitialPopulation2(
    populationSize: Int,
    numberOfInputNodes: Int,
    numberOfOutputNodes: Int,
    activationFunctions: List<ActivationGene>
): List<NeatMutator> {
    val neatMutator = createNeatMutator2(numberOfInputNodes, numberOfOutputNodes, random, activationFunctions.first())
    val range = 2.0
//    val assignConnectionRandomWeight = assignConnectionRandomWeight(range)
    fun addConnectionNode(sourceNode : Int, targetNode : Int): ConnectionGene {
        return ConnectionGene(
            sourceNode,
            targetNode,
            randomWeight(random),
            true,
            nextInnovation()
        )
    }
    fun addNode() = NodeGene(nextNode(), randomWeight(random), NodeType.Hidden, Activation.CPPN.gaussian)
//        clone.addConnection(connection)
//    val xNode = addNode()
//    val yNode = addNode()
//    val zNode = addNode()
//////
//    neatMutator.addNode(xNode)
//    neatMutator.addNode(yNode)
//    neatMutator.addNode(zNode)
//    neatMutator.addConnection(addConnectionNode(0, xNode.node))
//    neatMutator.addConnection(addConnectionNode(3, xNode.node))
//    neatMutator.addConnection(addConnectionNode(1, yNode.node))
//    neatMutator.addConnection(addConnectionNode(4, yNode.node))
//    neatMutator.addConnection(addConnectionNode(2, zNode.node))
//    neatMutator.addConnection(addConnectionNode(5, zNode.node))
//    neatMutator.addConnection(addConnectionNode(xNode.node, 7))
//    neatMutator.addConnection(addConnectionNode(yNode.node, 7))
//    neatMutator.addConnection(addConnectionNode(zNode.node, 7))
//
//    neatMutator.addConnection(addConnectionNode(xNode.node, 6))
//    neatMutator.addConnection(addConnectionNode(yNode.node, 6))
//    neatMutator.addConnection(addConnectionNode(zNode.node, 6))
//    val mutateBias = getMutateBiasConnections(1f, range, range)
    return (0 until populationSize).map {
        val clone = neatMutator.clone(UUID.randomUUID())
        clone.connections.forEach { connectionGene ->
            connectionGene.weight = random.nextDouble(-range, range).toFloat()
//            assignConnectionRandomWeight(connectionGene)
        }
        (clone.hiddenNodes + clone.outputNodes).forEach {
            it.bias = random.nextDouble(-range, range).toFloat()
        }
//        mutateBias(this, clone)
//        clone.outputNodes.forEach { println(it.node) }
        clone.outputNodes.forEach {
            it.activationFunction = activationFunctions.random(random)
        }
//        clone.outputNodes[0].activationFunction =  activationFunctions.random(random)//Activation.CPPN.linear
//        clone.outputNodes[1].activationFunction =  Activation.CPPN.linear
        clone
    }
}

fun NeatExperiment.generateInitialPopulation(
    populationSize: Int,
    numberOfInputNodes: Int,
    numberOfOutputNodes: Int,
    activationFunctions: List<ActivationGene>
): List<NeatMutator> {
    val neatMutator = createNeatMutator(numberOfInputNodes, numberOfOutputNodes, random, activationFunctions.first(), UUID.randomUUID())
    val assignConnectionRandomWeight = assignConnectionRandomWeight()
    return (0 until populationSize).map {
        val clone = neatMutator.clone(UUID.randomUUID())
        clone.connections.forEach { connectionGene ->
            assignConnectionRandomWeight(connectionGene)
        }
        clone.nodes.filter{ it.nodeType != NodeType.Input}.forEach {
            it.activationFunction = activationFunctions.random(random)
        }
        val mutate = .4f chanceToMutate mutateAddNode
        val mutateConnection = .4f chanceToMutate mutateAddConnection
        repeat(2) {
            if (mutate.roll(this)) {
                mutate.mutation(this, clone)
            }
            if (mutateConnection.roll(this)) {
                mutateConnection.mutation(this, clone)
            }
        }
//        repeat(5) {
//            if (mutateConnection.roll(this)) {
//                mutateConnection.mutation(this, clone)
//            }
//        }
        clone
    }
}


fun PopulationModel.neatMutatorList(): List<NeatMutator> {
    return this.models.map { it.toNeatMutator() }
}

fun NeatModel.toNeatMutator() = simpleNeatMutator(nodes.map { it.nodeGene() }, connections.map { it.connectionGene() }, UUID.fromString(uuid))

fun ConnectionGeneModel.connectionGene(): ConnectionGene {
    return ConnectionGene(inNode, outNode, weight, enabled, innovation)
}

fun NodeGeneModel.nodeGene(): NodeGene {
    return NodeGene(node, bias, nodeType.nodeType(), (Activation.CPPN.functions + Activation.identity).toMap { it.name }.getValue(activationFunction))
}

fun NodeTypeModel.nodeType(): NodeType = when (this) {
    Input -> NodeType.Input
    Hidden -> NodeType.Hidden
    Output -> NodeType.Output
}

fun main() {
    log.info { sigmoidalTransferFunction(-.5f) }
}