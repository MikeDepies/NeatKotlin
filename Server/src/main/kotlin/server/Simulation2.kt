package server

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
import neat.ActivatableNetwork
import server.message.endpoints.NeatModel
import java.util.*

private val log = KotlinLogging.logger { }


@Serializable(with = NodeLocationSerializer::class)
data class NodeLocation(val x: Int, val y: Int, val z: Int)

object NodeLocationSerializer : KSerializer<NodeLocation> {
    override fun deserialize(decoder: Decoder): NodeLocation {
        val (x, y, z) = decoder.decodeSerializableValue(delegate)
        return NodeLocation(x, y, z)
    }

    private val delegate = IntArraySerializer()
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
    val id: String,
    val connectionPlanes: List<LayerShape3D>,
    val connectionRelationships: Map<String, List<String>>,
    val targetConnectionMapping: Map<String, List<String>>,
    val calculationOrder: List<String>,
    val species: Int,
    val hiddenNodes: Int,
    val outputLayer: List<String>,
    val inputLayer: List<String>,
    val neatModel: NeatModel,
    val depth: Int,
    val bestModel: Boolean
)

fun layerPlane(height: Int, width: Int, id: String = UUID.randomUUID().toString()): LayerPlane {
    return LayerPlane(height, width, id)
}

@Serializable
data class LayerPlane(val height: Int, val width: Int, val id: String)

@Serializable
data class LayerShape3D(val layerPlane: LayerPlane, val xOrigin: Int, val yOrigin: Int, val zOrigin: Int)
data class NetworkShape(val width: Int, val height: Int, val depth: Int)


fun createNetwork(): TaskNetworkBuilder {
    val networkShape = NetworkShape(1, 1, 1)
//    val inputPlane = layerPlane(4, 14)
    val bias = layerPlane(1, 1)
    val inputPlane = layerPlane(2, 23)
    val inputPlane2 = layerPlane(2, 397)
//    val inputPlaneController = layerPlane(1, 9)
//    val inputPlaneProjectile = layerPlane(8, 8)
//    val inputStage = layerPlane(1, 16)
//    val plane1 = layerPlane(15, 15)
//    val plane2 = layerPlane(15, 15)
//    val plane3 = layerPlane(15, 15)
//    val plane4 = layerPlane(15, 15)
//    val plane5 = layerPlane(15, 15)
    val inputPlanes = buildList<LayerPlane> {
        repeat(10) {
            add(layerPlane(2, 23))
            add(layerPlane(2, 397))
        }
    }
    //listOf(inputPlane/*, inputPlane2, inputPlaneProjectile, inputPlaneController, inputStage*/)
    val hiddenPlanes = (0 until 1).map {
//        if (it < 2) layerPlane(12, 12) else
        layerPlane(6,6)
    }
//    val hiddenPlanes2 = (0 until 2).map {
////        if (it < 2) layerPlane(12, 12) else
//        layerPlane(4,4)
//    }
    val analogPlane = layerPlane(5, 5)
    val analogCPlane = layerPlane(5, 5)
    val button1Plane = layerPlane(1, 8)
    val button2Plane = layerPlane(1, 8)
//    val stateEmbeddingOutput = layerPlane(10, 10)
    val outputPlanes = listOf(analogPlane, analogCPlane, button1Plane, button2Plane)
    val computationOrder = hiddenPlanes + /*hiddenPlanes2 +*/ outputPlanes
    val connectionMapping = buildMap<LayerPlane, List<LayerPlane>> {

        put(inputPlane, hiddenPlanes /*+ outputPlanes*/)
        put(inputPlane2, hiddenPlanes /*+ outputPlanes*/)
        put(bias, hiddenPlanes + outputPlanes)
        inputPlanes.forEach {
            put(it, hiddenPlanes)
        }
        hiddenPlanes.chunked(5).forEach {layerGroup ->
            layerGroup.forEachIndexed { index, layer ->
                if (index == layerGroup.lastIndex)
                    put(layer, outputPlanes)
                else
                    put(layer, layerGroup.drop(index+1) + outputPlanes)
            }
        }

//            hiddenPlanes2.forEachIndexed { index, layer ->
//                if (index == hiddenPlanes2.lastIndex)
//                    put(layer, outputPlanes)
//                else
//                    put(layer, hiddenPlanes2.drop(index+1))
//            }

//        hiddenPlanes.forEachIndexed { index, layerPlane ->
////            if (index > hiddenPlanes.size - 2)
////            .take(1).ifEmpty { outputPlanes }
//            val planes = planeList + outputPlanes
//            put(layerPlane, outputPlanes)
////            else
////                put(layerPlane, planeList.drop(index + 1))
//        }
//        outputPlanes.forEach { outputPlane ->
//            put(outputPlane, planeList + outputPlanes)
//        }
    }
    val planeZMap = buildMap<LayerPlane, Int> {
        var zIndex = 0
        inputPlanes.forEach {
            put(it, zIndex++)
        }
        put(inputPlane, zIndex++)
        put(inputPlane2, zIndex++)
        put(bias, zIndex++)
        hiddenPlanes.forEach {
            put(it, zIndex++)
        }/*
        hiddenPlanes2.forEach {
            put(it, zIndex++)
        }*/
        outputPlanes.forEach {
            put(it, zIndex++)
        }
//        put(analogPlane, zIndex++)
//        put(analogCPlane, zIndex++)
//        put(button1Plane, zIndex++)
//        put(button2Plane, zIndex++)
//        put(stateEmbeddingOutput, zIndex++)
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
        computationOrder/*.reversed()*/,
        outputPlanes,
        listOf(inputPlane, inputPlane2) + bias + inputPlanes
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
    val planes = (connectionMapping.values.flatten() + connectionMapping.keys).distinctBy { it.id }
        .map {
            val zOrigin = idZMap.getValue(it.id)
            LayerShape3D(it, 0, 0, zOrigin)
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
                        log.info { input }
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
