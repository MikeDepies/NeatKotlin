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
    factory { (controllerId: Int) -> IOController(controllerId, getChannel(), getChannel()) }
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
        val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = 1f, disjointCoefficient = 1f)
        val randomSeed: Int = 20 + evaluationId
        val random = Random(randomSeed)
        val addConnectionAttempts = 5
        val shFunction = shFunction(.1f)
        val activationFunctions = listOf(Activation.sigmoidal, Activation.identity)//baseActivationFunctions()

//        val simpleNeatExperiment =
//            simpleNeatExperiment(random, 0, 0, activationFunctions, addConnectionAttempts)
//        val population = simpleNeatExperiment.generateInitialPopulation(
//            populationSize,
//            62,
//            8,
//            activationFunctions
//        )
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
        val compatibilityDistanceFunction = compatibilityDistanceFunction(1f, 1f, 1f)
        simulation(
            evaluationId,
            distanceFunction = { a, b ->
                cppnGeneRuler.measure(a, b)
//                            compatibilityDistanceFunction(a, b)
            },
            sharingFunction = {
                shFunction(it)
            },
            speciationController = SpeciationController(0, standardCompatibilityTest({
                shFunction(it)
            }, { a, b ->
                cppnGeneRuler.measure(a, b)
//                compatibilityDistanceFunction(a, b)
            })),
            simpleNeatExperiment = simpleNeatExperiment,
            population = population,
            generation = if (evaluationId == 0) 456 else 448
        )
    }
}


data class EvaluatorIdSet(val agentId: Int, val evaluationId: Int, val generation: Int, val controllerId: Int)

fun simulation(
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
        PopulationEvolver(speciationController, scoreKeeper, speciesLineage, simpleNeatExperiment, generation)


    val speciate = speciationController.speciate(population, speciesLineage, 0)

    return Simulation(population, populationEvolver, adjustedFitnessCalculation, evaluationId)
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
    return LoadedModels(file.name.split("_")[0].toInt(), Json {}.decodeFromString<List<NeatModel>>(string))

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