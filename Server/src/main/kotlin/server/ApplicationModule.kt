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
import org.koin.dsl.module
import server.message.endpoints.*
import server.message.endpoints.NodeTypeModel.*
import server.server.WebSocketManager
import simulationEndpoints
import java.io.File
import kotlin.random.Random

private val log = KotlinLogging.logger {  }

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
    factory { simulation(evaluationArena = get()) }
}


fun simulation(randomSeed: Int = 2056, evaluationArena: EvaluationArena): Simulation {
    val activationFunctions = listOf(Activation.identity, Activation.sigmoidal)

    val sharingFunction = shFunction(3f)
    val distanceFunction = compatibilityDistanceFunction(1f, 1f, 1f)
    val speciationController =
        SpeciationController(0, standardCompatibilityTest(sharingFunction, distanceFunction))
    val adjustedFitnessCalculation = adjustedFitnessCalculation(speciationController, distanceFunction, sharingFunction)
    fun input(inputSize: Int, useBoolean: Boolean) = inputSize + if (useBoolean) 1 else 0
    val speciesLineage = SpeciesLineage()
    val scoreKeeper = SpeciesScoreKeeper()
    val file = File("population.json")
    var simpleNeatExperiment = simpleNeatExperiment(Random(randomSeed), 0, 0, activationFunctions)
    val population = if (file.exists()) {
        val string = file.bufferedReader().lineSequence().joinToString("\n")
//        log.info { string }
        val populationModel =
            Json {}.decodeFromString<List<NeatModel>>(string)
        val maxNodeInnovation = populationModel.map { model -> model.connections.maxOf { it.innovation } }.maxOf { it }
        val maxInnovation = populationModel.map { model -> model.nodes.maxOf { it.node } }.maxOf { it }
        simpleNeatExperiment = simpleNeatExperiment(Random(randomSeed), maxInnovation, maxNodeInnovation, activationFunctions)
        populationModel.map { it.NeatMutator() }
    } else {

        simpleNeatExperiment.generateInitialPopulation(
            25,
            input(53, true),
            10,
            Activation.sigmoidal
        )
    }

    val speciate = speciationController.speciate(population, speciesLineage, 0)
    val populationEvolver = PopulationEvolver(speciationController, scoreKeeper, speciesLineage, simpleNeatExperiment)
    return Simulation(population, evaluationArena, populationEvolver, adjustedFitnessCalculation)
}

private fun PopulationModel.neatMutatorList(): List<NeatMutator> {
    return this.models.map { it.NeatMutator() }
}

private fun NeatModel.NeatMutator() = simpleNeatMutator(nodes.map { it.nodeGene() }, connections.map { it.connectionGene() })

private fun ConnectionGeneModel.connectionGene(): ConnectionGene {
    return ConnectionGene(inNode, outNode, weight, enabled, innovation)
}

private fun NodeGeneModel.nodeGene(): NodeGene {
    return NodeGene(node, nodeType.nodeType(), Activation.activationMap.getValue(activationFunction))
}

private fun NodeTypeModel.nodeType(): NodeType = when (this) {
    Input -> NodeType.Input
    Hidden -> NodeType.Hidden
    Output -> NodeType.Output
}
