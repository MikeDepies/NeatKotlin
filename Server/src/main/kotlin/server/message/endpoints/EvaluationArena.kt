package server.message.endpoints

import FrameOutput
import FrameUpdate
import mu.KotlinLogging
import neat.ActivatableNetwork
import neat.FitnessModel
import neat.model.NeatMutator
import neat.toNetwork
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private val log = KotlinLogging.logger { }

data class EvaluationData(val simulationFrameData: SimulationFrameData)

private fun List<Float>.toFrameOutput(): FrameOutput {
    fun bool(index: Int) = get(index).let {
        when {
            it.isNaN() -> false
            else -> it.roundToInt() > 0
        }
    }
    fun clamp(index: Int) = get(index).let {
        when {
            it < 0 -> 0f
            it > 1 -> 1f
            it.isNaN() -> 0f
            it.isInfinite() -> 1f
            else -> it
        }
    }
    val leftShoulderActivation = clamp(8)
    return FrameOutput(
        a = bool(0),
        b = bool(1),
        y = bool(2),
        z = bool(3),
        cStickX = clamp(4),
        cStickY = clamp(5),
        mainStickX = clamp(6),
        mainStickY = clamp(7),
        leftShoulder = when {
            leftShoulderActivation > .8f -> 1f
            leftShoulderActivation < .2f -> 0f
            else -> ((leftShoulderActivation - .2f)/.6f)
        },
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
            activeAgent = neatMutator.toNetwork()
            evaluateModel(neatMutator, block)
        }
    }

    suspend fun evaluateModel(
        model: NeatMutator,
        block: suspend SimulationState.(SimulationFrameData) -> Unit
    ): FitnessModel<NeatMutator> {
        log.info { "Evaluation for new model has begun" }
        val simulationState = SimulationState(
            lastAiStock = lastFrame?.player1?.stock ?: 4,
            lastOpponentStock = lastFrame?.player2?.stock ?: 4,
            lastPercent = lastFrame?.player1?.percent ?: 0,
            lastOpponentPercent = lastFrame?.player2?.percent ?: 0,
            timeGainMax = 1f,
            timeAvailable = 1.5f,
            damageTimeFrame = 1.5f,
            stockTakeTimeFrame = 5f,
            agentStart = Instant.now(),
            baseCumulativeDamageDealt = 8f
        )
        while (true) {
            if (resetSimulationForAgent) {
                resetSimulationForAgent = false
                simulationState.reset(lastFrame)
            }

            val simulationFrame = simulationState.createSimulationFrame(lastFrame)

            with(simulationState) {
                block(simulationFrame)
                update(simulationFrame)
                if ((finished(simulationFrame) || brokenNetwork) && !pauseSimulation) {
                    if (brokenNetwork) {
                        brokenNetwork = false
                        log.info { "Killing broken network" }
                        return FitnessModel(model, -1f)
                    } else {
                        val score = if (cumulativeDamageDealt < 8) 0f else cumulativeDamageDealt.pow(2)
                        val cumulativeDmgRatio = max(cumulativeDamageDealt, 1f) / max(cumulativeDamageTaken/4f, 1f)
                        val scoreWithPercentRatioModifier = score * cumulativeDmgRatio
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
                """.trimIndent()
                        }
                        return FitnessModel(model, scoreWithPercentRatioModifier)
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