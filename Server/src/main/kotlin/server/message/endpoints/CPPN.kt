package server.message.endpoints

import FrameUpdate
import neat.*
import neat.model.*
import kotlin.math.*

data class NodePosition(val playerLayer: Float, val x: Float, val y: Float, val z: Float)
data class NodePositionOutput(val x: Float, val y: Float, val z: Float)

/*
Produces a connection scheme from an irregular substrate.
 */
fun NeatExperiment.interpretFrameData(meleeFrameData: FrameUpdate, cppn: NeatMutator) {
    fun midPoint(x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> {
        val x = if (x1 < x2) x1 + ((x2 - x1) / 2) else x2 + ((x1 - x2) / 2)
        val y = if (y1 < y2) y1 + ((y2 - y1) / 2) else y2 + ((y1 - y2) / 2)
        return (x) to (y)
    }


    val cellSizeX = 32
    val cellSizeY = 64
    val blastzone = meleeFrameData.stage.blastzone
    val width = blastzone.right + blastzone.left.absoluteValue
    val height = blastzone.top + blastzone.bottom.absoluteValue
    val left = (blastzone.left) / cellSizeX
    val right = (blastzone.right) / cellSizeX
    val top = blastzone.top / cellSizeY
    val bottom = blastzone.bottom / cellSizeY
    val player1X = meleeFrameData.player1.x
    val player1Y = meleeFrameData.player1.y
    val player2X = meleeFrameData.player2.x
    val player2Y = meleeFrameData.player2.y
    val (targetX, targetY) = midPoint(player1X, player1Y, player2X, player2Y)
    val inputs = 0 until 13
    val outputs = 0 until 8
    val network = cppn.toNetwork()
    var collectedSubTargetSpaceNodes = false
    var collectedInputNodes = false
    var collectedControllerOutputNodes = false
    val inputPositionList = mutableSetOf<List<Float>>()
    val nodePositionList = mutableSetOf<List<Float>>()
    val outputNodePositionList = mutableSetOf<List<Float>>()

    val fcPlayerConnections = mutableMapOf<List<Float>, Float>()
    val fcPlayerIOConnections = mutableMapOf<List<Float>, Float>()
    val zLayerFullyConnected = -1f
    val playerLayer1 = 0f
    val playerLayer2 = 1f
    val generateOutput = { x1: Float, y1: Float, x2: Float, y2: Float ->
        outputs.forEach { z2 ->
            val z2Float = z2.toFloat()
            val input = listOf(
                playerLayer1, x1, y1,
                zLayerFullyConnected, x2, y2, z2Float
            )
            val input2 = listOf(
                playerLayer2, x1, y1,
                zLayerFullyConnected, x2, y2, z2Float
            )
            network.eval(input)
            network.eval(input2)
        }
    }
    inputs.map {
        NodeGene(nextNode(), 0f, NodeType.Input, Activation.identity)
    }
    (left.toInt() until right.toInt()).forEach { sX ->
        (bottom.toInt() until top.toInt()).forEach { sY ->
            NodeGene(nextNode(), 0f, NodeType.Hidden, Activation.sigmoidal)
        }
    }

    inputs.forEach { z1 ->
        val z1Float = z1.toFloat()
        if (!collectedInputNodes) {
            inputPositionList += listOf(playerLayer1, player1X, player1Y, z1Float)
            inputPositionList += listOf(playerLayer2, player1X, player1Y, z1Float)
        }
        outputs.forEach { z2 ->
            val z2Float = z2.toFloat()
            if (!collectedControllerOutputNodes) {
                outputNodePositionList += listOf(targetX, targetY, z2Float)
            }
            listOf(playerLayer1, player1X, player1Y, z1Float, targetX, targetY, z2Float)
                .let { input -> fcPlayerIOConnections[input] = network.eval(input)[0] }
            listOf(playerLayer2, player2X, player2Y, z1Float, targetX, targetY, z2Float)
                .let { fcPlayerIOConnections[it] = network.eval(it)[0] }

        }
        collectedControllerOutputNodes = true
        (left.toInt() until right.toInt()).forEach { tX ->
            (bottom.toInt() until top.toInt()).forEach { tY ->
                listOf(playerLayer1, player1X, player1Y, z1Float, tX.toFloat(), tY.toFloat(), zLayerFullyConnected)
                    .let { fcPlayerIOConnections[it] = network.eval(it)[0] }
                listOf(playerLayer2, player2X, player2Y, z1Float, tX.toFloat(), tY.toFloat(), zLayerFullyConnected)
                    .let { fcPlayerIOConnections[it] = network.eval(it)[0] }
            }
        }
    }
    collectedInputNodes = true

    //Connect 'output' substrate together
    fun fcConnectionInput(
        playerLayer: Float,
        sourceXFloat: Float,
        sourceYFloat: Float,
        targetXFloat: Float,
        targetYFloat: Float
    ) = listOf(
        playerLayer,
        sourceXFloat,
        sourceYFloat,
        zLayerFullyConnected,
        targetXFloat,
        targetYFloat,
        zLayerFullyConnected
    )

    (left.toInt() until right.toInt()).forEach { sX ->
        val sourceXFloat = sX.toFloat()
        (bottom.toInt() until top.toInt()).forEach { sY ->
            val sourceYFloat = sY.toFloat()
            if (!collectedSubTargetSpaceNodes) {
                nodePositionList += listOf(playerLayer1, sX.toFloat(), sY.toFloat(), zLayerFullyConnected)
                nodePositionList += listOf(playerLayer2, sX.toFloat(), sY.toFloat(), zLayerFullyConnected)
            }
            (left.toInt() until right.toInt()).forEach { tX ->
                val targetXFloat = tX.toFloat()
                (bottom.toInt() until top.toInt()).forEach { tY ->
                    val targetYFloat = tY.toFloat()
                    val input = fcConnectionInput(playerLayer1, sourceXFloat, sourceYFloat, targetXFloat, targetYFloat)
                    val input2 = fcConnectionInput(playerLayer2, sourceXFloat, sourceYFloat, targetXFloat, targetYFloat)
                    fcPlayerConnections[input] = network.eval(input)[0]
                    fcPlayerConnections[input2] = network.eval(input2)[0]
                }
            }
            outputs.forEach { z2 ->
                val z2Float = z2.toFloat()
                val input = listOf(
                    playerLayer1, sourceXFloat, sourceYFloat,
                    zLayerFullyConnected, targetX, targetY, z2Float
                )
                val input2 = listOf(
                    playerLayer2, sourceXFloat, sourceYFloat,
                    zLayerFullyConnected, targetX, targetY, z2Float
                )
                fcPlayerIOConnections[input] = network.eval(input)[0]
                fcPlayerIOConnections[input] = network.eval(input2)[0]
//                ConnectionGene()
            }
        }
        collectedSubTargetSpaceNodes = true
    }


    //TODO need to order the inputs so they are not interlaced by player. eg: (percent1, perecent2, stock1, stock2, etc.)
    //TODO collect nodes for all hidden
    //TODO construct output nodes
    //Nodes stay between rewriring, or hotwiring of the network.
    val listOfNodes = inputPositionList.map { NodeGene(nextNode(), 0f, NodeType.Input, Activation.identity) }
    //TODO should maintain a separate connection list that can be stamped together with a new input and output sections
    val connections = listOf<ConnectionGene>()

    val simpleNeatMutator = simpleNeatMutator(listOfNodes, connections)

    /**
     * Construct a neat mutator from the queried substrate.
     *  Maintain a way to swap out the input and output module connections
     *  Maintain a cache system maybe for connection weights per location
     * Neat Mutators can be created from this CPPN substrate. In the decoding process, we maintain a meta structure
     * that details the plans to construct a neat mutator.
     *
     * I wanted to conclude about the fact that we can take the produced neat mutator and then do simple direct
     * encoding on top.
     * However, because we are swapping out the input and output connection sets, that network would only optimize
     * one slice of the meta network
     */
}

fun ActivatableNetwork.eval(vararg input: Float) = eval(input.toList())

fun ActivatableNetwork.eval(input: List<Float>): List<Float> {
    evaluate(input)
    return output()
}