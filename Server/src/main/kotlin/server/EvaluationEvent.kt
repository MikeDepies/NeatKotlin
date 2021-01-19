package server

import FrameOutput
import FrameUpdate
import PopulationEvolver
import io.ktor.application.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import mu.*
import neat.*
import neat.model.*
import org.koin.ktor.ext.*
import server.message.endpoints.*
import java.io.*
import java.time.*
import java.time.format.*
import kotlin.math.*

private val log = KotlinLogging.logger { }

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
    val populationChannel: Channel<PopulationModels>
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
                .toMap { it.id }
        populationChannel.send(PopulationModels(populationMap, populationEvolver.generation))
        val modelScores = currentPopulation.mapIndexed { index, neatMutator ->
            agentModelChannel.send(populationMap.getValue(index))
            val network = neatMutator.toNetwork()
            val evaluator = get<Evaluator>()
            val evaluationScore = evaluate(frameChannel, network, networkOutputChannel, scoreChannel, evaluator)
            FitnessModel(
                model = neatMutator,
                score = evaluationScore.score
            )
        }.toModelScores(adjustedFitnessCalculation)
        populationEvolver.sortPopulationByAdjustedScore(modelScores)
        populationEvolver.updateScores(modelScores)
        var newPopulation = populationEvolver.evolveNewPopulation(modelScores)
        populationEvolver.speciate(newPopulation)
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
    val savePopulationFile = runFolder.resolve("${populationEvolver.generation + 168}.json")
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
    fun evaluateFrame(frameUpdate: FrameUpdate)
    fun finishEvaluation(): EvaluationScore
}

private suspend fun evaluate(
    frameChannel: ReceiveChannel<FrameUpdate>,
    network: ActivatableNetwork,
    networkOutputChannel: SendChannel<FrameOutput>,
    scoreChannel: SendChannel<EvaluationScore>,
    evaluator: Evaluator
): EvaluationScore {
    var lastEvaluationScore = EvaluationScore(0f, listOf())
    suspend fun sendEvaluationScoreUpdate() {
        val evaluationScore = EvaluationScore(evaluator.score, evaluator.scoreContributionList)
        if (evaluationScore != lastEvaluationScore) {
            scoreChannel.send(evaluationScore)
        }
        lastEvaluationScore = evaluationScore
    }

    for (frameUpdate in frameChannel) {
        network.evaluate(frameUpdate.flatten(), true)
        networkOutputChannel.send(network.output().toFrameOutput())
        evaluator.evaluateFrame(frameUpdate)
        sendEvaluationScoreUpdate()
        if (evaluator.isFinished()) {
            evaluator.finishEvaluation()
            return EvaluationScore(evaluator.score, evaluator.scoreContributionList)
        }
    }
    evaluator.finishEvaluation()
    return EvaluationScore(evaluator.score, evaluator.scoreContributionList)
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
            (frames * frameTime) / 1000
        }?.toInt()
    }

    fun miliseconds(simulationFrameData: MeleeFrameData): Long? {
        return frame?.let {
            val frames = simulationFrameData.frame - it
            (frames * frameTime)
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

fun FrameClockFactory.countDownClockSeconds(seconds: Float): CountDownSecondsClock {
    val clock = createClock()
    return CountDownSecondsClock(clock, seconds)
}

class CountDownSecondsClock(private val clock: FrameClock, val seconds: Float) : CountDownClock {
    override fun start(frameNumber: Int) = clock.start(frameNumber)
    override fun cancel() = clock.reset()
    override fun isFinished(simulationFrameData: MeleeFrameData) =
        clock.seconds(simulationFrameData)?.let { clockSecondsElapsed -> clockSecondsElapsed > seconds }
            ?: false

    override fun isCanceled(simulationFrameData: MeleeFrameData) = clock.seconds(simulationFrameData) == null
    override fun toFrameLength(): Int {
        return ceil(((seconds * 1000) / clock.frameTime)).toInt()
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
    override fun start(frameNumber: Int) {
        log.info { "$clockName is being started on frame $frameNumber for ${countDownClock.toFrameLength()} frames." }
        countDownClock.start(frameNumber)
    }

    override fun cancel() {
        log.info { "$clockName is being canceled on ${toFrameLength() - (startFrame ?: 0)} clock frame of ${countDownClock.toFrameLength()} frames." }
        countDownClock.cancel()
    }

    override fun isFinished(simulationFrameData: MeleeFrameData): Boolean {
        val finished = countDownClock.isFinished(simulationFrameData)
        log.info { "$clockName finished with a timer frame length of ${toFrameLength()}." }
        return finished
    }
}

fun FrameClockFactory.countDownClockMilliseconds(milliseconds: Long): CountDownClockMilliseconds {
    val clock = createClock()
    return CountDownClockMilliseconds(clock, milliseconds)
}

interface CountDownClock {
    fun start(frameNumber: Int): Unit
    fun cancel(): Unit
    fun isFinished(simulationFrameData: MeleeFrameData): Boolean
    fun isCanceled(simulationFrameData: MeleeFrameData): Boolean
    fun toFrameLength(): Int
    val startFrame: Int?
}

class CountDownClockMilliseconds(private val clock: FrameClock, val milliseconds: Long) : CountDownClock {
    override fun start(frameNumber: Int) = clock.start(frameNumber)
    override fun cancel() = clock.reset()
    override fun isFinished(simulationFrameData: MeleeFrameData) =
        clock.miliseconds(simulationFrameData)?.let { clockSecondsElapsed -> clockSecondsElapsed > milliseconds }
            ?: false

    override fun isCanceled(simulationFrameData: MeleeFrameData) = clock.seconds(simulationFrameData) == null
    override fun toFrameLength(): Int {
        return ceil((milliseconds / clock.frameTime)).toInt()
    }

    override val startFrame: Int?
        get() = clock.frame
}