package server

import FrameUpdate
import server.message.endpoints.*
import kotlin.math.*

class SimpleEvaluator(
    private val meleeState: MeleeState,
    var runningScore: Float,
    frameClockFactory: FrameClockFactory
) :
    Evaluator {
    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
    private var firstFrame = true
    private val damageClock = frameClockFactory.countDownClockSeconds(.5f)
    private val graceClock = frameClockFactory.countDownClockSeconds(2f)
    private val hitStunClock = frameClockFactory.countDownClockMilliseconds(2000)
    private val enemyHitStunClock = frameClockFactory.countDownClockMilliseconds(2000)
    private val landedClock = frameClockFactory.countDownClockMilliseconds(2000)
    private val stockTakenClock = frameClockFactory.countDownClockSeconds(6f)
    var cumulativeDamage = 0f
    var cumulativeDamageTaken = 0f
    var currentStockDamage = 0f

    /**
     * The finalized Score
     */
    override val finishedScore: Float
        get() {
            val score = if (runningScore < 8) 0f else runningScore.pow(2)
            val cumulativeDmgRatio = max(runningScore, 1f) / max(cumulativeDamageTaken, 1f)
            return score * cumulativeDmgRatio
        }

    override fun isFinished(): Boolean {
        val meleeFrameData = meleeState.lastMeleeFrameData
        val clocksFinished = clocksFinished(meleeFrameData)
        val playerStatusPass = isPlayerStatusReady(meleeFrameData.player1)
        val opponentStatusPass = isPlayerStatusReady(meleeFrameData.player2)
        val playtimeExpired = clocksFinished && playerStatusPass && opponentStatusPass
        return playtimeExpired || meleeFrameData.player1.lostStock
    }

    private fun clocksFinished(meleeFrameData: MeleeFrameData) = listOf(
        damageClock,
        damageClock,
        hitStunClock,
        enemyHitStunClock,
        landedClock,
        stockTakenClock
    ).all { it.isFinished(meleeFrameData) }

    override fun evaluateFrame(frameUpdate: FrameUpdate) {
        val frameData = meleeState.createSimulationFrame(frameUpdate)
        val frameNumber = frameData.frame
        val player1 = frameData.player1
        val player2 = frameData.player2
        val lastPlayer1 = meleeState.lastMeleeFrameData.player1
        val lastPlayer2 = meleeState.lastMeleeFrameData.player2
        //Perform evaluations
        checkClockEvents(frameNumber, frameData)
        if (player1.tookStock) scoreStockTake(frameNumber, lastPlayer2.percentFrame)
        if (player1.lostStock) {
            val scorePenalized = if (frameData.isPlayerInAirFromKnockBack(0)) runningScore / 8 else sqrt(runningScore)
            runningScore = scorePenalized
        }
        cumulativeDamage += player1.damageDone
        runningScore += player1.damageDone
        cumulativeDamageTaken += player1.damageTaken
        if (player1.lostStock)
            currentStockDamage = 0f
        else currentStockDamage += player1.damageDone
        //Then update to the new frame.
        meleeState.lastMeleeFrameData = frameData
    }

    private fun scoreStockTake(
        frameNumber: Int,
        deathPercent: Int
    ) {
        fun earlyKillBonus() {
            if (deathPercent < 100) {
                val baseBonus = (100f - deathPercent).div(max(1f, currentStockDamage))
                val bonusMultiplier = 12
                val earlyKillBonus = baseBonus * bonusMultiplier
                runningScore += earlyKillBonus
            }
        }
        earlyKillBonus()
        stockTakenClock.start(frameNumber)
        runningScore *= 2
    }

    private fun checkClockEvents(frameNumber: Int, frameData: MeleeFrameData) {
        fun CountDownClock.startClockIf(b: Boolean) {
            if (b) start(frameNumber)
        }
        startGraceClockOnFirstFrame(frameNumber)
        damageClock.startClockIf(frameData[0].tookDamage)
        landedClock.startClockIf(frameData.playerLanded(0))
        hitStunClock.startClockIf(frameData.playerExitingHitStun(0))
        enemyHitStunClock.startClockIf(frameData.playerExitingHitStun(1))
    }

    private fun MeleeFrameData.playerExitingHitStun(playerNumber: Int) =
        !this[playerNumber].hitStun && lastMeleeFrameData[playerNumber].hitStun

    private fun MeleeFrameData.playerLanded(playerNumber: Int) =
        this[playerNumber].onGround && !lastMeleeFrameData[playerNumber].onGround

    private fun isPlayerStatusReady(playerFrameData: PlayerFrameData) =
        !playerFrameData.hitStun && playerFrameData.onGround

    private fun MeleeFrameData.isPlayerInAirFromKnockBack(playerNumber: Int) =
        !this[playerNumber].onGround && lastMeleeFrameData[playerNumber].tookDamage

    private fun startGraceClockOnFirstFrame(frameNumber: Int) {
        if (firstFrame && meleeState.lastMeleeFrameData.player1.onGround) {
            graceClock.start(frameNumber)
            firstFrame = false
        }
    }
}