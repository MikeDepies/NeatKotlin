package server

import AuthService
import AuthServiceAuth0
import ClientRegistry
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
import kotlin.math.*
import kotlin.random.*

private val log = KotlinLogging.logger { }
inline fun <reified T> Scope.getChannel(): Channel<T> =
    get(qualifier<T>())

private var evaluationId = 0
val applicationModule = module {

    single<MessageWriter> { MessageWriterImpl(clientRegistry = get(), json = get(), application = get()) }
    single<SessionScope> { SessionScopeImpl(scope = this, messageWriter = get()) }
    single { SimulationSessionScope(scope = this, messageWriter = get()) }
    single<MessageEndpointRegistry> {
        val endpointProvider = get<EndpointProvider>()
//        val endpoints = endpointProvider.run {
//            simulationEndpoints()
//        }.toList()
        MessageEndpointRegistryImpl(listOf(), get())
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

    factory { (evaluationId: Int) -> simulation(evaluationId) }
}


data class EvaluatorIdSet(val agentId: Int, val evaluationId: Int, val generation: Int, val controllerId: Int)

fun activationFunctions(): List<ActivationGene> {
    return with(Activation) {
        listOf(
            identity,
            sigmoidal,
            step,
            complementaryLogLog,
            bipolarSigmoid,
            tanh,
//            tanhLeCun,
            hardTanh,
            absolute,
            relu,
            reluCos,
            reluSin,
//            cosine,
            smoothRectifier,
            logit,
            ActivationGene("log") { ln(it) },
            ActivationGene("inverse") { it * -1 },
            ActivationGene("inverse") { sin(it * 2) },
            ActivationGene("inverse") { cos(it * 2) },
            ActivationGene("inverse") { sinh(it) },
            ActivationGene("inverse") { cosh(it) },
            ActivationGene("inverse") { tan(it) },
            ActivationGene("inverse") { atan(it) },
//            ActivationGene("inverse") { atan2(it) },
//            ActivationGene("exp") { exp(it) },
            ActivationGene("gaussian") { exp(-1 * (it * it)) },
            ActivationGene("ramp") { 1f - (2f * (it - floor(it))) },
//            ActivationGene("exp") { it.pow(2) },
//            ActivationGene("exp") { it.pow(3) },
//            ActivationGene("exp") { it.pow(4) },
//            ActivationGene("exp") { it.pow(5) },
//            ActivationGene("exp") { it.pow(6) },
            ActivationGene("exp") { if (it == 0f) 0f else E.pow(-1.0 / (it.pow(2))).toFloat() },
//            ActivationGene("exp") { 1 },
        )
    }
}

fun simulation(evaluationId: Int, randomSeed: Int = 5, takeSize: Int? = 50): Simulation {

    val activationFunctions = baseActivationFunctions()//activationFunctions()//listOf(Activation.identity, Activation.sigmoidal)
    var largestCompatDistance = 0f
    val sharingFunction: (Float) -> Int = {
        shFunction(5f)(it)
    }
    val distanceFunction: (NeatMutator, NeatMutator) -> Float =
        { a, b -> compatibilityDistanceFunction(10f, 10f, 20f)(a, b) }
    val speciationController =
        SpeciationController(0, standardCompatibilityTest(sharingFunction, distanceFunction))
    val adjustedFitnessCalculation = adjustedFitnessCalculation(speciationController, distanceFunction, sharingFunction)
    fun input(inputSize: Int, useBoolean: Boolean) = inputSize + if (useBoolean) 1 else 0
    val speciesLineage = SpeciesLineage()
    val scoreKeeper = SpeciesScoreKeeper()
    val file = File("population$evaluationId.json")
    val random = Random(randomSeed)
    var simpleNeatExperiment = simpleNeatExperiment(random, 0, 0, activationFunctions, 100)
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
        simpleNeatExperiment = simpleNeatExperiment(random, maxInnovation, maxNodeInnovation, activationFunctions, 100)
        populationModel.map { it.toNeatMutator() }
    } else {

        simpleNeatExperiment.generateInitialPopulationCPPN(
            populationSize = 100,
            numberOfInputNodes = 3,
            numberOfOutputNodes = 3,
            functions = activationFunctions
        )
    }

    val speciate = speciationController.speciate(population, speciesLineage, 0)
    val populationEvolver = PopulationEvolver(speciationController, scoreKeeper, speciesLineage, simpleNeatExperiment)
    return Simulation(population, populationEvolver, adjustedFitnessCalculation, evaluationId)
}

fun NeatExperiment.generateInitialPopulationCPPN(
    populationSize: Int, numberOfInputNodes: Int, numberOfOutputNodes: Int, functions: List<ActivationGene>
): List<NeatMutator> {
    val neatMutator = createNeatMutator(numberOfInputNodes, numberOfOutputNodes, random, Activation.sigmoidal)
    /*fullyConnect(addNode(neatMutator), neatMutator)
    fullyConnect(addNode(neatMutator), neatMutator)*/
    addNode(neatMutator)
    addNode(neatMutator)
//    fullyConnect(addNode(neatMutator), neatMutator)
//    fullyConnect(addNode(neatMutator), neatMutator)
    addNode(neatMutator)
//    addNode(neatMutator)
//    addNode(neatMutator)
//    addNode(neatMutator)
    println(neatMutator.hiddenNodes.size)
    return (0 until populationSize).map {
        println("NEW NETWORK: $it")
        val clone = neatMutator.clone()
        clone.outputNodes.forEach {
            it.activationFunction = activationFunctions.random(random)
            println(it.activationFunction.name)
        }
        clone.hiddenNodes.forEach {
            it.activationFunction = activationFunctions.random(random)
            println(it.activationFunction.name)
        }
        clone.connections.forEach {
            it.weight = randomWeight(random)
//            println(it.weight)
        }
        clone
    }
}

private fun NeatExperiment.fullyConnect(nodeGene: NodeGene, neatMutator: NeatMutator) {
    neatMutator.inputNodes.forEach { input ->
        connectNodes(input, nodeGene, randomWeight(random), nextInnovation())
    }

    neatMutator.outputNodes.forEach { output ->
        connectNodes(nodeGene, output, randomWeight(random), nextInnovation())
    }
}

private fun NeatExperiment.addNode(neatMutator: NeatMutator): NodeGene {
    mutateAddNode(this, neatMutator)
    return neatMutator.nodes.last()
}


fun PopulationModel.neatMutatorList(): List<NeatMutator> {
    return this.models.map { it.toNeatMutator() }
}

fun NeatModel.toNeatMutator() = simpleNeatMutator(nodes.map { it.nodeGene() }, connections.map { it.connectionGene() })

fun ConnectionGeneModel.connectionGene(): ConnectionGene {
    return ConnectionGene(inNode, outNode, weight, enabled, innovation)
}

fun NodeGeneModel.nodeGene(): NodeGene {
    return NodeGene(node, bias, nodeType.nodeType(), Activation.activationMap.getValue(activationFunction))
}

fun NodeTypeModel.nodeType(): NodeType = when (this) {
    Input -> NodeType.Input
    Hidden -> NodeType.Hidden
    Output -> NodeType.Output
}

fun main() {
    log.info { sigmoidalTransferFunction(-.5f) }
}