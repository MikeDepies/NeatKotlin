package server

import PopulationEvolver
import io.ktor.application.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import mu.*
import neat.*
import neat.model.*
import neat.novelty.*
import server.message.endpoints.*
import java.io.*
import java.time.*
import java.time.format.*

private val logger = KotlinLogging.logger { }

data class IOController(
    val controllerId: Int, val frameUpdateChannel: Channel<MarioData>,
    val frameOutputChannel: Channel<MarioOutput>,
)

object OneController
object TwoController
class Evaluation(val evaluationId: Int, val controllers: List<IOController>)
data class EvaluationChannels(
    val scoreChannel: Channel<EvaluationScore>,
    val agentModelChannel: Channel<AgentModel>,
    val populationChannel: Channel<PopulationModels>,
)

data class ActionBehavior(val events: List<Int>)

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
                val evaluator = NoveltyEvaluator()


//                evaluator.noAttackTimerPenaltySeconds = 6 + (populationEvolver.generation / 20)
                val evaluationScore = evaluateNovelty(
                    evaluationId,
                    index,
                    player,
                    network,
                    scoreChannel,
                    evaluator,
                    noveltyArchive
                ) { frameUpdate -> frameUpdate.flatten() }
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
        var newPopulation = populationEvolver.evolveNewPopulation(modelScores, TODO())

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

private fun MarioData.flatten(): List<Float> {
    TODO("Not yet implemented")
}


private suspend fun evaluateNovelty(
    evaluationId: Int,
    agentId: Int,
    ioController: IOController,
    network: ActivatableNetwork,
    scoreChannel: SendChannel<EvaluationScore>,
    evaluator: Evaluator<MinimaCriteria<ActionBehavior>>,
    noveltyArchive: NoveltyArchive<ActionBehavior>,
    transformToInput: suspend (MarioData) -> List<Float>
): EvaluationScore {

    try {
        var i = 0
        for (frameUpdate in ioController.frameUpdateChannel) {
//            network.evaluate(transformToInput(frameUpdate) + lastOutput)
            network.evaluate(transformToInput(frameUpdate))
            val output = network.output()

            ioController.frameOutputChannel.send(output.toMarioOutput())
//            logger.info { "${ioController.controllerId} - $frameUpdate" }

            evaluator.evaluateFrame(frameUpdate)

            if (evaluator.isFinished()) {
                evaluator.finishEvaluation()
//                ioController.frameOutputChannel.send(flushControllerOutput(ioController))
                val score = when {
                    !evaluator.score.met -> 0f
                    noveltyArchive.size < 1 -> 2f.also {
                        noveltyArchive.addBehavior(evaluator.score.behavior)
                    }
                    else -> noveltyArchive.addBehavior(evaluator.score.behavior)
                }
//                logger.trace { "[eval: $evaluationId}] ${ioController.controllerId} - finished evaluating agent #$agentId. Score: ${score}" }
                return EvaluationScore(
                    evaluationId,
                    agentId,
                    score,
                    listOf()
                ).also { scoreChannel.send(it) }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "failed to build unwrap network properly - killing it" }
        val evaluationScore = EvaluationScore(evaluationId, agentId, 0f, listOf())
        scoreChannel.send(evaluationScore)
        return evaluationScore
    }
    error("This is wrong...")

}

private fun <E> List<E>.toMarioOutput(): MarioOutput {
    TODO()
}


//private fun flushControllerOutput(playerController: IOController) =
//    FrameOutput(playerController.controllerId, false, false, false, false, .5f, .5f, .5f, .5f, 0f, 0f)

 fun writeGenerationToDisk(
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
    fun isFinished(): Boolean
    suspend fun evaluateFrame(frameUpdate: MarioData)
    fun finishEvaluation()
}
