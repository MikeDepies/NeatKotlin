package server

import PopulationEvolver
import createMutationDictionary
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import server.message.endpoints.NeatModel
import java.lang.Integer.min
import java.util.*

private val log = KotlinLogging.logger { }


@Serializable(with = NodeLocationSerializer::class)
data class NodeLocation(val x: Int, val y: Int, val z: Int)

object NodeLocationSerializer : KSerializer<NodeLocation> {
    override fun deserialize(decoder: Decoder): NodeLocation {
        val (x, y, z) = decoder.decodeSerializableValue(delegate)
        return NodeLocation(x, y, z)
    }

    val delegate = IntArraySerializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: NodeLocation) {
        encoder.encodeSerializableValue(delegate, intArrayOf(value.x, value.y, value.z))
    }

}

object ConnectionLocationSerializer : KSerializer<ConnectionLocation> {
    val serializer = JsonArray.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor
    override fun deserialize(decoder: Decoder): ConnectionLocation {
        val a = decoder.decodeSerializableValue(serializer)
        return ConnectionLocation(
            a[0].jsonPrimitive.int,
            a[1].jsonPrimitive.int,
            a[2].jsonPrimitive.int,
            a[3].jsonPrimitive.int,
            a[4].jsonPrimitive.int,
            a[5].jsonPrimitive.int,
            a[6].jsonPrimitive.float
        )
    }

    override fun serialize(encoder: Encoder, value: ConnectionLocation) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.x1)
            encodeIntElement(descriptor, 1, value.y1)
            encodeIntElement(descriptor, 2, value.z1)
            encodeIntElement(descriptor, 3, value.x2)
            encodeIntElement(descriptor, 4, value.y2)
            encodeIntElement(descriptor, 5, value.z2)
            encodeFloatElement(descriptor, 6, value.weight)
        }

    }

}

@Serializable(with = ConnectionLocationSerializer::class)
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
data class NetworkBlueprint(
    val id : String,
    val connectionPlanes : List<LayerShape3D>,
    val connectionRelationships : Map<String, List<String>>,
    val targetConnectionMapping : Map<String, List<String>>,
    val calculationOrder : List<String>,
    val species: Int,
    val hiddenNodes: Int,
    val outputLayer: List<String>,
    val inputLayer: List<String>,
    val neatModel: NeatModel,
    val depth: Int
)

@Serializable
data class NetworkDescription(
    val connections: Collection<ConnectionLocation>,
    val nodes: Collection<NodeLocation>,
    val id: String,
    val bias: NodeLocation? = null,
    val shapes: List<List<Int>>
)

fun createTaskNetwork(network: ActivatableNetwork, modelIndex: String): NetworkDescription {
//    println("Creating Task Network")
    val connectionThreshold = .2f
    val connectionMagnitude = 3f
    val controllerInputWidth = 12
    val controllerInputHeight = 1
    val width = 32
    val height = 30
    val hiddenWidth = 5
    val hiddenHeight = 5

    val hiddenWidth2 = 5
    val hiddenHeight2 = 5

    val hiddenWidth3 = 5
    val hiddenHeight3 = 5

    val hiddenWidth4 = 5
    val hiddenHeight4 = 5

    val hiddenWidth5 = 5
    val hiddenHeight5 = 5

    val outputWidth = 12
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

    val xMinHidden2 = centerX - hiddenWidth2 / 2
    val xMaxHidden2 = centerX + hiddenWidth2 / 2
    val yMinHidden2 = centerY - hiddenHeight2 / 2
    val yMaxHidden2 = centerY + hiddenHeight2 / 2
    fun rangeFor(distance: Int) = (centerX - distance / 2)..(centerX + distance / 2)
    fun rangeForUntil(distance: Int) = (centerX - distance / 2) until (centerX + distance / 2)
    val xMinOutput = 0
    val xMaxOutput = 12
    val yMinOutput = 0
    val yMaxOutput = 1
    //slice 1
    val nodeSet = mutableSetOf<NodeLocation>()
    val connectionSet = mutableSetOf<ConnectionLocation>()
    val hiddenZ = 1
    val hiddenZ2 = 2
    val hiddenZ3 = 3
    val hiddenZ4 = 4
    val hiddenZ5 = 5
    val outputZ = 6
    val input = mutableListOf(0f, 0f, 0f, 0f, 0f, hiddenZ.toFloat() / outputZ)
//    println("Creating Input to Hidden")
    val z1 = 0


    fun connectSubstrate(xRange1: IntRange, yRange1: IntRange, z1: Int, xRange2: IntRange, yRange2: IntRange, z2: Int) {
        input[2] = ((z1.toFloat() / outputZ) * 2) - 1
        input[5] = ((z2.toFloat() / outputZ) * 2) - 1
        for (x1 in xRange1 step 1) {
            for (y1 in yRange1 step 1) {
                input[0] = x1.toFloat() / (xRange1.last + 1)
                input[1] = y1.toFloat() / (yRange1.last + 1)
                //slice 2
                for (x2 in xRange2 step 1) {
                    for (y2 in yRange2 step 1) {
                        input[3] = x2.toFloat() / (xRange2.last + 1)
                        input[4] = y2.toFloat() / (yRange2.last + 1)
                        network.evaluate(input)
                        val weight = network.output()[0]
                        val expressValue = network.output()[1]
                        val express = expressValue > 0
//                        if (z1 == 0 && z2 == 1)
//                            log.info { "($x1, $y1, $z1, $x2, $y2, $z2 = $weight, $expressValue " }

                        if (express) {
                            val ratio = weight
                            connectionSet += ConnectionLocation(
                                x1 - xRange1.first,
                                y1 - yRange1.first,
                                z1,
                                x2 - xRange2.first,
                                y2 - yRange2.first,
                                z2,
                                ratio * connectionMagnitude
                            )
                        }
                    }
                }
            }
        }
    }

    fun bias(xRange2: IntRange, yRange2: IntRange, z2: Int) {
        input[2] = -2f
        input[5] = ((z2.toFloat() / outputZ) * 2) - 1

        input[0] = 0f
        input[1] = 0f
        //slice 2
        for (x2 in xRange2 step 1) {
            for (y2 in yRange2 step 1) {
                input[3] = x2.toFloat() / (xRange2.last + 1)
                input[4] = y2.toFloat() / (yRange2.last + 1)
                network.evaluate(input)
                val weight = network.output()[0]
                val expressValue = network.output()[1]
                val express = expressValue > 0

                if (express) {
                    val ratio = weight
                    connectionSet += ConnectionLocation(
                        0,
                        0,
                        -2,
                        x2 - xRange2.first,
                        y2 - yRange2.first,
                        z2,
                        ratio * connectionMagnitude
                    )
                }
            }

        }
    }

    bias(
        xMinHidden..xMaxHidden,
        yMinHidden..yMaxHidden,
        hiddenZ
    )
    bias(
        xMinHidden2..xMaxHidden2,
        yMinHidden2..yMaxHidden2,
        hiddenZ2
    )
    bias(
        rangeFor(hiddenWidth3),
        rangeFor(hiddenHeight3),
        hiddenZ3
    )
    bias(
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4
    )
    bias(
        rangeFor(hiddenWidth5),
        rangeFor(hiddenHeight5),
        hiddenZ5
    )
    bias(
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ
    )
    connectSubstrate(
        rangeForUntil(width),
        0 until 1,
        z1,
        xMinHidden..xMaxHidden,
        yMinHidden..yMaxHidden,
        hiddenZ
    )
    connectSubstrate(
        rangeForUntil(controllerInputWidth),
        0 until 1,
        -1,
        xMinHidden..xMaxHidden,
        yMinHidden..yMaxHidden,
        hiddenZ
    )
    connectSubstrate(
        rangeForUntil(controllerInputWidth),
        0 until 1,
        -1,
        xMinHidden2..xMaxHidden2,
        yMinHidden2..yMaxHidden2,
        hiddenZ2
    )
    connectSubstrate(
        rangeForUntil(controllerInputWidth),
        0 until 1,
        -1,
        rangeFor(hiddenWidth3),
        rangeFor(hiddenHeight3),
        hiddenZ3
    )
    connectSubstrate(
        rangeForUntil(controllerInputWidth),
        0 until 1,
        -1,
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4
    )
    connectSubstrate(
        xMinHidden..xMaxHidden,
        yMinHidden..yMaxHidden,
        hiddenZ,
        xMinHidden2..xMaxHidden2,
        yMinHidden2..yMaxHidden2,
        hiddenZ2
    )

    connectSubstrate(
        xMinHidden..xMaxHidden,
        yMinHidden..yMaxHidden,
        hiddenZ,
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ
    )
    connectSubstrate(
        xMinHidden2..xMaxHidden2,
        yMinHidden2..yMaxHidden2,
        hiddenZ2,
        rangeFor(hiddenWidth3),
        rangeFor(hiddenHeight3),
        hiddenZ3
    )
    connectSubstrate(
        rangeFor(hiddenWidth3),
        rangeFor(hiddenHeight3),
        hiddenZ3,
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4
    )
    connectSubstrate(
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4,
        rangeFor(hiddenWidth5),
        rangeFor(hiddenHeight5),
        hiddenZ5
    )
    connectSubstrate(
        xMinHidden2..xMaxHidden2,
        yMinHidden2..yMaxHidden2,
        hiddenZ2,
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ
    )
    connectSubstrate(
        rangeFor(hiddenWidth3),
        rangeFor(hiddenHeight3),
        hiddenZ3,
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ
    )
    connectSubstrate(
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4,
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ
    )
    connectSubstrate(
        rangeFor(hiddenWidth5),
        rangeFor(hiddenHeight5),
        hiddenZ5,
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ
    )

    // cyclic
    connectSubstrate(
        xMinHidden2..xMaxHidden2,
        yMinHidden2..yMaxHidden2,
        hiddenZ2,
        xMinHidden2..xMaxHidden2,
        yMinHidden2..yMaxHidden2,
        hiddenZ2
    )
    connectSubstrate(
        rangeFor(hiddenWidth3),
        rangeFor(hiddenHeight3),
        hiddenZ3,
        rangeFor(hiddenWidth3),
        rangeFor(hiddenHeight3),
        hiddenZ3
    )
    connectSubstrate(
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4,
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4
    )
    connectSubstrate(
        rangeFor(hiddenWidth5),
        rangeFor(hiddenHeight5),
        hiddenZ5,
        rangeFor(hiddenWidth5),
        rangeFor(hiddenHeight5),
        hiddenZ5
    )
    connectSubstrate(
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ,
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ
    )
    // Backwards
    connectSubstrate(
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ,
        rangeFor(hiddenWidth5),
        rangeFor(hiddenHeight5),
        hiddenZ5
    )
    connectSubstrate(
        xMinOutput until xMaxOutput,
        yMinOutput until yMaxOutput,
        outputZ,
        rangeFor(hiddenWidth4),
        rangeFor(hiddenHeight4),
        hiddenZ4
    )
    nodeSet += NodeLocation(0, 0, outputZ)

    return NetworkDescription(
        connectionSet, nodeSet, modelIndex, shapes = listOf(
            listOf(height, width),
            listOf(hiddenHeight, hiddenWidth),
            listOf(hiddenHeight2, hiddenWidth2),
            listOf(hiddenHeight3, hiddenWidth3),
            listOf(hiddenHeight4, hiddenWidth4),
            listOf(hiddenHeight5, hiddenWidth5),
            listOf(outputHeight, outputWidth),
            listOf(controllerInputHeight, controllerInputWidth)
        )
    )

}

fun layerPlane(height: Int, width: Int, id: String = UUID.randomUUID().toString()): LayerPlane {
    return LayerPlane(height, width, id)
}
@Serializable
data class LayerPlane(val height: Int, val width: Int, val id: String)
@Serializable
data class LayerShape3D(val layerPlane: LayerPlane, val xOrigin: Int, val yOrigin: Int, val zOrigin: Int)
data class NetworkShape(val width: Int, val height: Int, val depth: Int)

@OptIn(ExperimentalStdlibApi::class)
fun createNetwork(): TaskNetworkBuilder {
    val networkShape = NetworkShape(1, 1, 1)
    val inputImagePlane = layerPlane(15, 16)
    val inputImagePlane2 = layerPlane(15, 16)
    val inputImagePlane3 = layerPlane(15, 16)
    val inputPlanes = listOf(inputImagePlane, inputImagePlane2, inputImagePlane3)
    val hiddenPlanes = (0..7).map {
         layerPlane(5,5)
    }
    val analogPlane = layerPlane(1, 5)
    val button1Plane = layerPlane(1, 3)
    val button2Plane = layerPlane(1, 3)
    val outputPlanes = listOf(analogPlane,button1Plane,button2Plane)
    val computationOrder = hiddenPlanes + outputPlanes
    val connectionMapping = buildMap<LayerPlane, List<LayerPlane>> {
        val planeList = hiddenPlanes
        put(inputImagePlane, planeList)
        put(inputImagePlane2, planeList)
        put(inputImagePlane3, planeList)
        hiddenPlanes.forEachIndexed { index, layerPlane ->
            if (index > hiddenPlanes.size -3)
                put(layerPlane, planeList.drop(index + 1).take(2) + outputPlanes)
            else
                put(layerPlane, planeList.drop(min(2, index + 1)))
        }
//        put(outputPlane, planeList.drop(2))
    }
//    println(connectionMapping)
    val planeZMap = buildMap<LayerPlane, Int> {
        var zIndex =0
        put(inputImagePlane, zIndex++)
        put(inputImagePlane2, zIndex++)
        put(inputImagePlane3, zIndex++)
        hiddenPlanes.forEach {
            put(it, zIndex++)
        }

        put(analogPlane, zIndex++)
        put(button1Plane, zIndex++)
        put(button2Plane, zIndex++)
    }
    val targetConnectionMapping: Map<LayerPlane, List<LayerPlane>> = buildMap<LayerPlane, MutableList<LayerPlane>> {
        computationOrder.forEach {
            put(it, mutableListOf())
        }
        connectionMapping.forEach { (key, value) ->
            value.forEach {
                getValue(it).add(key)
            }
        }
    }


    return TaskNetworkBuilder(
        networkShape,
        connectionMapping,
        targetConnectionMapping,
        planeZMap,
        planeZMap.values.maxOrNull()!!,
        computationOrder,
        outputPlanes,
        inputPlanes
    )
}




class TaskNetworkBuilder(
    val networkShape: NetworkShape,
    val connectionMapping: Map<LayerPlane, List<LayerPlane>>,
    val targetConnectionMapping: Map<LayerPlane, List<LayerPlane>>,
    val planeZMap: Map<LayerPlane, Int>,
    val depth: Int,
    val calculationOrder: List<LayerPlane>,
    val outputPlane: List<LayerPlane>,
    val inputPlane: List<LayerPlane>
) {
    private val idZMap = planeZMap.mapKeys { it.key.id }
    val planes = (connectionMapping.values.flatten() + connectionMapping.keys).distinctBy { it.id }.map {
        val zOrigin = idZMap.getValue(it.id)
        LayerShape3D(it, 0,  0, idZMap.getValue(it.id)) }
    fun build(network: ActivatableNetwork, connectionMagnitude: Float): List<ConnectionLocation> {
        val networkConnectionBuilder = NetworkConnectionBuilder(network, connectionMagnitude, networkShape, depth)
        val connections = connectionMapping.flatMap {  (source, targets) ->
            targets.flatMap { target ->
                networkConnectionBuilder.connectLayerPlanes(LayerShape3D(source, 0, 0, idZMap.getValue(source.id)), LayerShape3D(target, 0, 0, idZMap.getValue(target.id))) }
        }
        return connections
    }
}

data class HyperDimension3D(
    val xMin: Float,
    val xMax: Float,
    val yMin: Float,
    val yMax: Float,
    val zMin: Float,
    val zMax: Float
)

class NetworkConnectionBuilder(
    val network: ActivatableNetwork,
    val connectionMagnitude: Float,
    val shape: NetworkShape,
    val depth: Int,
) {
    val threshold = .3f
    //single structure to reduce object creation
    val input: MutableList<Float> = mutableListOf(0f, 0f, 0f, 0f, 0f, 0f)
    fun connectLayerPlanes(layerSource: LayerShape3D, layerTarget: LayerShape3D): MutableList<ConnectionLocation> {
        val connectionData = mutableListOf<ConnectionLocation>()
        val hyperDimensions = createHyperDimensions()
        val (height, width, _) = layerSource.layerPlane
        val (targetHeight, targetWidth, _) = layerTarget.layerPlane
        val z = layerSource.zOrigin
        val zTarget = layerTarget.zOrigin
        val totalHyperXDistance = (hyperDimensions.xMax - hyperDimensions.xMin)
        val totalHyperYDistance = (hyperDimensions.yMax - hyperDimensions.yMin)
        val totalHyperZDistance = (hyperDimensions.zMax - hyperDimensions.zMin)
//        log.info { hyperDimensions }
//        log.info { "$totalHyperXDistance, $totalHyperYDistance, $totalHyperZDistance" }

        val hyperZ = ((z / depth.toFloat()) * totalHyperYDistance) + hyperDimensions.yMin
        val hyperTargetZ = ((zTarget / depth.toFloat()) * totalHyperZDistance) + hyperDimensions.yMin
        input[2] = hyperZ
        input[5] = hyperTargetZ
//        print("Creating connections between: 0 - $width, 0 - $height to 0 - $targetWidth, 0 - $targetHeight")
        for (x in (0 until width) step 1) {
            for (y in (0 until height) step 1) {
                val hyperX = ((x / width.toFloat()) * totalHyperXDistance) + hyperDimensions.xMin
                val hyperY = ((y / height.toFloat()) * totalHyperYDistance) + hyperDimensions.yMin
                input[0] = hyperX
                input[1] = hyperY
                for (xTarget in (0 until targetWidth) step 1) {
                    for (yTarget in (0 until targetHeight) step 1) {
                        val hyperTargetX = ((xTarget / targetWidth.toFloat()) * totalHyperXDistance) + hyperDimensions.xMin
                        val hyperTargetY = ((yTarget / targetHeight.toFloat()) * totalHyperYDistance) + hyperDimensions.yMin

                        input[3] = hyperTargetX
                        input[4] = hyperTargetY
                        network.evaluate(input)
                        val weight = network.output()[0]
                        val expressValue = network.output()[1]
//                        if (z == 8 && zTarget == 7) println(ConnectionLocation(x, y, z, xTarget, yTarget, zTarget, weight * connectionMagnitude))
//                        if (weight.absoluteValue > threshold) {
                        if (expressValue > 0) {
//                            val sign = weight.sign
//                            val threshWeight =
//                                (weight.absoluteValue - threshold) / ((1f - threshold)) * connectionMagnitude * sign
                            connectionData.add(ConnectionLocation(x, y, z, xTarget, yTarget, zTarget, weight * connectionMagnitude))
                        }
                    }
                }
            }
        }
        return connectionData
    }

    private fun createHyperDimensions(): HyperDimension3D {
        fun Int.toNegativeFloat() = this.toFloat() * -1
        return HyperDimension3D(shape.width.toNegativeFloat(), shape.width.toFloat(), shape.height.toNegativeFloat(), shape.height.toFloat(), shape.depth.toNegativeFloat(), shape.depth.toFloat())
    }

}

fun buildHyperTaskNetwork(
    networkShape: NetworkShape,
    connectionMapping: Map<LayerPlane, List<LayerPlane>>,
    planeZMap: Map<LayerPlane, Int>
) {

}

interface DimensionLocation {
    val size: Int
}

data class DimensionDescription(
    val dimensionLocation: DimensionLocation,
)

data class TaskNetworkDefinition(
    val connectionThreshold: Float,
    val connectionMagnitude: Float,
    val dimensions: List<List<Int>>,
    val normalizeFactors: List<Int>,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val dimensionMin: Float = -1f,
    val dimensionMax: Float = 1f,
)

fun createTaskNetwork2(
    network: ActivatableNetwork,
    modelIndex: String,
    taskNetworkDefinition: TaskNetworkDefinition
): NetworkDescription {
    val numberOfDimensions = taskNetworkDefinition.normalizeFactors.size
    require(taskNetworkDefinition.dimensions.all { numberOfDimensions == it.size })
    val connectionSet = mutableSetOf<ConnectionLocation>()
    val normalizeFactors = taskNetworkDefinition.normalizeFactors

    val input = mutableListOf<Float>()
    repeat(numberOfDimensions * 2) { _ -> input += 0f }

    for (d in taskNetworkDefinition.dimensions) {

    }
    TODO()
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
        SpeciationController(speciesId)
    val adjustedFitnessCalculation =
        adjustedFitnessCalculation(speciationController, distanceFunction, shFunction)
    val speciesLineage = SpeciesLineage()
    val scoreKeeper = SpeciesScoreKeeper()
    val weightedReproduction = weightedReproduction(
        mutationEntries = mutationEntries,
        mateChance = mateChance,
        survivalThreshold = survivalThreshold,
        speciesScoreKeeper = scoreKeeper,
        stagnation = stagnation,
        championThreshold = 5
    )
    val generation = 0
    val populationEvolver = PopulationEvolver(
        generation,
        speciationController,
        scoreKeeper,
        speciesLineage,
        weightedReproduction,
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
    for (a in 0 until 10 step 1) {
        print(a)
    }
}