import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import org.koin.core.scope.Scope
import server.message.BroadcastMessage
import server.message.endpoints.EndpointProvider
import java.time.Instant
import java.time.Instant.now
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

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

class EvaluationController(val agentStart: Instant, val player1: PlayerDataUpdate, val player2: PlayerDataUpdate)

class EvaluationArena() {
    var activeModel: NeatMutator? = null
    var activeAgent: ActivatableNetwork? = null
    var evaluationController: EvaluationController? = null
    var lastFrame: FrameUpdate? = null
    var lastAgent: ActivatableNetwork? = null
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
        delay(3000)
        log.info { "Grace period gone" }
        evaluationController = EvaluationController(now(), lastFrame!!.player1, lastFrame!!.player2)
        var framesShield = 0
        while (true) {
            delay(5)

            val stocksTaken = evaluationController!!.player2.stock - (lastFrame!!.player2.stock)
            val remainingDamage = if (stocksTaken == 0) {
                (lastFrame!!.player2.percent) - (evaluationController!!.player2.percent)
            } else (lastFrame?.player2?.percent ?: 0)
            val secondsAlive = (now().epochSecond - (evaluationController?.agentStart?.epochSecond ?: 0L)).toDouble()
            val lnTimeScore = ln(
                secondsAlive +.0000001
            )
            if (lastFrame?.action1?.isShield == true) framesShield++
            val topPercent = 100
            val percentScore = topPercent - lastFrame!!.player1.percent
            val lostStock = lastFrame!!.player1.stock < evaluationController!!.player1.stock
            val damageDone = (lastFrame?.player2?.percent ?: 0) - (evaluationController?.player2?.percent ?: 0)
            val damageTaken = (lastFrame?.player1?.percent ?: 0) - (evaluationController?.player1?.percent ?: 0)
            val damageDifference = (damageDone - damageTaken).toFloat()
            val score = ((stocksTaken * stocksTaken) * 1000) + (remainingDamage.toFloat()).pow(6f) + (lnTimeScore * 2) * lnTimeScore - when {
                lostStock && stocksTaken == 0 -> {
                    (percentScore * percentScore) * 10
                }
                else -> 0
            } - framesShield * ln(framesShield.toFloat() + .00000001) - when {
                stocksTaken == 0 && !lostStock -> {
                    damageDifference * damageDifference
                }
                else -> 0f
            }
//            log.info { "${lastFrame!!.player1.stock} < ${evaluationController!!.player1.stock}"  }

            val tookThresholdDamage = damageTaken > 12

            val tookDamage = (lastFrame?.player1?.percent ?: 0) > evaluationController?.player1?.percent ?: 0
            if (lostStock || tookThresholdDamage) {
                log.info {
                    """
                Evaluation for model has finished:
                    stocksTaken: ${stocksTaken}
                    standingDamage: ${remainingDamage}
                    lostStock: ${lostStock}
                    tookDamage: ${tookDamage}
                    tookThresholdDamage: $tookThresholdDamage
                    shieldFrames: $framesShield
                    damageTaken: $damageTaken
                    damageDone: $damageDone
                    damageDifference: $damageDifference
                    secondsAlive: ${secondsAlive}
                    secondsLnScore: ${lnTimeScore}
                    score: ${score}
            """.trimIndent()
                }
                return FitnessModel(model, score.toFloat())
            }
        }
    }

    suspend fun processFrame(frameUpdate: FrameUpdate): FrameOutput? {
        val agent = activeAgent
        if (lastAgent != agent) {
            lastAgent = agent
            evaluationController = EvaluationController(now(), frameUpdate.player1, frameUpdate.player2)
        }
        this.lastFrame = frameUpdate
        if (agent != null) {
            agent.evaluate(frameUpdate.flatten())
            return agent.output().toFrameOutput()
        }

        return null
    }

    fun resetEvaluation() {
        lastFrame?.let {
            log.info { "Reset eval controller - new match" }
            evaluationController = EvaluationController(now(), it.player1, it.player2)
        }
    }
}

private fun List<Float>.toFrameOutput(): FrameOutput {
    fun bool(index: Int) = get(index).roundToInt() > 0
    fun clamp(index: Int) = get(index).let { if (it < 0) 0f else if (it > 1) 1f else it }
    return FrameOutput(bool(0), bool(1), bool(2), bool(3), clamp(4), clamp(5), clamp(6), clamp(7), clamp(8), clamp(9))
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

