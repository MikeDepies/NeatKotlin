package server

import PopulationEvolver
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
    val x1: Int, val y1: Int, val z1: Int, val x2: Int, val y2: Int, val z2: Int, val weight: Float
)

@Serializable
data class NetworkBlueprint(
    val connections: List<ConnectionLocation>,
    val id: String,
    val connectionPlanes: List<LayerShape3D>,
    val connectionRelationships: Map<String, List<String>>,
    val targetConnectionMapping: Map<String, List<String>>,
    val calculationOrder: List<String>
)

//@Serializable
//data class NetworkDescription(
//    val connections: Collection<ConnectionLocation>,
//    val nodes: Collection<NodeLocation>,
//    val id: String,
//    val bias: NodeLocation? = null,
//    val shapes: List<List<Int>>
//)


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
    val inputImagePlane = layerPlane(1, 143)
    val imagePlane1 = layerPlane(11, 11)
    val imagePlane2 = layerPlane(8, 8)
    val imagePlane3 = layerPlane(8, 8)
    val outputPlane = layerPlane(1, 9)
    val computationOrder = listOf(/*inputImagePlane, controllerPlane,*/ imagePlane1,
        imagePlane2,
        imagePlane3,
        outputPlane
    )
    val connectionMapping = buildMap<LayerPlane, List<LayerPlane>> {
        put(inputImagePlane, listOf(imagePlane1,imagePlane2,imagePlane3,outputPlane))
        put(imagePlane1, listOf(imagePlane1,imagePlane2,imagePlane3,outputPlane))
        put(imagePlane2, listOf(imagePlane1,imagePlane2,imagePlane3,outputPlane))
        put(imagePlane3, listOf(imagePlane1,imagePlane2,imagePlane3,outputPlane))
        put(outputPlane, listOf(imagePlane1,imagePlane2,imagePlane3,outputPlane))

    }
    val planeZMap = buildMap<LayerPlane, Int> {
        put(inputImagePlane, 0)

        put(imagePlane1, 1)

        put(imagePlane2, 2)

        put(imagePlane3, 3)

        put(outputPlane, 4)
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
        computationOrder
    )
}

class TaskNetworkBuilder(
    val networkShape: NetworkShape,
    val connectionMapping: Map<LayerPlane, List<LayerPlane>>,
    val targetConnectionMapping: Map<LayerPlane, List<LayerPlane>>,
    val planeZMap: Map<LayerPlane, Int>,
    val depth: Int,
    val calculationOrder: List<LayerPlane>
) {
    private val idZMap = planeZMap.mapKeys { it.key.id }
    val planes = (connectionMapping.values.flatten() + connectionMapping.keys).distinctBy { it.id }
        .map { LayerShape3D(it, 0, 0, idZMap.getValue(it.id)) }

    fun build(network: ActivatableNetwork, connectionMagnitude: Float): List<ConnectionLocation> {
        val networkConnectionBuilder = NetworkConnectionBuilder(network, connectionMagnitude, networkShape, depth)
        val connections = connectionMapping.flatMap { (source, targets) ->
            targets.flatMap { target ->
                networkConnectionBuilder.connectLayerPlanes(
                    LayerShape3D(source, 0, 0, idZMap.getValue(source.id)),
                    LayerShape3D(target, 0, 0, idZMap.getValue(target.id))
                )
            }
        }
        return connections
    }
}

data class HyperDimension3D(
    val xMin: Float, val xMax: Float, val yMin: Float, val yMax: Float, val zMin: Float, val zMax: Float
)

class NetworkConnectionBuilder(
    val network: ActivatableNetwork,
    val connectionMagnitude: Float,
    val shape: NetworkShape,
    val depth: Int,
) {
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
                        val hyperTargetX =
                            ((xTarget / targetWidth.toFloat()) * totalHyperXDistance) + hyperDimensions.xMin
                        val hyperTargetY =
                            ((yTarget / targetHeight.toFloat()) * totalHyperYDistance) + hyperDimensions.yMin
                        input[3] = hyperTargetX
                        input[4] = hyperTargetY
                        network.evaluate(input)
                        val weight = network.output()[0]
                        val expressValue = network.output()[1]
//                        if (z == 8 && zTarget == 7) println(ConnectionLocation(x, y, z, xTarget, yTarget, zTarget, weight * connectionMagnitude))
                        if (expressValue > 0) {
                            connectionData.add(
                                ConnectionLocation(
                                    x, y, z, xTarget, yTarget, zTarget, weight * connectionMagnitude
                                )
                            )
                        }
                    }
                }
            }
        }
        return connectionData
    }

    private fun createHyperDimensions(): HyperDimension3D {
        fun Int.toNegativeFloat() = this.toFloat() * -1
        return HyperDimension3D(
            shape.width.toNegativeFloat(),
            shape.width.toFloat(),
            shape.height.toNegativeFloat(),
            shape.height.toFloat(),
            shape.depth.toNegativeFloat(),
            shape.depth.toFloat()
        )
    }

}

fun buildHyperTaskNetwork(
    networkShape: NetworkShape, connectionMapping: Map<LayerPlane, List<LayerPlane>>, planeZMap: Map<LayerPlane, Int>
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


//
//fun createSimulation(
//    evaluationId: Int,
//    population: List<NeatMutator>,
//    distanceFunction: DistanceFunction,
//    shFunction: SharingFunction,
//    mateChance: Float,
//    survivalThreshold: Float,
//    stagnation: Int
//): Simulation {
//    val mutationEntries = createMutationDictionary()
//    val speciesId = 0
//    val speciationController = SpeciationController(speciesId)
//    val adjustedFitnessCalculation = adjustedFitnessCalculation(speciationController, distanceFunction, shFunction)
//    val speciesLineage = SpeciesLineage()
//    val scoreKeeper = SpeciesScoreKeeper()
//    val weightedReproduction = weightedReproduction(
//        mutationEntries = mutationEntries,
//        mateChance = mateChance,
//        survivalThreshold = survivalThreshold,
//        speciesScoreKeeper = scoreKeeper,
//        stagnation = stagnation
//    )
//    val generation = 0
//    val populationEvolver = PopulationEvolver(
//        generation,
//        speciationController,
//        scoreKeeper,
//        speciesLineage,
//        weightedReproduction,
//    )
//    return simulation(evaluationId, population, populationEvolver, adjustedFitnessCalculation)
//}

//fun simulation(
//    evaluationId: Int,
//    population: List<NeatMutator>,
//    populationEvolver: PopulationEvolver,
//    adjustedFitnessCalculation: AdjustedFitnessCalculation,
//
//    ): Simulation {
//
//    return Simulation(population, populationEvolver, adjustedFitnessCalculation, evaluationId)
//}
//
//data class Simulation(
//    val initialPopulation: List<NeatMutator>,
//    val populationEvolver: PopulationEvolver,
//    val adjustedFitnessCalculation: AdjustedFitnessCalculation,
//    val evaluationId: Int
//)

fun main() {
    for (a in 0 until 10 step 1) {
        print(a)
    }
}