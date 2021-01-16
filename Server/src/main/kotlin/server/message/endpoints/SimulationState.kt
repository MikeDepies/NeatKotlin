package server.message.endpoints

import FrameUpdate
import mu.*
import java.time.*

private val log = KotlinLogging.logger {}

class SimulationState(
    var lastAiStock: Int,
    var lastOpponentStock: Int,
    var lastPercent: Int,
    var lastOpponentPercent: Int,
    var timeGainMax: Float,
    val timeAvailable: Float,
    var damageTimeFrame: Float,
    val stockTakeTimeFrame: Float,
    val hitStunTimeFrame: Float = .200f,
    val opponentHitStunTimeFrame: Float = .200f,
    val landingTimeFrame: Float = .200f,
    val noTimeGainDistance: Float = 44f,
    val linearTimeGainDistanceEnd: Float = 100f,
    var distanceTimeGain: Float = 0f,
    var cumulativeDamageDealt: Float = 0f,
    var cumulativeDamageTaken: Float = 0f,
    var agentStart: Instant
) {
    fun reset(frame: FrameUpdate?) {
        log.info { "Reset" }
        distanceTimeGain = 0f
        lastDamageDealt = 0f
        cumulativeDamageDealt = 16f
        cumulativeDamageTaken = 0f
        secondTime = Instant.now()
        agentStart = Instant.now()
        lastAiStock = frame?.player1?.stock ?: 4
        lastOpponentStock = frame?.player2?.stock ?: 4
        lastPercent = frame?.player1?.percent ?: 0
        lastOpponentPercent = frame?.player2?.percent ?: 0
        gameLostFlag = false
        currentStockDamageDealt = 0f
        bonusGraceDamageApplied = false
        earlyKillBonusApplied = false
        gracePeriodEnded = false
        inAirFromKnockBack = false
    }

    fun update(simulationFrameData: SimulationFrameData) = with(simulationFrameData) {
        if (Duration.between(now, agentStart).seconds > timeAvailable) {
            gracePeriodEnded = true
            log.info { "Grace period gone" }
        }
        if (aiOnGround && !prevOnGround) {
            landingClock = Instant.now()
            log.info { "Ai landed" }
        }

        if (!hitStun && prevHitStun) {
            hitStunClock = Instant.now()
            log.info { "Ai is out of hitstun" }
        }

        if (!opponentHitStun && prevOpponentHitStun) {
            opponentHitStunClock = Instant.now()
            log.info { "Opponent is out of hitstun" }
        }
        if (lastPercent < percentFrame) cumulativeDamageTaken += percentFrame - lastPercent
        lastDamageDealt = damageDoneFrame
        lastOpponentStock = opponentStockFrame
        lastAiStock = aiStockFrame
        lastPercent = percentFrame
        lastOpponentPercent = opponentPercentFrame
        prevHitStun = hitStun
        prevOpponentHitStun = opponentHitStun
        prevOnGround = aiOnGround
        prevTookDamage = tookDamage

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

    fun createSimulationFrame(lastFrame: FrameUpdate?): SimulationFrameData {
        val aiStockFrame = lastFrame?.player1?.stock ?: 4
        val percentFrame = lastFrame?.player1?.percent ?: 0
        val wasStockButNotGameLost = (lastAiStock - aiStockFrame) == 1 && aiStockFrame != 0
        val wasGameLost = (aiStockFrame) == 0 && lastAiStock == 1
        val opponentStockFrame = lastFrame?.player2?.stock ?: 4
//        log.info { "${lastFrame?.player2?.percent ?: 0} - ${lastOpponentPercent}" }
        val opponentPercentFrame = lastFrame?.player2?.percent ?: 0
        return SimulationFrameData(
            damageDoneFrame = damageDone(opponentPercentFrame).toFloat(),
            opponentPercentFrame = opponentPercentFrame,
            percentFrame = percentFrame,
            aiStockFrame = aiStockFrame,
            opponentStockFrame = opponentStockFrame,
            wasDamageDealt = cumulativeDamageDealt > 0,
            distance = lastFrame?.distance ?: 0f,
            wasStockButNotGameLost = wasStockButNotGameLost,
            tookDamage = lastPercent < percentFrame,
            wasGameLost = wasGameLost,
            stockLoss = wasGameLost || wasStockButNotGameLost,
            aiOnGround = lastFrame?.player1?.onGround ?: false,
            opponentOnGround = lastFrame?.player2?.onGround ?: false,
            hitStun = lastFrame?.player1?.hitStun ?: false,
            opponentHitStun = lastFrame?.player2?.hitStun ?: false,
            wasOneStockTaken = (lastOpponentStock > opponentStockFrame),
            frame = lastFrame?.frame ?: -1,
            now = Instant.now()
        )
    }

    private fun damageDone(opponentPercentFrame: Int) =
        opponentPercentFrame - lastOpponentPercent

    fun finished(simulationFrameData: SimulationFrameData): Boolean = with(simulationFrameData) {
        val timeElapsedSinceBonus = if (stockTakenTime != null) {
            Duration.between(stockTakenTime, now).seconds <= stockTakeTimeFrame
        } else true
        val timeElapsedSinceDamage = if (damageDoneTime != null) {
            Duration.between(damageDoneTime, now).seconds <= damageTimeFrame + distanceTimeGain
        } else false
        val graceTimeActive =
            (Duration.between(agentStart, now).seconds <= timeAvailable + distanceTimeGain)
        val damageClockActive = wasDamageDealt && timeElapsedSinceDamage && timeElapsedSinceBonus
        val landingClockActive = if (landingClock != null) {
            Duration.between(landingClock, now).toMillis() <= landingTimeFrame
        } else false
        val hitStunClockActive = if (hitStunClock != null) {
            Duration.between(hitStunClock, now).toMillis() <= hitStunTimeFrame
        } else false
        val opponentHitStunClockActive = if (opponentHitStunClock != null) {
            Duration.between(opponentHitStunClock, now).toMillis() <= opponentHitStunTimeFrame
        } else false
        val clocksExpired =
            !(graceTimeActive || damageClockActive || hitStunClockActive || landingClockActive || opponentHitStunClockActive)
        val terminatePlayTime = (clocksExpired) && aiOnGround && opponentOnGround && !hitStun && !opponentHitStun

        return ((terminatePlayTime || stockLoss))
    }



    var damageDoneTime: Instant? = null
    var stockTakenTime: Instant? = null
    var stockLostTime: Instant? = null
    var distanceTime: Instant? = null
    var doubleDeathQuick = false
    val linearTimeGainDistanceStart = noTimeGainDistance
    val linearTimeGainDistance = linearTimeGainDistanceEnd - linearTimeGainDistanceStart
    var lastDamageDealt = 0f
    var secondTime = Instant.now()
    var gameLostFlag = false
    var currentStockDamageDealt = 0f
    var bonusGraceDamageApplied = false
    var earlyKillBonusApplied = false
    var gracePeriodEnded = false
    var prevOnGround = false
    var prevHitStun = false
    var prevOpponentHitStun = false
    var hitStunClock: Instant? = null
    var opponentHitStunClock: Instant? = null
    var landingClock: Instant? = null
    var prevTookDamage = false
    var inAirFromKnockBack = false
}