package server

import FrameUpdate
import mu.KotlinLogging
import server.message.endpoints.*
import kotlin.math.*
private val logger = KotlinLogging.logger {  }
class SimpleEvaluator(
    private val meleeState: MeleeState,
    var runningScore: Float,
    frameClockFactory: FrameClockFactory
) :
    Evaluator {
    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
    private var firstFrame = true
    private val damageClock = frameClockFactory.countDownClockSeconds(3f).log("Damage Clock")
    private val graceClock = frameClockFactory.countDownClockSeconds(1.5f).log("Grace Clock")
    private val hitStunClock = frameClockFactory.countDownClockMilliseconds(200).log("HitStun Clock")
    private val enemyHitStunClock = frameClockFactory.countDownClockMilliseconds(200).log("P2 HitStun Clock")
    private val landedClock = frameClockFactory.countDownClockMilliseconds(200).log("Landed Clock")
    private val stockTakenClock = frameClockFactory.countDownClockSeconds(6f).log("StockTaken Clock")
    var cumulativeDamage = 0f
    var cumulativeDamageTaken = 0f
    var currentStockDamage = 0f
    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()

    /**
     * The finalized Score
     */
    override val score: Float
        get() {
            return runningScore
        }

    override fun isFinished(): Boolean {
        val meleeFrameData = meleeState.lastMeleeFrameData
        val clocksFinished = clocksFinished(meleeFrameData)
        val playerStatusPass = isPlayerStatusReady(meleeFrameData.player1)
        val opponentStatusPass = isPlayerStatusReady(meleeFrameData.player2)
        val playtimeExpired = clocksFinished && playerStatusPass && opponentStatusPass
//        logger.info { "Clocks Finished: $clocksFinished" }
        return playtimeExpired || meleeFrameData.player1.lostStock
    }

    private fun clocksFinished(meleeFrameData: MeleeFrameData) = listOf(
        damageClock,
        hitStunClock,
        graceClock,
        enemyHitStunClock,
        landedClock,
        stockTakenClock
    ).all { val finished = it.isFinished(meleeFrameData)
        finished
    }

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
            val scorePenalized = if (frameData.isPlayerInAirFromKnockBack(0)) {
                val newRunningScore = runningScore / 8
                scoreContributionList.add(
                    EvaluationScoreContribution(
                        "Stock Loss from KnockBack",
                        newRunningScore,
                        newRunningScore - runningScore
                    )
                )
                newRunningScore
            } else {
                val newRunningScore = sqrt(runningScore)
                scoreContributionList += EvaluationScoreContribution(
                    "Stock Loss SD",
                    newRunningScore,
                    newRunningScore - runningScore
                )
                newRunningScore
            }
            runningScore = scorePenalized
        }
        cumulativeDamage += player1.damageDone
        val newRunningScore = runningScore + player1.damageDone
        scoreContributionList += EvaluationScoreContribution(
            "Damage Dealt (${player1.damageDone})",
            newRunningScore,
            newRunningScore - runningScore
        )
        runningScore = newRunningScore
        if (player1.damageTaken > 0 || player1.tookDamage)
            logger.info { "DAMAGE TAKEN ${player1.tookDamage}" }
        cumulativeDamageTaken += player1.damageTaken
        if (player2.lostStock)
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
                scoreContributionList.add(
                    EvaluationScoreContribution(
                        "Early Kill Bonus",
                        earlyKillBonus,
                        earlyKillBonus - runningScore
                    )
                )
                runningScore += earlyKillBonus
            }
        }
        earlyKillBonus()
        stockTakenClock.start(frameNumber)
        val doubleScore = runningScore * 2
        scoreContributionList.add(
            EvaluationScoreContribution(
                "Stock Take Modifier",
                doubleScore,
                doubleScore - runningScore
            )
        )
        runningScore = doubleScore
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

    override fun finishEvaluation(): EvaluationScore {
        val score = if (runningScore < 8) {
            scoreContributionList.add(EvaluationScoreContribution("Damage Minimum Threshold", 0f, runningScore * -1))
            0f
        } else {
            val pow = runningScore.pow(2)
            scoreContributionList.add(EvaluationScoreContribution("Finish Bonus (Score^2)", pow, pow - runningScore))
            pow
        }
        val cumulativeDmgRatio = max(runningScore, 1f) / max(cumulativeDamageTaken / 4, 1f)
        val newScore = score * cumulativeDmgRatio
        scoreContributionList.add(
            EvaluationScoreContribution(
                "Cumulative Ratio Modifier",
                newScore,
                newScore - runningScore
            )
        )
        runningScore = newScore
        val evaluationScore = EvaluationScore(runningScore, scoreContributionList)
        return evaluationScore
    }
}