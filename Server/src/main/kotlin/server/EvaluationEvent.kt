package server

import FrameOutput
import FrameUpdate
import PopulationEvolver
import io.ktor.application.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import org.koin.core.parameter.DefinitionParameters
import org.koin.ktor.ext.get
import server.message.endpoints.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.set
import kotlin.math.ceil

private val logger = KotlinLogging.logger { }

interface EvaluationEvent
sealed class CoreEvaluationEvent : EvaluationEvent
object DamageTaken : CoreEvaluationEvent()
object DamageDealt : CoreEvaluationEvent()
object StockLost : CoreEvaluationEvent()
object StockTaken : CoreEvaluationEvent()
object Landed : CoreEvaluationEvent()
object KnockedInAir : CoreEvaluationEvent()
object ActionChange : CoreEvaluationEvent()
object GameStart : CoreEvaluationEvent()
object GameEnd : CoreEvaluationEvent()

/*
These events correspond to a given snapshot of the game. (delta?) Various queries can be then made on top of the snapshot,
 in order to answer various conditions. Lastly there will be actions that can take place given an event and condition pass.
 The action includes: ScoreModification (variables ing general?), Clock Controls, EndAgentTurn
 */
typealias SimulationSnapshot = SimulationState

data class EvaluationChannels(
    val frameUpdateChannel: ReceiveChannel<FrameUpdate>,
    val frameOutputChannel: Channel<FrameOutput>,
    val scoreChannel: Channel<EvaluationScore>,
    val agentModelChannel: Channel<AgentModel>,
    val populationChannel: Channel<PopulationModels>,
    val clockChannel: Channel<EvaluationClocksUpdate>,
)

interface EvaluationQuery {
    fun MeleeFrameData.lastPlayersDamageTaken(): List<PlayerNumber>
    fun MeleeFrameData.lastPlayersDamageDealt(): List<PlayerNumber>
    fun MeleeFrameData.lastPlayersStockLost(): List<PlayerNumber>
    fun MeleeFrameData.lastPlayersStockTaken(): List<PlayerNumber>
}

suspend fun Application.evaluationLoop(
    initialPopulation: List<NeatMutator>,
    populationEvolver: PopulationEvolver,
    adjustedFitnessCalculation: AdjustedFitnessCalculation,
    evaluationChannels: EvaluationChannels
) {
    //Extract to koin
    //hook up websockets
    val (frameChannel, networkOutputChannel, scoreChannel, agentModelChannel, populationChannel) = evaluationChannels
    var currentPopulation = initialPopulation
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = File("runs/run-${LocalDateTime.now().format(format)}")
    runFolder.mkdirs()
    while (true) {
        writeGenerationToDisk(currentPopulation, runFolder, populationEvolver)
        val populationMap =
            currentPopulation.mapIndexed { index, neatMutator ->
                val species = populationEvolver.speciationController.species(neatMutator).id
                AgentModel(index, species, neatMutator.toModel())
            }

        populationChannel.send(PopulationModels(populationMap, populationEvolver.generation))
        val modelScores = currentPopulation.mapIndexed { index, neatMutator ->
            logger.info { "New Agent (${index + 1} / ${currentPopulation.size})" }
            agentModelChannel.send(populationMap[index])
            val network = neatMutator.toNetwork()
            val evaluator = get<Evaluator>(parameters = { DefinitionParameters(listOf(index)) })
            val evaluationScore = evaluate(index, frameChannel, network, networkOutputChannel, scoreChannel, evaluator)
            logger.info { "Score: ${evaluationScore.score}" }
            FitnessModel(
                model = neatMutator,
                score = evaluationScore.score
            )
        }.toModelScores(adjustedFitnessCalculation)
        populationEvolver.sortPopulationByAdjustedScore(modelScores)
        populationEvolver.updateScores(modelScores)
        var newPopulation = populationEvolver.evolveNewPopulation(modelScores)
        populationEvolver.speciate(newPopulation)
        logger.info { "Species Count: ${populationEvolver.speciesLineage.species.size}" }
        while (newPopulation.size < currentPopulation.size) {
            newPopulation = newPopulation + newPopulation.first().clone()
        }
        currentPopulation = newPopulation
    }
}

private fun writeGenerationToDisk(
    currentPopulation: List<NeatMutator>,
    runFolder: File,
    populationEvolver: PopulationEvolver
) {
    val modelPopulationPersist = currentPopulation.toModel()
    val savePopulationFile = runFolder.resolve("${populationEvolver.generation + 0}.json")
    val json = Json { prettyPrint = true }
    val encodedModel = json.encodeToString(modelPopulationPersist)
    savePopulationFile.bufferedWriter().use {
        it.write(encodedModel)
        it.flush()
    }
    val manifestFile = runFolder.resolve("manifest.json")
    val manifestData = Manifest(
        populationEvolver.scoreKeeper.toModel(),
        populationEvolver.speciesLineage.toModel()
    )
    manifestFile.bufferedWriter().use {
        it.write(json.encodeToString(manifestData))
        it.flush()
    }
}

interface Evaluator {
    val score: Float
    val scoreContributionList: List<EvaluationScoreContribution>
    fun isFinished(): Boolean
    suspend fun evaluateFrame(frameUpdate: FrameUpdate)
    fun finishEvaluation(): EvaluationScore
}

private suspend fun evaluate(
    agentId: Int,
    frameChannel: ReceiveChannel<FrameUpdate>,
    network: ActivatableNetwork,
    networkOutputChannel: SendChannel<FrameOutput>,
    scoreChannel: SendChannel<EvaluationScore>,
    evaluator: Evaluator
): EvaluationScore {
    var lastEvaluationScore = EvaluationScore(-1, 0f, listOf())
    suspend fun sendEvaluationScoreUpdate() {
        val evaluationScore = EvaluationScore(agentId, evaluator.score, evaluator.scoreContributionList)
        if (evaluationScore != lastEvaluationScore) {
//            logger.info { "sending evalScore: ${evaluationScore}" }
            scoreChannel.send(evaluationScore)
            lastEvaluationScore = evaluationScore
        }
    }
    try {
        for (frameUpdate in frameChannel) {
            network.evaluate(frameUpdate.flatten2(), true)
            networkOutputChannel.send(network.output().toFrameOutput())
            evaluator.evaluateFrame(frameUpdate)
            sendEvaluationScoreUpdate()
            if (evaluator.isFinished()) {
                scoreChannel.send(evaluator.finishEvaluation())
                delay(200)
                return EvaluationScore(agentId, evaluator.score, evaluator.scoreContributionList)
            }
        }
    } catch (e: Exception) {
        logger.error { "failed to build unwrap network properly - killing it" }
        val evaluationScore = EvaluationScore(agentId, evaluator.score, evaluator.scoreContributionList)
        scoreChannel.send(evaluationScore)
        return evaluationScore
    }
    scoreChannel.send(evaluator.finishEvaluation())
    return EvaluationScore(agentId, evaluator.score, evaluator.scoreContributionList)
}
typealias PlayerNumber = Int

class BaseEvaluationQuery(val simulationState: SimulationSnapshot) : EvaluationQuery {
    override fun MeleeFrameData.lastPlayersDamageTaken(): List<PlayerNumber> = with(simulationState) {
        val players = mutableListOf<Int>()
        if (lastPercent < player1.percentFrame) players.add(0)
        if (lastOpponentPercent < player2.percentFrame) players.add(1)
        return players
    }

    override fun MeleeFrameData.lastPlayersDamageDealt(): List<PlayerNumber> = with(simulationState) {
        val players = mutableListOf<Int>()
        if (lastPercent < player1.percentFrame) players.add(1)
        if (lastOpponentPercent < player2.percentFrame) players.add(0)
        return players
    }

    override fun MeleeFrameData.lastPlayersStockLost(): List<PlayerNumber> = with(simulationState) {
        val players = mutableListOf<Int>()
        if (lastAiStock > player1.stockCount) players.add(0)
        if (lastOpponentStock > player2.stockCount) players.add(1)
        return players
    }

    override fun MeleeFrameData.lastPlayersStockTaken(): List<PlayerNumber> = with(simulationState) {
        val players = mutableListOf<Int>()
        if (lastAiStock > player1.stockCount) players.add(1)
        if (lastOpponentStock > player2.stockCount) players.add(0)
        return players
    }
}

typealias EvaluationEventHandler = EvaluationQuery.(MeleeFrameData) -> Unit

enum class EvaluationPhase {
    PreFrame, PostFrame
}

class EvaluationDirector(clockFactory: FrameClockFactory, evaluationTerminationPredicate: () -> Boolean) {
    val eventMap = mutableMapOf<EvaluationEvent, MutableList<EvaluationEventHandler>>()
    val phaseMap = mutableMapOf<EvaluationPhase, MutableList<EvaluationEventHandler>>()
    fun onEvent(evaluationEvent: EvaluationEvent, eventHandler: EvaluationEventHandler) {
        if (!eventMap.containsKey(evaluationEvent))
            eventMap[evaluationEvent] = mutableListOf(eventHandler)
        else eventMap.getValue(evaluationEvent).add(eventHandler)
    }
}

fun createEvaluationDirector(frameClockFactory: FrameClockFactory) {
    var cumulativeDamageScore = 16f
    val evaluationDirector = EvaluationDirector(frameClockFactory) { false }
    evaluationDirector.onEvent(DamageDealt) {
        //need to add damage done calculation...
        cumulativeDamageScore += it.player1.damageDone
    }

}

fun comboSequence() = sequence {
    var i = 1
    while (true) {
        repeat(i) {
            yield(i)
        }
        i++
    }
}

class FrameClockFactory(val fps: Float = 60f) {
    val frameTime get() = 1 / fps

    fun createClock(): FrameClock {
        return FrameClock(frameTime)
    }
}

class FrameClock(val frameTime: Float) {
    var frame: Int? = null
    fun seconds(simulationFrameData: MeleeFrameData): Int? {
        return frame?.let {
            val frames = simulationFrameData.frame - it
            (frames * frameTime * 1000) / 1000
        }?.toInt()
    }

    fun miliseconds(simulationFrameData: MeleeFrameData): Long? {
        return frame?.let {
            val frames = simulationFrameData.frame - it
            (frames * frameTime * 1000)
        }?.toLong()
    }

    fun frames(simulationFrameData: MeleeFrameData): Int? {
        return frame?.let { simulationFrameData.frame - it }
    }

    fun start(simulationFrameData: MeleeFrameData) {
        frame = simulationFrameData.frame
    }

    fun start(startFrame: Int) {
        frame = startFrame
    }

    fun reset() {
        frame = null
    }
}

fun FrameClockFactory.countDownClockSeconds(clockId : String, seconds: Float): CountDownSecondsClock {
    val clock = createClock()
    return CountDownSecondsClock(clock, seconds, clockId)
}

class CountDownSecondsClock(private val clock: FrameClock, val seconds: Float, override val clockId: String) : CountDownClock {
    override fun start(frameNumber: Int) = clock.start(frameNumber)
    override fun cancel() = clock.reset()
    override fun isFinished(simulationFrameData: MeleeFrameData) =
        clock.seconds(simulationFrameData)?.let { clockSecondsElapsed -> clockSecondsElapsed >= seconds }
            ?: true

    override fun isCanceled(simulationFrameData: MeleeFrameData) = clock.seconds(simulationFrameData) == null
    override fun toFrameLength(): Int {
        return ceil(((seconds * 1000) / (clock.frameTime * 1000))).toInt()
    }

    override val startFrame: Int?
        get() = clock.frame
}

fun CountDownClock.log(name: String): LogCountDownClock {
    return LogCountDownClock(name, this)
}

class LogCountDownClock(
    private val clockName: String,
    private val countDownClock: CountDownClock
) : CountDownClock by countDownClock {
    var finishedLogged = true
    override fun start(frameNumber: Int) {
        logger.info { "$clockName is being started on frame $frameNumber for ${countDownClock.toFrameLength()} frames." }
        countDownClock.start(frameNumber)
        finishedLogged = false
    }

    override fun cancel() {
        logger.info { "$clockName is being canceled on ${toFrameLength() - (startFrame ?: 0)} clock frame of ${countDownClock.toFrameLength()} frames." }
        countDownClock.cancel()
        finishedLogged = true
    }

    override fun isFinished(simulationFrameData: MeleeFrameData): Boolean {
        val finished = countDownClock.isFinished(simulationFrameData)
        if (finished && !finishedLogged) {
            logger.info { "$clockName finished with a timer frame length of ${toFrameLength()}." }
            finishedLogged = true
        }
        return finished
    }
}

fun FrameClockFactory.countDownClockMilliseconds(clockId : String, milliseconds: Long): CountDownClockMilliseconds {
    val clock = createClock()
    return CountDownClockMilliseconds(clock, milliseconds, clockId)
}

interface CountDownClock {
    val clockId: String
    fun start(frameNumber: Int): Unit
    fun cancel(): Unit
    fun isFinished(simulationFrameData: MeleeFrameData): Boolean
    fun isCanceled(simulationFrameData: MeleeFrameData): Boolean
    fun toFrameLength(): Int
    val startFrame: Int?
}

class CountDownClockMilliseconds(private val clock: FrameClock, val milliseconds: Long, override val clockId: String) : CountDownClock {
    override fun start(frameNumber: Int) = clock.start(frameNumber)
    override fun cancel() = clock.reset()
    override fun isFinished(simulationFrameData: MeleeFrameData) =
        clock.miliseconds(simulationFrameData)?.let { clockSecondsElapsed -> clockSecondsElapsed >= milliseconds }
            ?: true

    override fun isCanceled(simulationFrameData: MeleeFrameData) = clock.seconds(simulationFrameData) == null
    override fun toFrameLength(): Int {
        return ceil((milliseconds / (clock.frameTime * 1000))).toInt()
    }

    override val startFrame: Int?
        get() = clock.frame
}