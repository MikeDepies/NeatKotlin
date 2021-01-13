package server

import AuthService
import AuthServiceAuth0
import ClientRegistry
import EvaluationArena
import MessageEndpointRegistry
import MessageEndpointRegistryImpl
import MessageWriter
import MessageWriterImpl
import PopulationEvolver
import SessionScope
import SessionScopeImpl
import Simulation
import SimulationSessionScope
import UserTokenResolver
import UserTokenResolverImpl
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.features.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.*
import neat.mutation.*
import org.koin.dsl.module
import server.message.endpoints.*
import server.message.endpoints.NodeTypeModel.*
import server.server.WebSocketManager
import simulationEndpoints
import java.io.File
import kotlin.random.Random

private val log = KotlinLogging.logger { }

val applicationModule = module {
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
    single { EvaluationArena() }
    single { simulation(evaluationArena = get()/*, takeSize = 20*/) }
}

fun simulation(evaluationArena: EvaluationArena, randomSeed: Int = 2056, takeSize: Int? = null): Simulation {
    val activationFunctions = listOf(Activation.identity, Activation.sigmoidal)

    val sharingFunction = shFunction(10f)
    val distanceFunction = compatibilityDistanceFunction(1f, 1f, 1f)
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

        simpleNeatExperiment.generateInitialPopulationWithOneButton(
            200,
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
        clone.connections
            .forEach { connectionGene ->
                if (randomOutputNode.node == connectionGene.outNode)
                    mutateConnectionWeight(connectionGene)
                else connectionGene.weight = 0f
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
    return NodeGene(node, nodeType.nodeType(), Activation.activationMap.getValue(activationFunction))
}

fun NodeTypeModel.nodeType(): NodeType = when (this) {
    Input -> NodeType.Input
    Hidden -> NodeType.Hidden
    Output -> NodeType.Output
}
