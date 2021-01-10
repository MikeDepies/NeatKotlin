import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.ActivatableNetwork
import neat.AdjustedFitnessCalculation
import neat.FitnessModel
import neat.model.NeatMutator
import neat.toNetwork
import org.koin.core.scope.Scope
import server.message.BroadcastMessage
import server.message.endpoints.EndpointProvider
import java.time.Duration
import java.time.Instant
import java.time.Instant.now
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val log = KotlinLogging.logger { }
var receivedAnyMessages = false
fun EndpointProvider.simulationEndpoints() = sequence<SimpleMessageEndpoint<*, *>> {
    registerEndpoint<FrameUpdate, SimulationSessionScope>("simulation.frame.update") {
        val evaluationArena = get<EvaluationArena>()
//        log.info { "${it.data}" }

        evaluationArena.processFrame(it.data)?.let { frameOutput ->
            messageWriter.sendAllMessage(
                BroadcastMessage("simulation.frame.output", frameOutput),
                FrameOutput.serializer()
            )
        }

        receivedAnyMessages = true
//        messageWriter.sendAllMessage()
    }

    registerEndpoint<NoData, SimulationSessionScope>("simulation.reset.game") {
        val evaluationArena = get<EvaluationArena>().resetEvaluation()
    }


    registerEndpoint<NoData, SimulationSessionScope>("simulation.pause") {
        val evaluationArena = get<EvaluationArena>()
        val sim = get<Simulation>()
        evaluationArena.pause()
    }

    registerEndpoint<NoData, SimulationSessionScope>("simulation.resume") {
        val evaluationArena = get<EvaluationArena>()
        evaluationArena.resetEvaluation()
        evaluationArena.resume()
    }
}

@Serializable
object NoData


class SimulationSessionScope(override val scope: Scope, override val messageWriter: MessageWriter) : SessionScope

data class Simulation(
    val initialPopulation: List<NeatMutator>,
    val evaluationArena: EvaluationArena,
    val populationEvolver: PopulationEvolver,
    val adjustedFitnessCalculation: AdjustedFitnessCalculation
)

class EvaluationController(
    val agentStart: Instant,
    val player1: PlayerDataUpdate,
    val player2: PlayerDataUpdate,
    var distance: Float
)

class EvaluationArena() {
    var activeModel: NeatMutator? = null
    var activeAgent: ActivatableNetwork? = null
    var evaluationController: EvaluationController? = null
    var lastFrame: FrameUpdate? = null
    var lastAgent: ActivatableNetwork? = null
    var pauseSimulation = false
    var resetSimulationForAgent = false
    var brokenNetwork = false
    suspend fun evaluatePopulation(population: List<NeatMutator>): List<FitnessModel<NeatMutator>> {
        val size = population.size
        log.info { "Starting to evaluate population($size)" }
        return population.mapIndexed { index, neatMutator ->
            log.info { "${index + 1} / $size" }
            evaluateModel(neatMutator)
        }
    }

    fun gracePeriodHitBonus(t: Float, gracePeriod: Float, bonus: Int = 20): Float {
        return bonus * (1 - (t / gracePeriod))
    }

    suspend fun evaluateModel(model: NeatMutator): FitnessModel<NeatMutator> {
        val agent = model.toNetwork().also { activeAgent = it; activeModel = model }
        log.info { "Evaluation for new model has begun" }
        evaluationController =
            EvaluationController(now(), lastFrame!!.player1, lastFrame!!.player2, evaluationController?.distance ?: 0f)
        var damageDoneTime: Instant? = null
        var stockTakenTime: Instant? = null
        var stockLostTime: Instant? = null
        var distanceTime: Instant? = null
        var doubleDeathQuick = false
        val timeAvailable = .5f
        var damageTimeFrame = 1.5f
        var timeGainMax = 5f
        val stockTakeTimeFrame = 10f
        val noTimeGainDistance = 44f
        val linearTimeGainDistanceStart = noTimeGainDistance
        val linearTimeGainDistanceEnd = 100f
        val linearTimeGainDistance = linearTimeGainDistanceEnd - linearTimeGainDistanceStart
        var distanceTimeGain = 0f
        var lastDamageDealt = 0f
        var cumulativeDamageDealt = 0f
        var cumulativeDamageTaken = 0f
        var secondTime = now()
        var lastAiStock = evaluationController?.player1?.stock ?: 4
        var lastOpponentStock = evaluationController?.player2?.stock ?: 4
        var lastPercent = evaluationController?.player1?.percent ?: 0
        var lastOpponentPercent = evaluationController?.player2?.percent ?: 0
        var gameLostFlag = false
        var currentStockDamageDealt = 0f
        var bonusGraceDamageApplied = false
        var earlyKillBonusApplied = false
        var gracePeriodEnded = false
        evaluationController?.let { secondTime = it.agentStart }
        while (true) {
            if (resetSimulationForAgent) {
                resetSimulationForAgent = false
                distanceTimeGain = 0f
                lastDamageDealt = 0f
                cumulativeDamageDealt = 0f
                cumulativeDamageTaken = 0f
                secondTime = now()
                lastAiStock = evaluationController?.player1?.stock ?: 4
                lastOpponentStock = evaluationController?.player2?.stock ?: 4
                lastPercent = evaluationController?.player1?.percent ?: 0
                lastOpponentPercent = evaluationController?.player2?.percent ?: 0
                gameLostFlag = false
                currentStockDamageDealt = 0f
                bonusGraceDamageApplied = false
                earlyKillBonusApplied = false
                gracePeriodEnded = false
            }
            val now = now()
            val damageDoneFrame = damageDone().toFloat()
            val opponentPercentFrame = lastFrame?.player1?.percent ?: 0
            val percentFrame = lastFrame?.player1?.percent ?: 0
            val aiStockFrame = lastFrame?.player1?.stock ?: 4
            val opponentStockFrame = lastFrame?.player2?.stock ?: 4
            val wasDamageDealt = cumulativeDamageDealt > 0
            val distance = distance()
            val wasStockButNotGameLost = (lastAiStock - aiStockFrame) == 1
            val wasGameLost = (lastAiStock - aiStockFrame) == -3
            val stockLoss = wasGameLost || wasStockButNotGameLost
            val aiOnGround = lastFrame?.player1?.onGround ?: false

            if (distanceTime != null && Duration.between(distanceTime, now).seconds > distanceTimeGain) {
                distanceTime = null
                distanceTimeGain = 0f
            }
            if (distanceTime != null && distance > linearTimeGainDistanceEnd) {
                distanceTime = null
                distanceTimeGain = 0f
            }

            if (damageDoneFrame > lastDamageDealt) {
                damageDoneTime = now

                val damage = if (gameLostFlag) 0f.also { gameLostFlag = false } else damageDoneFrame - lastDamageDealt

                val secondsAiPlay = evaluationController?.let { Duration.between(it.agentStart, now).seconds } ?: 0
                log.info { "Damage at $secondsAiPlay seconds" }
                log.info {
                    """DamageAccumulation: $cumulativeDamageDealt + $damage -> (${cumulativeDamageDealt + damage})
                            |StockDelta: $lastAiStock - $aiStockFrame -> ${lastAiStock - aiStockFrame}
                        """.trimMargin()
                }
                currentStockDamageDealt += damage
                cumulativeDamageDealt += damage
                if (secondsAiPlay < 2) {
                    bonusGraceDamageApplied = true
                    val gracePeriodHitBonus = gracePeriodHitBonus(
                        t = secondsAiPlay.toFloat(),
                        gracePeriod = timeAvailable,
                        bonus = 10
                    )
                    log.info { "Grace period damage bonus: $cumulativeDamageDealt + $gracePeriodHitBonus -> (${cumulativeDamageDealt + gracePeriodHitBonus})" }
                    cumulativeDamageDealt += gracePeriodHitBonus
                }
            }

            val wasOneStockTaken = (lastOpponentStock) != opponentStockFrame
            if (wasOneStockTaken) {
                if (lastOpponentPercent < 100f && currentStockDamageDealt > 0) {
                    val earlyKillBonus =
                        sqrt(((100f - lastOpponentPercent) / max(1f, currentStockDamageDealt)) * 2).pow(2)
                    log.info {
                        """
                        PercentCap: 100
                        LastEnemyPercent: $lastOpponentPercent
                        currentDamageDealt: $currentStockDamageDealt
                        Early Kill: $cumulativeDamageDealt + $earlyKillBonus -> (${cumulativeDamageDealt + earlyKillBonus})
                    """.trimIndent()
                    }
                    cumulativeDamageDealt += earlyKillBonus
                    earlyKillBonusApplied = true
                } else log.info { "Kill but percent was over 100." }
                val doubledDamage = cumulativeDamageDealt * 2
                log.info {
                    """
                            Stock taken: $lastOpponentStock -> $opponentStockFrame ($wasOneStockTaken)
                            Damage: $lastDamageDealt -> $damageDoneFrame
                            CumulativeDamage: $cumulativeDamageDealt -> $doubledDamage
                        """.trimIndent()
                }

                cumulativeDamageDealt = doubledDamage
                stockTakenTime = now

            }


            if (lastAiStock != aiStockFrame) {
                log.info { "Stocks not equal! $lastAiStock -> $aiStockFrame" }
            }
            if (stockLoss)
                log.info { "Stock was lost: $cumulativeDamageDealt > 0 (${cumulativeDamageDealt > 0})" }
            if (stockLoss && cumulativeDamageDealt > 0) {
                damageTimeFrame -= .25f
                timeGainMax -= 2f
                val sqrt = sqrt(cumulativeDamageDealt)
                log.info {
                    """
                        Stock Lost: $lastAiStock -> $aiStockFrame ($wasStockButNotGameLost)
                        CumulativeDamage: $cumulativeDamageDealt -> $sqrt
                    """.trimIndent()
                }
                if (stockLostTime != null && Duration.between(now, stockLostTime).seconds < 4) {
                    log.info { "Double quick death... be gone" }
                    doubleDeathQuick = true
                }
                stockLostTime = now
                cumulativeDamageDealt = sqrt
                currentStockDamageDealt = 0f
            }
            if (wasGameLost) {
                gameLostFlag = true
                lastDamageDealt = 0f
                evaluationController =
                    evaluationController!!.run {
                        EvaluationController(
                            agentStart,
                            player1.copy(percent = 0),
                            player2.copy(percent = 0),
                            distance
                        )
                    }
            }

            if (lastPercent < percentFrame) cumulativeDamageTaken += percentFrame - lastPercent

            if (evaluationController?.agentStart.let { Duration.between(now, it).seconds } > timeAvailable) {
                gracePeriodEnded = true
                log.info { "Grace period gone" }
            }
            lastDamageDealt = damageDoneFrame
            lastOpponentStock = opponentStockFrame
            lastAiStock = aiStockFrame
            lastPercent = percentFrame
            lastOpponentPercent = opponentPercentFrame
            evaluationController?.let {
                val distanceTimeStep = .06f
                if (Duration.between(secondTime, now).toMillis() > 100) {
                    secondTime = now
                    if (distance >= linearTimeGainDistanceStart && distance < linearTimeGainDistanceEnd) {
                        val x = (distance - linearTimeGainDistanceStart) / linearTimeGainDistance
                        if (distanceTimeGain == 0f && x > 0f) {
                            distanceTime = now
                        }
                        distanceTimeGain += (1 - x) * distanceTimeStep
                    } else {
                        distanceTimeGain += distanceTimeStep
                    }
                    log.trace {
                        """
                        distance:   $distance
                        distanceTimeGain:   $distanceTimeGain
                        """.trimIndent()
                    }
                    if (distanceTimeGain > timeGainMax) distanceTimeGain = timeGainMax
                }
            }

            val timeElapsedSinceBonus = if (stockTakenTime != null) {
                Duration.between(stockTakenTime, now).seconds > stockTakeTimeFrame
            } else true
            val timeElapsedSinceDamage = if (damageDoneTime != null) {
                Duration.between(damageDoneTime, now).seconds > damageTimeFrame + distanceTimeGain
            } else false
            val timeElapsed =
                (Duration.between(evaluationController!!.agentStart, now).seconds > timeAvailable + distanceTimeGain)
            val score = if (cumulativeDamageDealt < 8) 0f else cumulativeDamageDealt.pow(2)
            val cumulativeDmgRatio = cumulativeDamageDealt / max(cumulativeDamageTaken, 1f)
            val scoreWithPercentRatioModifier = score * cumulativeDmgRatio
            val damageClockActive = wasDamageDealt && timeElapsedSinceDamage && timeElapsedSinceBonus
            val gracePeriodClockActive = timeElapsed && !wasDamageDealt
            if (((gracePeriodClockActive || damageClockActive) && aiOnGround || stockLoss || brokenNetwork) && !pauseSimulation) {
                if (brokenNetwork) {
                    brokenNetwork = false
                    log.info { "Killing broken network" }
                    return FitnessModel(model, -1f)
                }
                else {
                    log.info {
                        """
                    ${
                            if (stockTakenTime != null) "timeElapsedSinceStock: ${
                                Duration.between(
                                    stockTakenTime,
                                    now
                                ).seconds
                            } ($timeElapsedSinceBonus)" else ""
                        }
                    ${
                            if (damageDoneTime != null) "timeElapsedSinceDamage: ${
                                Duration.between(
                                    damageDoneTime,
                                    now
                                ).seconds
                            } ($timeElapsedSinceDamage)" else ""
                        }
                    timeGain: $distanceTimeGain
                    timeElapsed: ${Duration.between(evaluationController!!.agentStart, now).seconds} ($timeElapsed)
                    damageTaken: $cumulativeDamageTaken ($cumulativeDmgRatio)
                    damageDone: $cumulativeDamageDealt ($wasDamageDealt)
                    earlyKill: $earlyKillBonusApplied
                    graceHit: $bonusGraceDamageApplied
                    score: $score
                    finalScore: $scoreWithPercentRatioModifier
                """.trimIndent()
                    }
                    return FitnessModel(model, scoreWithPercentRatioModifier)
                }

            }
        }
    }

    suspend fun processFrame(frameUpdate: FrameUpdate): FrameOutput? {
        val agent = activeAgent
        if (lastAgent != agent) {
            lastAgent = agent
            evaluationController =
                EvaluationController(now(), frameUpdate.player1, frameUpdate.player2, distance = frameUpdate.distance)
        } else evaluationController?.distance = frameUpdate.distance
        this.lastFrame = frameUpdate
        if (agent != null && !pauseSimulation) {
            try {
                agent.evaluate(frameUpdate.flatten())
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
            evaluationController =
                EvaluationController(now(), it.player1.copy(percent = 0), it.player2.copy(percent = 0), distance())
            resetSimulationForAgent = true
        }
    }

    private fun distance() = evaluationController?.distance ?: 0f

    fun pause() {
        pauseSimulation = true
    }

    fun resume() {
        pauseSimulation = false
    }
}

private fun EvaluationArena.damageDone() =
    (lastFrame?.player2?.percent ?: 0) - (evaluationController?.player2?.percent ?: 0)

private fun EvaluationArena.lostStock() = lastFrame!!.player1.stock < evaluationController!!.player1.stock

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


fun Boolean.toFloat() = if (this) 1f else 0f
private suspend fun FrameUpdate.flatten() = sequence<Float> {
    yieldPlayerData(player1)
    yieldPlayerData(player2)
    yieldActionData(action1)
    yieldActionData(action2)
    yield(distance)
}.toList()

private suspend fun SequenceScope<Float>.yieldActionData(actionData: ActionData) {
    with(actionData) {
        yield(action.toFloat())
        yield(isAttack.toFloat())
        yield(isBMove.toFloat())
        yield(isGrab.toFloat())
        yield(isShield.toFloat())
        yield(rangeForward)
        yield(rangeBackward)
        yield(hitBoxCount.toFloat())
    }
}

private suspend fun SequenceScope<Float>.yieldPlayerData(playerDataUpdate: PlayerDataUpdate) {
    with(playerDataUpdate) {
        yield(x)
        yield(y)
        yield(speedAirX)
        yield(speedGroundX)
        yield(speedXAttack)
        yield(speedY)
        yield(speedYAttack)
        yield(facingRight.toFloat())
        yield(percent.toFloat())
        yieldEnvironmentalCollisionBox(ecb)
    }
}

private suspend fun SequenceScope<Float>.yieldEnvironmentalCollisionBox(environmentalCollisionBox: EnvironmentalCollisionBox) {
    yieldPosition(environmentalCollisionBox.bottom)
    yieldPosition(environmentalCollisionBox.left)
    yieldPosition(environmentalCollisionBox.right)
    yieldPosition(environmentalCollisionBox.top)
}

private suspend fun SequenceScope<Float>.yieldPosition(position: Position) {
    yield(position.x)
    yield(position.y)
}

