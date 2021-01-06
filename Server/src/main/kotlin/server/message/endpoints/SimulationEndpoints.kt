import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import org.koin.core.scope.Scope
import server.message.BroadcastMessage
import server.message.endpoints.EndpointProvider
import java.time.Duration
import java.time.Instant
import java.time.Instant.now
import kotlin.math.*

private val log = KotlinLogging.logger { }
var recievedAnyMessages = false
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

        recievedAnyMessages = true
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
    suspend fun evaluatePopulation(population: List<NeatMutator>): List<FitnessModel<NeatMutator>> {
        val size = population.size
        log.info { "Starting to evaluate population($size)" }
        return population.mapIndexed { index, neatMutator ->
            log.info { "${index + 1} / $size" }
            evaluateModel(neatMutator)
        }
    }

    suspend fun evaluateModel(model: NeatMutator): FitnessModel<NeatMutator> {
        val agent = model.toNetwork().also { activeAgent = it; activeModel = model }
        log.info { "Evaluation for new model has begun" }
//        delay(3000)
        log.info { "Grace period gone" }

        evaluationController =
            EvaluationController(now(), lastFrame!!.player1, lastFrame!!.player2, evaluationController?.distance ?: 0f)
        var damageDoneTime: Instant? = null
        var stockTakenTime: Instant? = null
        var distanceTime: Instant? = null
        val timeAvailable = 1f
        val damageTimeFrame = 2.5f
        val stockTakeTimeFrame = 10f
        val noTimeGainDistance = 88f
        val linearTimeGainDistanceStart = noTimeGainDistance
        val linearTimeGainDistanceEnd = 176f
        val linearTimeGainDistance = linearTimeGainDistanceEnd - linearTimeGainDistanceStart
        var distanceTimeGain = 0f
        var lastDamage = 0f
        var cumulativeDamage = 0f
        var secondTime = now()
        var lastAiStock = evaluationController?.player1?.stock ?: 4
        var lastOpponentStock = evaluationController?.player2?.stock ?: 4
        var lastPercent = evaluationController?.player1?.percent ?: 0
        var gameLostFlag = false
        evaluationController?.let { secondTime = it.agentStart }
        while (true) {
            val now = now()
            val damageDoneFrame = damageDone().toFloat()
            val percentFrame = lastFrame?.player1?.percent ?: 0
            val aiStockFrame = lastFrame?.player1?.stock ?: 4
            val opponentStockFrame = lastFrame?.player2?.stock ?: 4
            val wasDamageDealt = cumulativeDamage > 0
            val distance = distance()
            val wasOneStockLost = (lastAiStock - aiStockFrame) == 1
            val wasGameLost = (lastAiStock - aiStockFrame) == -3
            val shouldPenalizeCumulativeDamage = wasGameLost || wasOneStockLost

            if (distanceTime != null && Duration.between(distanceTime, now).seconds > distanceTimeGain) {
                distanceTime = null
                distanceTimeGain = 0f
            }

            if (damageDoneFrame > lastDamage) {
                damageDoneTime = now

                val damage = if (gameLostFlag) 0f.also { gameLostFlag = false } else damageDoneFrame - lastDamage

                log.info {
                    """DamageAccumulation: $cumulativeDamage + $damage -> (${cumulativeDamage + damage})
                            |StockDelta: $lastAiStock - $aiStockFrame -> ${lastAiStock - aiStockFrame}
                        """.trimMargin()
                }

                cumulativeDamage += damage
            }

            val wasOneStockTaken = (lastOpponentStock - opponentStockFrame) == 1
            if (wasOneStockTaken) {
                val doubledDamage = cumulativeDamage * 2
                log.info {
                    """
                            Stock taken: $lastOpponentStock -> $opponentStockFrame ($wasOneStockTaken)
                            Damage: $lastDamage -> $damageDoneFrame
                            CumulativeDamage: $cumulativeDamage -> $doubledDamage
                        """.trimIndent()
                }
                cumulativeDamage = doubledDamage
                stockTakenTime = now
            }


            if (lastAiStock != aiStockFrame) {
                log.info { "Stocks not equal! $lastAiStock -> $aiStockFrame" }
            }
            if (shouldPenalizeCumulativeDamage)
                log.info { "Stock was lost: $cumulativeDamage > 0 (${cumulativeDamage > 0})" }
            if (shouldPenalizeCumulativeDamage && cumulativeDamage > 0) {
                val sqrt = sqrt(cumulativeDamage)
                log.info {
                    """
                        Stock Lost: $lastAiStock -> $aiStockFrame ($wasOneStockLost)
                        Percent: $lastPercent -> $percentFrame
                        CumulativeDamage: $cumulativeDamage -> $sqrt
                    """.trimIndent()
                }
                cumulativeDamage = sqrt
            }
            if (wasGameLost) {
                gameLostFlag = true
                lastDamage = 0f
                evaluationController =
                    evaluationController!!.run { EvaluationController(agentStart, player1.copy(percent = 0), player2.copy(percent = 0), distance) }
            }


            lastDamage = damageDoneFrame
            lastOpponentStock = opponentStockFrame
            lastAiStock = aiStockFrame
            lastPercent = percentFrame
            evaluationController?.let {
                if (Duration.between(secondTime, now).toMillis() > 300) {
                    secondTime = now
                    if (distance >= linearTimeGainDistanceStart && distance < linearTimeGainDistanceEnd) {
                        val x = (distance - linearTimeGainDistanceStart) / linearTimeGainDistance
                        if (distanceTimeGain == 0f && x > 0f) {
                            distanceTime = now
                        }
                        distanceTimeGain += x
                        if (distanceTimeGain > 8) distanceTimeGain = 8f
                    }
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
            val score = cumulativeDamage.pow(2)
            if ((timeElapsed && !wasDamageDealt || wasDamageDealt && timeElapsedSinceDamage && timeElapsedSinceBonus) && !pauseSimulation) {
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
                    timeGain: ${distanceTimeGain}
                    timeElapsed: ${Duration.between(evaluationController!!.agentStart, now).seconds} ($timeElapsed)
                    damageDone: $cumulativeDamage ($wasDamageDealt)
                    score: $score
                """.trimIndent()
                }
                return FitnessModel(model, score)
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
                log.error(e) { "failed to evaluate agent" }
            }
        }

        return null
    }

    fun resetEvaluation() {
        lastFrame?.let {
            log.info { "Reset eval controller - new match" }
            evaluationController = EvaluationController(now(), it.player1.copy(percent = 0), it.player2.copy(percent = 0), distance())
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
        rightShoulder = clamp(9)
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

