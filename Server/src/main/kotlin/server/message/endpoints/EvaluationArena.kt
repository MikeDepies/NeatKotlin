package server.message.endpoints

import FrameOutput
import FrameUpdate
import mu.*
import neat.*
import neat.model.*
import java.time.*
import kotlin.math.*

private val log = KotlinLogging.logger { }

data class EvaluationData(val simulationFrameData: SimulationFrameData)

private fun List<Float>.toFrameOutput(): FrameOutput {
    fun bool(index: Int) = get(index).roundToInt() > 0
    fun clamp(index: Int) = get(index).let { if (it < 0) 0f else if (it > 1) 1f else it }
    return FrameOutput(
        a = bool(0),
        b = bool(1),
        y = bool(2),
        z = bool(3),
        cStickX = clamp(4),
        cStickY = clamp(5),
        mainStickX = clamp(6),
        mainStickY = clamp(7),
        leftShoulder = if (clamp(8).roundToInt() == 1) (clamp(8) - .5f) * 2 else 0f,
        rightShoulder = 0f//clamp(9)
    )
}

class EvaluationArena() {
    var activeModel: NeatMutator? = null
    var activeAgent: ActivatableNetwork? = null

    //    var evaluationController: EvaluationController? = null
    var lastFrame: FrameUpdate? = null
    var lastAgent: ActivatableNetwork? = null
    var pauseSimulation = false
    var resetSimulationForAgent = false
    var brokenNetwork = false
    suspend fun evaluatePopulation(
        population: List<NeatMutator>,
        block: suspend SimulationState.(SimulationFrameData) -> Unit
    ): List<FitnessModel<NeatMutator>> {
        val size = population.size
        log.info { "Starting to evaluate population($size)" }
        return population.mapIndexed { index, neatMutator ->
            log.info { "${index + 1} / $size" }
            evaluateModel(neatMutator, block)
        }
    }

    suspend fun evaluateModel(
        model: NeatMutator,
        block: suspend SimulationState.(SimulationFrameData) -> Unit
    ): FitnessModel<NeatMutator> {
        log.info { "Evaluation for new model has begun" }
        val simulationState = SimulationState(
            lastFrame?.player1?.stock ?: 4,
            lastFrame?.player2?.stock ?: 4,
            lastFrame?.player1?.percent ?: 0,
            lastFrame?.player2?.percent ?: 0,
            agentStart = Instant.now()
        )
        while (true) {
            if (resetSimulationForAgent) {
                resetSimulationForAgent = false
                simulationState.reset(lastFrame)
            }

            val simulationFrame = simulationState.createSimulationFrame(lastFrame)
            block(simulationState, simulationFrame)
            with(simulationState) {
                if ((finished(simulationFrame) || brokenNetwork) && !pauseSimulation) {
                    if (brokenNetwork) {
                        brokenNetwork = false
                        log.info { "Killing broken network" }
                        FitnessModel(model, -1f)
                    } else {
                        val score = if (cumulativeDamageDealt < 8) 0f else cumulativeDamageDealt.pow(2)
                        val cumulativeDmgRatio = cumulativeDamageDealt / max(cumulativeDamageTaken, 1f)
                        val scoreWithPercentRatioModifier = score * cumulativeDmgRatio
                        val stockKeptBonusScore =
                            if (!simulationFrame.stockLoss) scoreWithPercentRatioModifier + 0 else scoreWithPercentRatioModifier
                        log.info {
                            """
                    timeGain: $distanceTimeGain
                    timeElapsed: ${Duration.between(agentStart, simulationFrame.now).seconds}
                    damageTaken: $cumulativeDamageTaken ($cumulativeDmgRatio)
                    damageDone: $cumulativeDamageDealt (${simulationFrame.wasDamageDealt})
                    earlyKill: $earlyKillBonusApplied
                    graceHit: $bonusGraceDamageApplied
                    stockLost: ${simulationFrame.stockLoss}
                    score: $score
                    percentModifierScore: $scoreWithPercentRatioModifier
                    stockKeptScore: $stockKeptBonusScore
                """.trimIndent()
                        }
                        FitnessModel(model, stockKeptBonusScore)
                    }
                }
            }
        }
    }


    suspend fun processFrame(frameUpdate: FrameUpdate): FrameOutput? {
        val agent = activeAgent
        if (lastAgent != agent) {
            lastAgent = agent
        }
        this.lastFrame = frameUpdate
        if (agent != null && !pauseSimulation) {
            try {
                agent.evaluate(frameUpdate.flatten(), true)
                return agent.output().toFrameOutput()
            } catch (e: Exception) {
                brokenNetwork = true
                log.error(e) { "failed to evaluate agent" }
            }
        }
        return null
    }

    fun resetEvaluation() {
        lastFrame?.let {
            log.info { "Reset eval controller - new match" }
            resetSimulationForAgent = true
        }
    }

    fun pause() {
        pauseSimulation = true
    }

    fun resume() {
        pauseSimulation = false
    }
}