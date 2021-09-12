package server

import PopulationEvolver
import createMutationDictionary
import kotlinx.serialization.Serializable
import neat.*
import neat.model.*
import kotlin.math.*
import kotlin.random.*


fun main2() {
    val evaluationId = 0
    val populationSize = 100


    val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = 4f, disjointCoefficient = 1f)
    val shFunction = shFunction(.61f)
    val mateChance = .1f
    val survivalThreshold = .30f
    val stagnation = 200

    val randomSeed: Int = 20 + evaluationId
    val addConnectionAttempts = 5
    val activationFunctions = Activation.CPPN.functions
    val random = Random(randomSeed)


    val simpleNeatExperiment =
        simpleNeatExperiment(random, 0, 0, activationFunctions, addConnectionAttempts)
    val population = simpleNeatExperiment.generateInitialPopulation(
        populationSize,
        6,
        1,
        activationFunctions
    )
//    createTaskNetwork(population.first().toNetwork())
    val distanceFunction = cppnGeneRuler::measure
    val simulation = createSimulation(
        evaluationId,
        population,
        distanceFunction,
        shFunction,
        mateChance,
        survivalThreshold,
        stagnation
    )
}

@Serializable
data class NodeLocation(val x: Int, val y: Int, val z: Int)

@Serializable
data class ConnectionLocation(
    val x1: Int,
    val y1: Int,
    val z1: Int,
    val x2: Int,
    val y2: Int,
    val z2: Int,
    val weight: Float
)

@Serializable
data class NetworkDescription(
    val connections: Set<ConnectionLocation>,
    val nodes: Set<NodeLocation>,
    val id : String,
    val bias: NodeLocation? = null
)

fun createTaskNetwork(network: ActivatableNetwork, modelIndex: String): NetworkDescription {
//    println("Creating Task Network")
    val connectionThreshold = .3f
    val connectionMagnitude = 2f
    val width = 240 /16
    val height = 256 /16
    val hiddenWidth = 5
    val hiddenHeight = 3
    val outputWidth = 1
    val outputHeight = 1

    var nodeId = 0
    var innovationId = 0
    val centerX = 0
    val centerY = 0
    val resolution = 1
    val resolutionY = 1
    val xMin = centerX - width / 2
    val xMax = centerX + width / 2
    val yMin = centerY - height / 2
    val yMax = centerY + height / 2

    val xMinHidden = centerX - hiddenWidth / 2
    val xMaxHidden = centerX + hiddenWidth / 2
    val yMinHidden = centerY - hiddenHeight / 2
    val yMaxHidden = centerY + hiddenHeight / 2

    val xMinOutput = centerX - outputWidth / 2
    val xMaxOutput = centerX + outputWidth / 2
    val yMinOutput = 0
    val yMaxOutput = 1
    //slice 1
    val nodeSet = mutableSetOf<NodeLocation>()
    val connectionSet = mutableSetOf<ConnectionLocation>()
    val hiddenZ = 3
    val outputZ = 4
    val input = mutableListOf(0f, 0f, 0f, 0f, 0f, hiddenZ.toFloat())
//    println("Creating Input to Hidden")
    for (x1 in xMin until xMax step resolution) {
        input[0] = x1.toFloat()
        for (y1 in yMin until yMax step resolutionY) {
            input[1] = y1.toFloat()
//            for (z1 in 0..2) {
            val z1 = 0
            input[2] = z1.toFloat()
            nodeSet += NodeLocation(x1, y1, z1)
            //slice 2
            for (x2 in xMinHidden..xMaxHidden step 1) {
                input[3] = x2.toFloat()
                for (y2 in yMinHidden..yMaxHidden step 1) {
                    input[4] = y2.toFloat()
                    network.evaluate(input)
                    val weight = network.output()[0]

                    if (weight.absoluteValue > connectionThreshold) {
                        val ratio =
                            ((weight.absoluteValue - connectionThreshold) / (1f - connectionThreshold)) * weight.sign
                        connectionSet += ConnectionLocation(x1, y1, z1, x2, y2, hiddenZ, ratio * connectionMagnitude)
                    }
                }
            }
//            }
        }
    }
//    println("Creating Hidden Connection to Hidden Connection")
    /*input[2] = hiddenZ.toFloat()
    input[5] = hiddenZ.toFloat()
    for (x1 in xMinHidden..xMaxHidden step 1) {
        for (y1 in yMinHidden..yMaxHidden step 1) {
            input[0] = x1.toFloat()
            input[1] = y1.toFloat()

            //slice 2
            for (x2 in xMinHidden..xMaxHidden step 1) {
                for (y2 in yMinHidden..yMaxHidden step 1) {
                    input[3] = x2.toFloat()
                    input[4] = y2.toFloat()
                    network.evaluate(input)
                    val weight = network.output()[0]
                    if (weight.absoluteValue > connectionThreshold) {
                        val ratio = ((weight.absoluteValue - connectionThreshold) / (1f - connectionThreshold)) * weight.sign
                        connectionSet += ConnectionLocation(x1, y1, hiddenZ, x2, y2, hiddenZ, ratio * connectionMagnitude)
                    }
                }
            }
        }
    }*/
//    println("Creating hidden to output")
    input[2] = hiddenZ.toFloat()
    input[5] = outputZ.toFloat()
    nodeSet += NodeLocation(0, 0, outputZ)
    for (x1 in xMinHidden until xMaxHidden step 1) {
        for (y1 in yMinHidden until yMaxHidden step 1) {
            input[0] = x1.toFloat()
            input[1] = y1.toFloat()
            //slice 2
            for (x2 in xMinOutput .. xMaxOutput step 1) {
                for (y2 in yMinOutput .. yMaxOutput step 1) {
                    input[3] = x2.toFloat()
                    input[4] = y2.toFloat()
                    network.evaluate(input)
                    val weight = network.output()[0]
                    if (weight.absoluteValue > connectionThreshold) {
                        val ratio =
                            ((weight.absoluteValue - connectionThreshold) / (1f - connectionThreshold)) * weight.sign
                        connectionSet += ConnectionLocation(
                            x1,
                            y1,
                            hiddenZ,
                            x2,
                            y2,
                            outputZ,
                            ratio * connectionMagnitude
                        )
                    }
                }
            }
        }
    }
//    val idMap = mutableMapOf<NodeLocation, Int>()
//    println("Creating Node Genes")
//    val nodeGenes = nodeSet.map {
//        input[0] = it.x.toFloat()
//        input[1] = it.y.toFloat()
//        input[2] = it.z.toFloat()
//        input[3] = it.x.toFloat()
//        input[4] = it.y.toFloat()
//        input[5] = it.z.toFloat()
//        network.evaluate(input)
//        val bias = network.output()[0]
//        val nodeType = when (it.z) {
//            0 -> NodeType.Input
//            1 -> NodeType.Hidden
//            2 -> NodeType.Output
//            else -> error("Too large of z index")
//        }
//        val nodeGene = NodeGene(nodeId, bias, nodeType, Activation.sigmoidal)
//        idMap[it] = nodeId
//        nodeId += 1
//        nodeGene
//    }
//    println("Creating Connection Genes")
//    val connectionGenes = connectionSet.map {
//        val sourceNode = idMap[NodeLocation(it.x1, it.y1, it.z1)]!!
//        val targetNode = idMap[NodeLocation(it.x2, it.y2, it.z2)]!!
//        ConnectionGene(sourceNode, targetNode, it.weight, true, innovationId++)
//    }

    return NetworkDescription(connectionSet, nodeSet, modelIndex)
}

fun createSimulation(
    evaluationId: Int,
    population: List<NeatMutator>,
    distanceFunction: DistanceFunction,
    shFunction: SharingFunction,
    mateChance: Float,
    survivalThreshold: Float,
    stagnation: Int
): Simulation {
    val mutationEntries = createMutationDictionary()
    val speciesId = 0
    val speciationController =
        SpeciationController(speciesId, standardCompatibilityTest(shFunction, distanceFunction))
    val adjustedFitnessCalculation =
        adjustedFitnessCalculation(speciationController, distanceFunction, shFunction)
    val speciesLineage = SpeciesLineage()
    val scoreKeeper = SpeciesScoreKeeper()
    val weightedReproduction = weightedReproduction(
        mutationEntries = mutationEntries,
        mateChance = mateChance,
        survivalThreshold = survivalThreshold,
        speciesScoreKeeper = scoreKeeper,
        stagnation = stagnation
    )
    val generation = 0
    val populationEvolver = PopulationEvolver(
        generation,
        speciationController,
        scoreKeeper,
        speciesLineage,
        weightedReproduction
    )
    return simulation(evaluationId, population, populationEvolver, adjustedFitnessCalculation)
}

fun simulation(
    evaluationId: Int,
    population: List<NeatMutator>,
    populationEvolver: PopulationEvolver,
    adjustedFitnessCalculation: AdjustedFitnessCalculation,

    ): Simulation {

    return Simulation(population, populationEvolver, adjustedFitnessCalculation, evaluationId)
}

data class Simulation(
    val initialPopulation: List<NeatMutator>,
    val populationEvolver: PopulationEvolver,
    val adjustedFitnessCalculation: AdjustedFitnessCalculation,
    val evaluationId: Int
)

fun main() {
    (0..3).forEach { println(it) }
}