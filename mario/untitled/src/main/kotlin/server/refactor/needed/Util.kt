package server.refactor.needed

import MessageWriter
import PopulationEvolver
import UserRef
import createMutationDictionary
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.novelty.NoveltyArchive
import org.jetbrains.exposed.sql.Database
import server.*
import server.message.BroadcastMessage
import server.message.TypedUserMessage
import server.message.endpoints.*
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.max

import kotlin.math.min
import kotlin.streams.toList

private val logger = KotlinLogging.logger {  }
val minSpeices = 5
val maxSpecies = 15
val speciesThresholdDelta = .05f
val dist = compatibilityDistanceFunction(2f, 2f, 1f)
val cppnGeneRuler = CPPNGeneRuler(weightCoefficient = 1f, disjointCoefficient = 1f, normalize = 1)
var distanceFunction = cppnGeneRuler::measure
var speciesSharingDistance = .25f
var shFunction = shFunction(speciesSharingDistance)
@Serializable
data class ScoreAndModel(val model: NeatModel, val score: MarioDiscovery, val scoreValue: Float)

@Serializable
data class Settings(val noveltyThreshold: Float)
data class DeadNetwork(val id: String)
data class NetworkWithId(val neatMutator: NeatMutator, val id: String)


fun compatibilityDistanceFunction(c1: Float, c2: Float, c3: Float): DistanceFunction = { a, b ->
    compatibilityDistance(a, b, c1, c2, c3)
}

class CPPNGeneRuler(val weightCoefficient: Float = .5f, val disjointCoefficient: Float = 1f, val normalize: Int) {
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
            parent1.connections.size, parent2.connections.size
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
class KNNNoveltyArchiveWeighted(
    var k: Int,
    val baseK: Int,
    var noveltyThreshold: Float,
    val behaviorFilter: (MarioDiscovery, MarioDiscovery) -> Boolean = { _, _ -> true },
    val behaviorDistanceMeasureFunction: (MarioDiscovery, MarioDiscovery) -> Float
) : NoveltyArchive<MarioDiscovery> {
    override val behaviors = mutableListOf<MarioDiscovery>()
    var maxDiscovery: MarioDiscovery = MarioDiscovery("", 0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, )
    override val size: Int
        get() = behaviors.size

    override fun addBehavior(behavior: MarioDiscovery): Float {
        val distance = measure(behavior)
        if (distance > noveltyThreshold || size == 0) behaviors += behavior
        return distance
    }

    override fun measure(behavior: MarioDiscovery): Float {
        if (maxDiscovery.stageParts < behavior.stageParts) maxDiscovery = behavior
        val expRatio = ((behavior.stageParts).toFloat()) / (maxDiscovery.stageParts)
        val newK = k + (behavior.stageParts.squared() * 20).toInt()
        val distance = behaviors.parallelStream().filter { behaviorFilter(behavior, it) }
            .map { behaviorDistanceMeasureFunction(behavior, it) }.sorted().toList()
            .take(newK).average()
            .toFloat()
        logger.info { "K: $newK" }
        return if (distance.isNaN()) 0f else distance
    }
}

fun MarioDiscovery.toVector() = listOf(
    mushrooms.toFloat() * 5f,
    fireFlowers.toFloat() * 10f,
    coins.toFloat() * 1f,
    score.toFloat() / 100,
    flags.toFloat() * 30f,
    lifes.toFloat() * 5f,
//    life.toFloat() * 100f,
    (xPos.toFloat() / 32),
//    stage.toFloat() * 30,
//    world.toFloat() * 30,
//    ((yPos) / 32).toFloat(),
//    xPos.toFloat(),
//    stageParts.toFloat(),
//    time.toFloat()
//    (min(4f, time.toFloat() / stageParts) * stageParts),
//    xPos.toFloat() / 4f,
//    world.toFloat() * 100f,
//    stage.toFloat() * 100f
)

fun evolve(
    populationEvolver: PopulationEvolver,
    modelScores: List<ModelScore>,
    neatExperiment: NeatExperiment,
    populationSize: Int
): List<NeatMutator> {
    populationEvolver.sortPopulationByAdjustedScore(modelScores)
    populationEvolver.updateScores(modelScores)
    var newPopulation = populationEvolver.evolveNewPopulation(modelScores, neatExperiment)
    populationEvolver.speciationController.speciesSet.forEach { species ->
        val speciesPopulation = populationEvolver.speciationController.getSpeciesPopulation(species)
        populationEvolver.speciesLineage.updateMascot(
            species,
            speciesPopulation.take(max(1, (speciesPopulation.size * .2).toInt())).random()
        )
//        .take(max(1, (speciesPopulation.size * .2).toInt()))
    }
    val mutationEntries = createMutationDictionary()
    while (newPopulation.size < populationSize) {
        newPopulation = newPopulation + newPopulation.random(neatExperiment.random).clone(UUID.randomUUID()).mutateModel(mutationEntries, neatExperiment)
    }
    if (populationEvolver.speciationController.speciesSet.size < minSpeices) {
        if (speciesSharingDistance > speciesThresholdDelta) {
            speciesSharingDistance -= speciesThresholdDelta
        }
    } else if (populationEvolver.speciationController.speciesSet.size > maxSpecies) {
        speciesSharingDistance += speciesThresholdDelta
    }
    logger.info { "Species (${populationEvolver.speciationController.speciesSet.size}) Sharing Function Distance: $speciesSharingDistance" }
    shFunction = neat.shFunction(speciesSharingDistance)
    populationEvolver.speciate(newPopulation, standardCompatibilityTest(shFunction, distanceFunction))
    if (newPopulation.size > populationSize) {
        val dropList = newPopulation.drop(populationSize)
        val speciationController = populationEvolver.speciationController

        speciationController.speciesSet.forEach { species ->
            val speciesPopulation = speciationController.getSpeciesPopulation(species)
            speciesPopulation.filter { it in dropList }.forEach { neatMutator ->

                speciesPopulation.remove(neatMutator)
            }
        }
    }
    return newPopulation.take(populationSize)
}
//
//@Serializable
//data class MarioInfo(
//    val id: String,
//    val coins: Int,
//    val flag_get: Boolean,
//    val life: Int,
//    val score: Int,
//    val stage: Int,
//    val status: String,
//    val time: Int,
//    val world: Int,
//    val x_pos: Int,
//    val y_pos: Int,
//    val dstatus: Int,
//    val dx: Int,
//    val dy: Int,
//    val dtime: Int,
//    val dstage: Int,
//    val dworld: Int,
//    val dlife: Int,
//    val dscore: Int,
//    val dcoins: Int,
//
//    )


@Serializable
data class MarioDiscovery(
    val id: String,
    val life: Int,
    val stage: Int,
    val status: String,
    val time: Int,
    val world: Int,
    val xPos: Int,
    val yPos: Int,
    val score: Int,
    val coins: Int,
    val mushrooms: Int,
    val fireFlowers: Int,
    val flags: Int,
    val stageParts: Int,
    val lifes: Int
)

fun Int.squared() = this * this

fun List<Int>.actionString() = map { it.toChar() }.joinToString("")
private fun Application.connectAndCreateDatabase() {
    launch {
        fun dbProp(propName: String) = environment.config.property("ktor.database.$propName")
        fun dbPropString(propName: String) = dbProp(propName).getString()

        Database.connect(
            url = dbPropString("url"),
            driver = dbPropString("driver"),
            user = dbPropString("user"),
            password = dbPropString("password")
        )
    }
}

fun evaluationContext(
    controllers: List<IOController>, evaluationId: Int
) = EvaluationContext(evaluationId, controllers.map { it.controllerId })

@Serializable
data class EvaluationContext(val evaluationId: Int, val controllers: List<Int>)

class EvaluationMessageProcessor(
    val evaluationChannels: EvaluationChannels,
    val inputChannel: ReceiveChannel<MarioData>,
    val messageWriter: MessageWriter
) {
    suspend fun processOutput(controller: IOController) {
        for (frameOutput in controller.frameOutputChannel) {
            messageWriter.sendAllMessage(
                BroadcastMessage("simulation.frame.output", frameOutput), MarioOutput.serializer()
            )
        }
    }

    suspend fun processPopulation() {
        for (frame in evaluationChannels.populationChannel) {
            messageWriter.sendPlayerMessage(
                userMessage = TypedUserMessage(
                    userRef = UserRef("dashboard"), topic = "simulation.event.population.new", data = frame
                ), serializer = PopulationModels.serializer()
            )

        }
    }

    suspend fun processAgentModel() {
        for (frame in evaluationChannels.agentModelChannel) {
            messageWriter.sendPlayerMessage(
                userMessage = TypedUserMessage(
                    userRef = UserRef("dashboard"), topic = "simulation.event.agent.new", data = frame
                ), serializer = AgentModel.serializer()
            )
        }
    }

    suspend fun processScores() {
        try {
            for (frame in evaluationChannels.scoreChannel) {
                messageWriter.sendPlayerMessage(
                    userMessage = TypedUserMessage(
                        userRef = UserRef("dashboard"), topic = "simulation.event.score.new", data = frame
                    ), serializer = EvaluationScore.serializer()
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Score processor crashed..." }
        }
    }

    suspend fun processFrameData(frameUpdateChannels: List<IOController>) {
        for (frame in inputChannel) {
            //forward to evaluation and broadcast data to dashboard
            frameUpdateChannels.forEach { it.frameUpdateChannel.send(frame) }
//            evaluationChannels.player1.frameUpdateChannel.send(frame)
//            evaluationChannels.player2.frameUpdateChannel.send(frame)
            messageWriter.sendPlayerMessage(
                userMessage = TypedUserMessage(
                    userRef = UserRef("dashboard"), topic = "simulation.event.frame.update", data = frame
                ), serializer = MarioData.serializer()
            )

        }
    }
}

private fun Application.networkEvaluatorOutputBridgeLoop(
    evaluationMessageProcessor: EvaluationMessageProcessor, controllers: List<IOController>
) {

    controllers.forEach {
        launch { evaluationMessageProcessor.processOutput(it) }
    }
    launch { evaluationMessageProcessor.processFrameData(controllers) }
//    launch { evaluationMessageProcessor.processEvaluationClocks() }
    launch { evaluationMessageProcessor.processPopulation() }
    launch { evaluationMessageProcessor.processAgentModel() }
    launch { evaluationMessageProcessor.processScores() }
}

fun previewMessage(frame: Frame.Text): String {
    val readText = frame.readText()
    val frameLength = readText.length
    return when {
        frameLength < 101 -> readText
        else -> {
            val messagePreview = readText.take(100)
            "$messagePreview...\n[[[rest of message has been trimmed]]]"
        }
    }
}

@Serializable
data class Manifest(val scoreKeeperModel: SpeciesScoreKeeperModel, val scoreLineageModel: SpeciesLineageModel)
