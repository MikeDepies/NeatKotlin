package server
//
//import AuthService
//import AuthServiceAuth0
//import ClientRegistry

import PopulationEvolver
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mu.KotlinLogging
import neat.*
import neat.model.*
import neat.mutation.assignConnectionRandomWeight
import org.koin.core.qualifier.qualifier
import org.koin.core.scope.Scope
import org.koin.dsl.module
import server.message.endpoints.*
import server.message.endpoints.NodeTypeModel.*
import server.service.TwitchBotService
import java.io.File
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.random.Random

private val log = KotlinLogging.logger { }
inline fun <reified T> Scope.getChannel(): Channel<T> =
    get(qualifier<T>())

private var evaluationId = 0
val applicationModule = module {
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
            }
            install(ContentNegotiation)

        }
    }
    single {
        val json by inject<Json>()
        json.decodeFromStream<Config>(File("config.json").inputStream())
    }
    single {
        val config by inject<Config>()
        TwitchBotService(get(), config.url.twitchBot)
    }

}

class CPPNGeneRuler(val weightCoefficient: Float = .5f, val disjointCoefficient: Float =1f, val normalize : Int = 1) {
    fun measure(parent1: NeatMutator, parent2: NeatMutator): Float {
        return nodeDistance(parent1, parent2) + connectionDistance(parent1, parent2)
    }

    private fun connectionDistance(parent1: NeatMutator, parent2: NeatMutator): Float {
        val connectionDisjoint =
            parent1.connections.count { !parent2.hasConnection(it.innovation) } + parent2.connections.count {
                !parent1.hasConnection(
                    it.innovation
                )
            }
        val connectionDistance = parent2.connections.filter { parent1.hasConnection(it.innovation) }
            .map { (it.weight - parent2.connection(it.innovation).weight).absoluteValue * weightCoefficient }.sum()
        val max = max(
            parent1.connections.size,
            parent2.connections.size
        )
        return (connectionDistance + connectionDisjoint * disjointCoefficient) / (if (max < normalize) 1 else max)
    }

    private fun nodeDistance(parent1: NeatMutator, parent2: NeatMutator): Float {
        val nodeDisjoint =
            parent1.nodes.count { !parent2.hasNode(it.node) } + parent2.nodes.count { !parent1.hasNode(it.node) }
        val nodeDistance = 0f + parent2.nodes.filter { parent1.hasNode(it.node) }.map {
            val node = parent1.node(it.node)
            val activationFunctionDistance = if (node.activationFunction == it.activationFunction) 0f else 1f
            val biasDistance = (it.bias - node.bias).absoluteValue
            val distance = biasDistance + activationFunctionDistance
            distance * weightCoefficient
        }.sum()
        val max = max(parent1.nodes.size, parent2.nodes.size)
        return (nodeDistance + nodeDisjoint * disjointCoefficient) / (if (max < normalize) 1 else max)
    }


}


data class EvaluatorIdSet(val agentId: Int, val evaluationId: Int, val generation: Int, val controllerId: Int)

fun NeatExperiment.connectNodes2(simpleNeatMutator: NeatMutator) {
    for (input in simpleNeatMutator.inputNodes) {
            newConnection(input, simpleNeatMutator.outputNodes[0], simpleNeatMutator)
            newConnection(input, simpleNeatMutator.outputNodes[1], simpleNeatMutator)
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
    val neatMutator = createNeatMutator2(numberOfInputNodes, numberOfOutputNodes, random, activationFunctions.first())
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
    repeat(500) {
        mutateAddNode(neatMutator)

    }

    return (0 until populationSize).map {
        val clone = neatMutator.clone()
        clone.connections.forEach { connectionGene ->
            assignConnectionRandomWeight(connectionGene)
        }
//        clone.outputNodes.forEach { println(it.node) }
//        repeat(300) {
//            mutateAddConnection(clone)
//        }
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