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
import neat.mutation.assignConnectionRandomWeight
import org.koin.core.qualifier.qualifier
import org.koin.core.scope.Scope
import org.koin.dsl.module
import server.message.endpoints.*
import server.message.endpoints.NodeTypeModel.*
import server.server.WebSocketManager
import java.io.File
import kotlin.random.Random

private val log = KotlinLogging.logger { }
inline fun <reified T> Scope.getChannel(): Channel<T> =
    get(qualifier<T>())

private var evaluationId = 0
val applicationModule = module {
    single<Channel<FrameUpdate>>(qualifier("input")) { Channel() }
    factory<Channel<FrameUpdate>>(qualifier<FrameUpdate>()) { Channel(Channel.CONFLATED) }
    factory<Channel<FrameOutput>>(qualifier<FrameOutput>()) { Channel() }
    factory<Channel<FrameOutput>>(qualifier<ModelUpdate>()) { Channel() }
    single<Channel<EvaluationScore>>(qualifier<EvaluationScore>()) { Channel() }
    single<Channel<PopulationModels>>(qualifier<PopulationModels>()) { Channel() }
    single<Channel<EvaluationClocksUpdate>>(qualifier<EvaluationClocksUpdate>()) { Channel() }
    single<Channel<AgentModel>>(qualifier<AgentModel>()) { Channel() }
    factory {
        EvaluationChannels(
            getChannel(),
            getChannel(),
            getChannel(),
            getChannel()
        )
    }
    single {
        val inputChannel = get<Channel<FrameUpdate>>(qualifier("input"))
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

    factory { MeleeState(null) }
    single { FrameClockFactory() }
    factory { (controllerId: Int) -> IOController(controllerId, getChannel(), getChannel(), getChannel()) }
    factory<ResourceEvaluator> { (evaluationIdSet: EvaluatorIdSet, meleeState: MeleeState, network: ActivatableNetwork) ->
        println("New Evaluator?")
        val (agentId: Int, evaluationId: Int, generation: Int, controllerId: Int) = evaluationIdSet
        ResourceEvaluator(
            network,
            agentId,
            evaluationId,
            generation,
            controllerId,
            meleeState,
            10f,
            get(),
            12000f * 24
        )
    }
    factory { (evaluationId: Int, populationSize: Int) ->
        val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = 1f, disjointCoefficient = 2f)
        val randomSeed: Int = 200 + evaluationId
        val random = Random(randomSeed)
        val addConnectionAttempts = 5
        val shFunction = shFunction(.1f)

////
        val populationModel = loadPopulation(File("population/${evaluationId}_population.json"))
        val models = populationModel.models
        log.info { "population loaded with size of: ${models.size}" }
        val maxNodeInnovation = models.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it } + 1
        val maxInnovation = models.map { model -> model.nodes.maxOf { it.node } }.maxOf { it } + 1
        val simpleNeatExperiment = simpleNeatExperiment(
            random, maxInnovation, maxNodeInnovation, Activation.CPPN.functions,
            addConnectionAttempts
        )
        val population = models.map { it.toNeatMutator() }
        val compatibilityDistanceFunction = compatibilityDistanceFunction(1f, 1f, 1f)
        val standardCompatibilityTest = standardCompatibilityTest({
            shFunction(it)
        }, { a, b ->
            cppnGeneRuler.measure(a, b)
        })
//        val simpleNeatExperiment = simpleNeatExperiment(random, 0, 0, Activation.CPPN.functions, addConnectionAttempts)
//        val population = simpleNeatExperiment.generateInitialPopulation2(
//            populationSize,
//            6,
//            2,
//            Activation.CPPN.functions
//        )
        simulation(
            standardCompatibilityTest,
            evaluationId,
            distanceFunction = { a, b ->
                cppnGeneRuler.measure(a, b)
            },
            sharingFunction = {
                shFunction(it)
            },
            speciationController = SpeciationController(0),
            simpleNeatExperiment = simpleNeatExperiment,
            population = population,
            generation = populationModel.generation
        )
    }
}


data class EvaluatorIdSet(val agentId: Int, val evaluationId: Int, val generation: Int, val controllerId: Int)

fun NeatExperiment.connectNodes2(simpleNeatMutator: NeatMutator) {
    for (input in simpleNeatMutator.inputNodes) {
            newConnection(input, simpleNeatMutator.outputNodes[0], simpleNeatMutator)
    }
}


fun NeatExperiment.createNeatMutator2(
    inputNumber: Int,
    outputNumber: Int,
    random: Random = Random,
    function: ActivationGene = Activation.identity
): NeatMutator {
    val simpleNeatMutator = simpleNeatMutator(listOf(), listOf())
    createNodes(inputNumber, 0f, NodeType.Input, Activation.identity, simpleNeatMutator)
    createNodes(outputNumber, randomWeight(random), NodeType.Output, function, simpleNeatMutator)
    connectNodes2(simpleNeatMutator)
    return simpleNeatMutator
}
fun NeatExperiment.generateInitialPopulation2(
    populationSize: Int,
    numberOfInputNodes: Int,
    numberOfOutputNodes: Int,
    activationFunctions: List<ActivationGene>
): List<NeatMutator> {
    val neatMutator = createNeatMutator(numberOfInputNodes, numberOfOutputNodes, random, activationFunctions.first())
    val assignConnectionRandomWeight = assignConnectionRandomWeight()
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
//
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

    return (0 until populationSize).map {
        val clone = neatMutator.clone()
        clone.connections.forEach { connectionGene ->
            assignConnectionRandomWeight(connectionGene)
        }
//        clone.outputNodes.forEach { println(it.node) }
        clone.outputNodes.forEach {
            it.activationFunction = activationFunctions.random(random)
        }
        clone.outputNodes[1].activationFunction = Activation.CPPN.linear
        clone
    }
}

fun simulation(
    standardCompatibilityTest: CompatibilityTest,
    evaluationId: Int,
    distanceFunction: (NeatMutator, NeatMutator) -> Float,
    sharingFunction: (Float) -> Int,
    speciationController: SpeciationController,
    simpleNeatExperiment: NeatExperiment,
    population: List<NeatMutator>,
    generation: Int
): Simulation {
    val adjustedFitnessCalculation = adjustedFitnessCalculation(speciationController, distanceFunction, sharingFunction)
    val speciesLineage = SpeciesLineage()
    val scoreKeeper = SpeciesScoreKeeper()

    val populationEvolver =
        PopulationEvolver(speciationController, scoreKeeper, speciesLineage, simpleNeatExperiment, generation, standardCompatibilityTest)


    val speciate = speciationController.speciate(population, speciesLineage, 0, standardCompatibilityTest)

    return Simulation(population, populationEvolver, adjustedFitnessCalculation, evaluationId, standardCompatibilityTest)
//    val file = File("population/${evaluationId}_population.json")
}

/**
 * if (file.exists()) {
val string = file.bufferedReader().lineSequence().joinToString("\n")
log.info { "Loading population from file" }
val populationModel =
Json {}.decodeFromString<List<NeatModel>>(string).let {
if (takeSize == null || takeSize > it.size) it else it.shuffled(random).take(takeSize)
}
log.info { "population loaded with size of: ${populationModel.size}" }
val maxNodeInnovation = populationModel.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it }
val maxInnovation = populationModel.map { model -> model.nodes.maxOf { it.node } }.maxOf { it }
simpleNeatExperiment = simpleNeatExperiment(
random, maxInnovation, maxNodeInnovation, activationFunctions,
addConnectionAttempts
)
populationModel.map { it.toNeatMutator() }
} else {
}
 */
data class LoadedModels(val generation: Int, val models: List<NeatModel>)

fun loadPopulation(file: File): LoadedModels {
    val string = file.bufferedReader().lineSequence().joinToString("\n")
    log.info { "Loading population from file ${file.path}" }
    return LoadedModels(1774, Json {}.decodeFromString<List<NeatModel>>(string))

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
val toMap = (Activation.CPPN.functions + Activation.identity).toMap { it.name }
fun NodeGeneModel.nodeGene(): NodeGene {

    return NodeGene(node, bias, nodeType.nodeType(), toMap.getValue(activationFunction))
}

fun NodeTypeModel.nodeType(): NodeType = when (this) {
    Input -> NodeType.Input
    Hidden -> NodeType.Hidden
    Output -> NodeType.Output
}

fun main() {
    log.info { sigmoidalTransferFunction(-.5f) }
}