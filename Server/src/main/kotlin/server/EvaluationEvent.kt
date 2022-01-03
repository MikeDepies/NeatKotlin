package server

import FrameOutput
import FrameUpdate
import PopulationEvolver
import io.ktor.application.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import neat.novelty.NoveltyArchive
import org.koin.core.parameter.DefinitionParameters
import org.koin.ktor.ext.get
import server.message.endpoints.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
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

@Serializable
data class ModelUpdate(val controllerId: Int, val modelId: String)
data class IOController(
    val controllerId: Int, val frameUpdateChannel: Channel<FrameUpdate>,
    val frameOutputChannel: Channel<FrameOutput>,
    val modelChannel: Channel<ModelUpdate>
)

suspend fun IOController.quitMatch() {
    pressStart()
    delay(250)
    frameOutputChannel.send(FrameOutput(controllerId, true, false, false, false, .5f, .5f, .5f, .5f, 1f, 1f, true))
}

private suspend fun IOController.pressStart() {
    frameOutputChannel.send(FrameOutput(controllerId, false, false, false, false, .5f, .5f, .5f, .5f, 0f, 0f, true))
}

object OneController
object TwoController
class Evaluation(val evaluationId: Int, val controllers: List<IOController>)
data class EvaluationChannels(
    val scoreChannel: Channel<EvaluationScore>,
    val agentModelChannel: Channel<AgentModel>,
    val populationChannel: Channel<PopulationModels>,
    val clockChannel: Channel<EvaluationClocksUpdate>,
)


suspend fun Application.evaluationLoop(
    evaluationId: Int,
    initialPopulation: List<NeatMutator>,
    populationEvolver: PopulationEvolver,
    adjustedFitnessCalculation: AdjustedFitnessCalculation,
    evaluationChannels: EvaluationChannels,
    player: IOController
) {
    //Extract to koin
    //hook up websockets
    val (scoreChannel, agentModelChannel, populationChannel) = evaluationChannels

    var currentPopulation = initialPopulation
    val populationSize = initialPopulation.size
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = File("runs/run-${LocalDateTime.now().format(format)}")
    var meleeState: MeleeState? = MeleeState(null)
    runFolder.mkdirs()
    while (true) {
        currentPopulation = currentPopulation.shuffled()
        writeGenerationToDisk(currentPopulation, runFolder, populationEvolver, "${evaluationId}_")
        val populationMap =
            currentPopulation.mapIndexed { index, neatMutator ->
                val species = populationEvolver.speciationController.species(neatMutator).id
                AgentModel(index, species, neatMutator.toModel(), evaluationId, player.controllerId)
            }

        populationChannel.send(PopulationModels(populationMap, populationEvolver.generation, evaluationId))
        val modelScores = currentPopulation.mapIndexed { index, neatMutator ->
            logger.info { "[eval: $evaluationId}] New Agent (${index + 1} / ${currentPopulation.size})" }
            agentModelChannel.send(populationMap[index])
            try {

                val network = neatMutator.toNetwork()
                val evaluator =
                    get<ResourceEvaluator>(parameters = {
                        DefinitionParameters(
                            listOf(
                                EvaluatorIdSet(
                                    evaluationId,
                                    index,
                                    populationEvolver.generation,
                                    player.controllerId
                                ),
                                meleeState,
                                network
                            )
                        )
                    })

                evaluator.noAttackTimerPenaltySeconds = 6 + (populationEvolver.generation / 20)
                val evaluationScore = evaluate(
                    evaluationId,
                    index,
                    player,
                    network,
                    scoreChannel,
                    evaluator
                ) { frameUpdate -> frameUpdate.flatten2() }
                logger.info { "[eval: $evaluationId] Score: ${evaluationScore.score}" }
                FitnessModel(
                    model = neatMutator,
                    score = evaluationScore.score
                )
            } catch (e: Exception) {
                FitnessModel(
                    model = neatMutator,
                    score = 0f
                )
            }
        }.toModelScores(adjustedFitnessCalculation)
        populationEvolver.sortPopulationByAdjustedScore(modelScores)
        populationEvolver.updateScores(modelScores)
        var newPopulation = populationEvolver.evolveNewPopulation(modelScores)

        logger.info { "Species Count: ${populationEvolver.speciesLineage.species.size}" }
        while (newPopulation.size < populationSize) {

            newPopulation = newPopulation + newPopulation.first().clone()
        }
        populationEvolver.speciate(newPopulation)
        if (newPopulation.size > populationSize) {
            val dropList = newPopulation.drop(populationSize)
            val speciationController = populationEvolver.speciationController
            logger.info {
                "Dropping ${dropList.size} models from ${
                    dropList.map { speciationController.species(it) }.distinct()
                }"
            }
            speciationController.speciesSet.forEach { species ->
                val speciesPopulation = speciationController.getSpeciesPopulation(species)
                speciesPopulation.filter { it in dropList }.forEach { neatMutator ->
//                    logger.info { "Removing model from $species since it has been dropped." }
                    speciesPopulation.remove(neatMutator)
                }


            }
        }
        currentPopulation = newPopulation.take(populationSize)
    }
}


suspend fun Application.evaluationLoopNovelty(
    evaluationId: Int,
    initialPopulation: List<NeatMutator>,
    populationEvolver: PopulationEvolver,
    adjustedFitnessCalculation: AdjustedFitnessCalculation,
    evaluationChannels: EvaluationChannels,
    player: IOController,
    noveltyArchive: NoveltyArchive<ActionBehavior>
) {
    val (scoreChannel, agentModelChannel, populationChannel) = evaluationChannels
    var currentPopulation = initialPopulation
    val populationSize = initialPopulation.size
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = File("runs/run-${LocalDateTime.now().format(format)}")
    var meleeState: MeleeState = MeleeState(null)
    runFolder.mkdirs()
    while (true) {
        currentPopulation = currentPopulation.shuffled()
        writeGenerationToDisk(currentPopulation, runFolder, populationEvolver, "${evaluationId}_")
        launch {
            runFolder.resolve("${evaluationId}_noveltyArchive.json").bufferedWriter().use {
                val json = Json { prettyPrint = true }
                it.write(json.encodeToString(noveltyArchive.behaviors))
                it.flush()
            }
        }
        val populationMap =
            currentPopulation.mapIndexed { index, neatMutator ->
                val species = populationEvolver.speciationController.species(neatMutator).id
                AgentModel(index, species, neatMutator.toModel(), evaluationId, player.controllerId)
            }

        populationChannel.send(PopulationModels(populationMap, populationEvolver.generation, evaluationId))
        val modelScores = currentPopulation.mapIndexed { index, neatMutator ->
            logger.info { "[eval: $evaluationId}] New Agent (${index + 1} / ${currentPopulation.size})" }
            agentModelChannel.send(populationMap[index])
            try {

                val network = neatMutator.toNetwork()
                val evaluator = NoveltyEvaluatorMultiBehavior(
                    index,
                    evaluationId,
                    populationEvolver.generation,
                    player.controllerId,
                    meleeState,
                    get()
                )


//                evaluator.noAttackTimerPenaltySeconds = 6 + (populationEvolver.generation / 20)
                val evaluationScore = evaluateNovelty(
                    evaluationId,
                    index,
                    player,
                    network,
                    scoreChannel,
                    evaluator,
                    noveltyArchive
                ) { frameUpdate -> frameUpdate.flatten3() }
                logger.info { "[eval: $evaluationId] Score: ${evaluationScore.score}" }
                FitnessModel(
                    model = neatMutator,
                    score = evaluationScore.score
                )
            } catch (e: Exception) {
                FitnessModel(
                    model = neatMutator,
                    score = 0f
                )
            }
        }.toModelScores(adjustedFitnessCalculation)//.filter { it.fitness > 0f }
        populationEvolver.sortPopulationByAdjustedScore(modelScores)
        populationEvolver.updateScores(modelScores)
        var newPopulation = populationEvolver.evolveNewPopulation(modelScores)

        logger.info { "Species Count: ${populationEvolver.speciesLineage.species.size}" }
        while (newPopulation.size < populationSize) {

            newPopulation = newPopulation + newPopulation.first().clone()
        }
        populationEvolver.speciate(newPopulation)
        if (newPopulation.size > populationSize) {
            val dropList = newPopulation.drop(populationSize)
            val speciationController = populationEvolver.speciationController
            logger.info {
                "Dropping ${dropList.size} models from ${
                    dropList.map { speciationController.species(it) }.distinct()
                }"
            }
            speciationController.speciesSet.forEach { species ->
                val speciesPopulation = speciationController.getSpeciesPopulation(species)
                speciesPopulation.filter { it in dropList }.forEach { neatMutator ->
//                    logger.info { "Removing model from $species since it has been dropped." }
                    speciesPopulation.remove(neatMutator)
                }


            }
        }
        currentPopulation = newPopulation.take(populationSize)
    }
}


private suspend fun evaluateNovelty(
    evaluationId: Int,
    agentId: Int,
    ioController: IOController,
    network: ActivatableNetwork,
    scoreChannel: SendChannel<EvaluationScore>,
    evaluator: NoveltyEvaluatorMultiBehavior,
    noveltyArchive: NoveltyArchive<ActionBehavior>,
    transformToInput: suspend (FrameUpdate) -> List<Float>
): EvaluationScore {

    try {
        var i = 0
        for (frameUpdate in ioController.frameUpdateChannel) {
            val frameAdjustedForController = controllerFrameUpdate(ioController, frameUpdate)
//            network.evaluate(transformToInput(frameUpdate) + lastOutput)
            network.evaluate(transformToInput(frameUpdate))
            val output = network.output()

            ioController.frameOutputChannel.send(output.toFrameOutput(ioController.controllerId))
//            logger.info { "${ioController.controllerId} - $frameUpdate" }

            evaluator.evaluateFrame(frameAdjustedForController)

            if (evaluator.isFinished()) {
                evaluator.finishEvaluation()
                ioController.frameOutputChannel.send(flushControllerOutput(ioController))
                val score = when {
                    !evaluator.score.met -> 0f
                    noveltyArchive.size < 1 -> evaluator.score.behavior.allActions.size.toFloat().also {
                        noveltyArchive.addBehavior(evaluator.score.behavior)
                    }
                    else -> noveltyArchive.addBehavior(evaluator.score.behavior)
                } + evaluator.score.behavior.totalDamageDone / 5
//                logger.trace { "[eval: $evaluationId}] ${ioController.controllerId} - finished evaluating agent #$agentId. Score: ${score}" }
                return EvaluationScore(
                    evaluationId,
                    agentId,
                    score,
                    evaluator.scoreContributionList
                ).also { scoreChannel.send(it) }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "failed to build unwrap network properly - killing it" }
        val evaluationScore = EvaluationScore(evaluationId, agentId, 0f, evaluator.scoreContributionList)
        scoreChannel.send(evaluationScore)
        return evaluationScore
    }
    error("This is wrong...")

}


suspend fun Application.evaluationLoopNoveltyHyperNeat(
    modelManager: ModelManager,
    evaluationId: Int,
    initialPopulation: List<NeatMutator>,
    populationEvolver: PopulationEvolver,
    adjustedFitnessCalculation: AdjustedFitnessCalculation,
    evaluationChannels: EvaluationChannels,
    player: IOController,
    noveltyArchive: NoveltyArchive<ActionBehavior>,

    ) {
    val (scoreChannel, agentModelChannel, populationChannel) = evaluationChannels
    var currentPopulation = initialPopulation.map { NetworkWithId(it, "${UUID.randomUUID()}") }
    val populationSize = initialPopulation.size
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = File("runs/run-${LocalDateTime.now().format(format)}")
    var meleeState: MeleeState = MeleeState(null)
    runFolder.mkdirs()
    while (true) {
        currentPopulation = currentPopulation.shuffled()

        writeGenerationToDisk(
            currentPopulation.map { it.neatMutator },
            runFolder,
            populationEvolver,
            "${evaluationId}_"
        )
        launch {
            runFolder.resolve("${evaluationId}_noveltyArchive.json").bufferedWriter().use {
                val json = Json { prettyPrint = true }
                it.write(json.encodeToString(noveltyArchive.behaviors))
                it.flush()
            }
        }
        val populationMap =
            currentPopulation.mapIndexed { index, (neatMutator, id) ->
                val species = populationEvolver.speciationController.species(neatMutator).id
                //replace index w/ id
                AgentModel(index, species, neatMutator.toModel(), evaluationId, player.controllerId)
            }
        //Updates the register for http requests
        modelManager[player] =
            EvolutionGeneration(
                populationEvolver.generation,
                currentPopulation.associateBy { it.id },
                currentPopulation,
                mutableMapOf(),
                mutableMapOf(),
                mutableMapOf()
            )
        populationChannel.send(PopulationModels(populationMap, populationEvolver.generation, evaluationId))
//        modelManager.setPopulation(player, currentPopulation)
        val modelScores = currentPopulation.mapIndexed { index, (neatMutator, id) ->
            logger.info { "[eval: $evaluationId}] New Agent (${index + 1} / ${currentPopulation.size})" }
            agentModelChannel.send(populationMap[index])
            try {

//                modelManager[player] =
                val evaluator = NoveltyEvaluatorMultiBehavior(
                    index,
                    evaluationId,
                    populationEvolver.generation,
                    player.controllerId,
                    meleeState,
                    get()
                )

                modelManager[player].activeId = id
                player.modelChannel.send(ModelUpdate(player.controllerId, id))
                log.info("Applied active model id for controller ${player.controllerId}")
//                evaluator.noAttackTimerPenaltySeconds = 6 + (populationEvolver.generation / 20)
                val evaluationScore = evaluateNoveltyHyperNeat(
                    evaluationId,
                    index,
                    player,
                    scoreChannel,
                    evaluator,
                    noveltyArchive,
                    id,
                    modelManager
                )
                logger.info { "[eval: $evaluationId] Score: ${evaluationScore.score}" }
                FitnessModel(
                    model = neatMutator,
                    score = evaluationScore.score
                )
            } catch (e: Exception) {
                FitnessModel(
                    model = neatMutator,
                    score = 0f
                )
            }
        }.toModelScores(adjustedFitnessCalculation)//.filter { it.fitness > 0f }
        populationEvolver.sortPopulationByAdjustedScore(modelScores)
        populationEvolver.updateScores(modelScores)
        var newPopulation = populationEvolver.evolveNewPopulation(modelScores)

        logger.info { "Species Count: ${populationEvolver.speciesLineage.species.size}" }
        while (newPopulation.size < populationSize) {

            newPopulation = newPopulation + newPopulation.first().clone()
        }
        populationEvolver.speciate(newPopulation)
        if (newPopulation.size > populationSize) {
            val dropList = newPopulation.drop(populationSize)
            val speciationController = populationEvolver.speciationController
            logger.info {
                "Dropping ${dropList.size} models from ${
                    dropList.map { speciationController.species(it) }.distinct()
                }"
            }
            speciationController.speciesSet.forEach { species ->
                val speciesPopulation = speciationController.getSpeciesPopulation(species)
                speciesPopulation.filter { it in dropList }.forEach { neatMutator ->
//                    logger.info { "Removing model from $species since it has been dropped." }
                    speciesPopulation.remove(neatMutator)
                }


            }
        }
        currentPopulation = newPopulation.take(populationSize).map { NetworkWithId(it, "${UUID.randomUUID()}") }
    }
}

private suspend fun evaluateNoveltyHyperNeat(
    evaluationId: Int,
    agentId: Int,
    ioController: IOController,
    scoreChannel: SendChannel<EvaluationScore>,
    evaluator: NoveltyEvaluatorMultiBehavior,
    noveltyArchive: NoveltyArchive<ActionBehavior>,
    id: String,
    modelManager: ModelManager
): EvaluationScore {

    try {

        var i = 0
        for (frameUpdate in ioController.frameUpdateChannel) {
            val frameAdjustedForController = controllerFrameUpdate(ioController, frameUpdate)
            if (modelManager[ioController].requestedNetwork[id] == true) {
                evaluator.evaluateFrame(frameAdjustedForController)


                if (evaluator.isFinished()) {
                    evaluator.finishEvaluation()
                    val score = when {
                        !evaluator.score.met -> 0f
                        noveltyArchive.size < 1 -> evaluator.score.behavior.allActions.size.toFloat().also {
                            noveltyArchive.addBehavior(evaluator.score.behavior)
                        }
                        else -> noveltyArchive.addBehavior(evaluator.score.behavior)
                    } + evaluator.score.behavior.totalDamageDone / 5 + evaluator.score.behavior.totalDistanceTowardOpponent / 10
                    return EvaluationScore(
                        evaluationId,
                        agentId,
                        score,
                        evaluator.scoreContributionList
                    ).also { scoreChannel.send(it) }
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "failed to build unwrap network properly - killing it" }
        val evaluationScore = EvaluationScore(evaluationId, agentId, 0f, evaluator.scoreContributionList)
        scoreChannel.send(evaluationScore)
        return evaluationScore
    }
    error("This is wrong...")

}

private fun flushControllerOutput(playerController: IOController) =
    FrameOutput(playerController.controllerId, false, false, false, false, .5f, .5f, .5f, .5f, 0f, 0f)

private fun writeGenerationToDisk(
    currentPopulation: List<NeatMutator>,
    runFolder: File,
    populationEvolver: PopulationEvolver,
    prefix: String
) {
    val modelPopulationPersist = currentPopulation.toModel()
    val savePopulationFile = runFolder.resolve("$prefix${populationEvolver.generation + 0}.json")
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

interface Evaluator<T> {
    val score: T
    val scoreContributionList: List<EvaluationScoreContribution>
    fun isFinished(): Boolean
    suspend fun evaluateFrame(frameUpdate: FrameUpdate)
    fun finishEvaluation()
}
//var lastFrame

private suspend fun evaluate(
    evaluationId: Int,
    agentId: Int,
    ioController: IOController,
    network: ActivatableNetwork,
    scoreChannel: SendChannel<EvaluationScore>,
    evaluator: Evaluator<Float>,
    transformToInput: suspend (FrameUpdate) -> List<Float>
): EvaluationScore {
    var lastOutput = (0..8).map { 0f }
//    var lastEvaluationScore = EvaluationScore(-1, 0f, listOf())
    var lastEvaluationScore = EvaluationScore(evaluationId, -1, 0f, listOf())
    suspend fun sendEvaluationScoreUpdate() {
        val evaluationScore = EvaluationScore(evaluationId, agentId, evaluator.score, evaluator.scoreContributionList)
        if (evaluationScore != lastEvaluationScore) {
//            logger.info { "sending evalScore: ${evaluationScore}" }
            scoreChannel.send(evaluationScore)
            lastEvaluationScore = evaluationScore
        }
    }
    try {
        var i = 0
        for (frameUpdate in ioController.frameUpdateChannel) {
            val frameAdjustedForController = controllerFrameUpdate(ioController, frameUpdate)
//            network.evaluate(transformToInput(frameUpdate) + lastOutput)
            network.evaluate(transformToInput(frameUpdate))
            val output = network.output()
            lastOutput = output
            ioController.frameOutputChannel.send(output.toFrameOutput(ioController.controllerId))
//            logger.info { "${ioController.controllerId} - $frameUpdate" }

            evaluator.evaluateFrame(frameAdjustedForController)
            if (i++ % (8) == 0) sendEvaluationScoreUpdate()
            if (evaluator.isFinished()) {
                logger.trace { "[eval: $evaluationId}] ${ioController.controllerId} - finished evaluating agent #$agentId" }
                sendEvaluationScoreUpdate()

                return EvaluationScore(evaluationId, agentId, evaluator.score, evaluator.scoreContributionList)
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "failed to build unwrap network properly - killing it" }
        val evaluationScore = EvaluationScore(evaluationId, agentId, 0f, evaluator.scoreContributionList)
        scoreChannel.send(evaluationScore)
        return evaluationScore
    }
    error("This is wrong...")
    scoreChannel.send(lastEvaluationScore)
    return lastEvaluationScore
}

private fun controllerFrameUpdate(ioController: IOController, frameUpdate: FrameUpdate) =
    when (ioController.controllerId) {
        1 -> {
            frameUpdate.copy(
                player1 = frameUpdate.player2,
                player2 = frameUpdate.player1,
                action1 = frameUpdate.action2,
                action2 = frameUpdate.action1
            )

        }
        else -> {
            frameUpdate
        }
    }
typealias PlayerNumber = Int
//
//class BaseEvaluationQuery(val simulationState: SimulationSnapshot) : EvaluationQuery {
//    override fun MeleeFrameData.lastPlayersDamageTaken(): List<PlayerNumber> = with(simulationState) {
//        val players = mutableListOf<Int>()
//        if (lastPercent < player1.percentFrame) players.add(0)
//        if (lastOpponentPercent < player2.percentFrame) players.add(1)
//        return players
//    }
//
//    override fun MeleeFrameData.lastPlayersDamageDealt(): List<PlayerNumber> = with(simulationState) {
//        val players = mutableListOf<Int>()
//        if (lastPercent < player1.percentFrame) players.add(1)
//        if (lastOpponentPercent < player2.percentFrame) players.add(0)
//        return players
//    }
//
//    override fun MeleeFrameData.lastPlayersStockLost(): List<PlayerNumber> = with(simulationState) {
//        val players = mutableListOf<Int>()
//        if (lastAiStock > player1.stockCount) players.add(0)
//        if (lastOpponentStock > player2.stockCount) players.add(1)
//        return players
//    }
//
//    override fun MeleeFrameData.lastPlayersStockTaken(): List<PlayerNumber> = with(simulationState) {
//        val players = mutableListOf<Int>()
//        if (lastAiStock > player1.stockCount) players.add(1)
//        if (lastOpponentStock > player2.stockCount) players.add(0)
//        return players
//    }
//}
//
//typealias EvaluationEventHandler = EvaluationQuery.(MeleeFrameData) -> Unit

enum class EvaluationPhase {
    PreFrame, PostFrame
}
//
//class EvaluationDirector(clockFactory: FrameClockFactory, evaluationTerminationPredicate: () -> Boolean) {
//    val eventMap = mutableMapOf<EvaluationEvent, MutableList<EvaluationEventHandler>>()
//    val phaseMap = mutableMapOf<EvaluationPhase, MutableList<EvaluationEventHandler>>()
//    fun onEvent(evaluationEvent: EvaluationEvent, eventHandler: EvaluationEventHandler) {
//        if (!eventMap.containsKey(evaluationEvent))
//            eventMap[evaluationEvent] = mutableListOf(eventHandler)
//        else eventMap.getValue(evaluationEvent).add(eventHandler)
//    }
//}
//
//fun createEvaluationDirector(frameClockFactory: FrameClockFactory) {
//    var cumulativeDamageScore = 16f
//    val evaluationDirector = EvaluationDirector(frameClockFactory) { false }
//    evaluationDirector.onEvent(DamageDealt) {
//        //need to add damage done calculation...
//        cumulativeDamageScore += it.player1.damageDone
//    }
//
//}

fun comboSequence() = sequence {
    var i = 1
    while (true) {
        repeat(i) {
//            logger.info { "combo multiplier: $i" }
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
        }?.toInt()?.takeIf { it >= 0 }
    }

    fun miliseconds(simulationFrameData: MeleeFrameData): Long? {
        return frame?.let {
            val frames = simulationFrameData.frame - it
            (frames * frameTime * 1000)
        }?.toLong()?.takeIf { it >= 0 }
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

fun FrameClockFactory.countDownClockSeconds(clockId: String, seconds: Float): CountDownSecondsClock {
    val clock = createClock()
    return CountDownSecondsClock(clock, seconds, clockId)
}

class CountDownSecondsClock(private val clock: FrameClock, val seconds: Float, override val clockId: String) :
    CountDownClock {
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

fun FrameClockFactory.countDownClockMilliseconds(clockId: String, milliseconds: Long): CountDownClockMilliseconds {
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

class CountDownClockMilliseconds(private val clock: FrameClock, val milliseconds: Long, override val clockId: String) :
    CountDownClock {
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