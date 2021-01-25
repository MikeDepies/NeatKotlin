package server

import FrameUpdate
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import server.message.endpoints.MeleeFrameData
import server.message.endpoints.MeleeState
import server.message.endpoints.PlayerFrameData
import server.message.endpoints.createSimulationFrame

private val logger = KotlinLogging.logger { }

class SimpleEvaluator(
    val agentId: Int,
    val generation: Int,
    val controllerId: Int,
    private val meleeState: MeleeState,
    val baseScore: Float,
    frameClockFactory: FrameClockFactory,
    private val clockChannel: SendChannel<EvaluationClocksUpdate>
) :
    Evaluator {
    var runningScore: Float = baseScore
    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
    private var firstFrame = true
    private val damageClock = frameClockFactory.countDownClockSeconds("Damage Clock", 3f)
    private val graceClock = frameClockFactory.countDownClockSeconds("Grace Clock", 3f)
    private val hitStunClock = frameClockFactory.countDownClockMilliseconds("HitStun Clock", 200)
    private val enemyHitStunClock = frameClockFactory.countDownClockMilliseconds("P2 HitStun Clock", 200)
    private val landedClock = frameClockFactory.countDownClockMilliseconds("Landed Clock", 0)
    private val stockTakenClock = frameClockFactory.countDownClockSeconds("StockTaken Clock", 6f)
    private val idleGameClock = frameClockFactory.countDownClockSeconds("No Damage Clock", 8f)
    val stockTakeBonus = 30
    var cumulativeDamage = 0f
    var cumulativeDamageTaken = 0f
    var currentStockDamage = 0f
    var comboActive = false
    var comboSequence = comboSequence().iterator()
    var bankedScore = 0f
    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()
    val clockList = listOf(
        damageClock,
        hitStunClock,
        graceClock,
        enemyHitStunClock,
        landedClock,
        stockTakenClock
    )

    /**
     * The finalized Score
     */
    override val score: Float
        get() {
            return runningScore + bankedScore
        }

    override fun isFinished(): Boolean {
        val meleeFrameData = meleeState.lastMeleeFrameData
        val clocksFinished = meleeFrameData !== null && clocksFinished(meleeFrameData)
        val playerStatusPass = meleeFrameData !== null && isPlayerStatusReady(meleeFrameData.player1)
        val opponentStatusPass = meleeFrameData !== null && isPlayerStatusReady(meleeFrameData.player2)
        val playtimeExpired = clocksFinished && playerStatusPass && opponentStatusPass
        if (!clocksFinished)
            idleGameClock.start(meleeFrameData?.frame ?: -200)
        if (meleeFrameData?.player1?.lostStock == true || meleeFrameData?.player2?.lostStock == true || clocksFinished) {
            logger.trace { "$controllerId - Clocks Finished: $clocksFinished" }
            logger.trace { "$controllerId - Status Check: $playerStatusPass - $opponentStatusPass" }
            logger.trace { "$controllerId - playerLostStock: ${meleeFrameData?.player1?.lostStock} - ${meleeFrameData?.player2?.lostStock}" }
        }

        return playtimeExpired || (meleeFrameData !== null && meleeFrameData.player1.lostStock) || (meleeFrameData !== null && idleGameClock.isFinished(meleeFrameData))
    }

    private fun clocksFinished(meleeFrameData: MeleeFrameData): Boolean {
        return clockList.all {
            val finished = it.isFinished(meleeFrameData)
            finished
        }
    }

    override suspend fun evaluateFrame(frameUpdate: FrameUpdate) {
        val frameData = meleeState.createSimulationFrame(frameUpdate)
        if (lastMeleeFrameData != null) {
            val frameNumber = frameData.frame
            val player1 = frameData.player1
            val player2 = frameData.player2
            val lastPlayer1 = meleeState.lastMeleeFrameData?.player1
            val lastPlayer2 = meleeState.lastMeleeFrameData?.player2
            //Perform evaluations
            checkClockEvents(frameNumber, frameData)
            if (player1.tookStock && cumulativeDamage > 0) scoreStockTake(frameNumber)
            if (player1.lostStock) {
//            val scorePenalized = if (frameData.isPlayerInAirFromKnockBack(0)) {
//                val newRunningScore = runningScore / 8
//                scoreContributionList.add(
//                    EvaluationScoreContribution(
//                        "Stock Loss from KnockBack",
//                        newRunningScore,
//                        newRunningScore - runningScore
//                    )
//                )
//                newRunningScore
//            } else {
//                val newRunningScore = 0f
//                scoreContributionList += EvaluationScoreContribution(
//                    "Stock Loss SD",
//                    newRunningScore,
//                    newRunningScore - runningScore
//                )
//                newRunningScore
//            }
//                runningScore -= baseScore
            }
            if (player1.damageDone > 1)
                cumulativeDamage += player1.damageDone
            if (player1.dealtDamage && player1.damageDone > 1) {
//            logger.info { "DAMAGE DEALT" }
                val comboMultiplier = comboSequence.next()
                comboActive = true
                val newRunningScore = runningScore + (player1.damageDone * comboMultiplier)
                scoreContributionList += EvaluationScoreContribution(
                    "Damage Dealt (${player1.damageDone} * $comboMultiplier)",
                    newRunningScore,
                    newRunningScore - runningScore
                )
                runningScore = newRunningScore
            }
//        if (player1.damageTaken > 0 || player1.tookDamage)
//            logger.info { "DAMAGE TAKEN ${player1.tookDamage}" }
            if (player1.damageTaken > 0)
                cumulativeDamageTaken += player1.damageTaken
            if (player2.lostStock)
                currentStockDamage = 0f
            else currentStockDamage += player1.damageDone
            if (player1.tookDamage && comboActive) {
                logger.info { "reset combo sequence" }
                comboSequence = comboSequence().iterator()
                comboActive = false
            }
            if (player1.winGame) {
                clockList.forEach { it.cancel() }
                firstFrame = true
            }
        } else {
            idleGameClock.start(frameData.frame)
        }
        //Then update to the new frame.
        meleeState.lastMeleeFrameData = frameData
//        if (frameNumber % 4 == 0)
//            sendClockUpdates(frameNumber)
    }

    private suspend fun sendClockUpdates(frameNumber: Int) {
        val clockUpdateList = clockList.map { countDownClock ->
            EvaluationClockUpdate(
                clock = countDownClock.clockId,
                framesLeft = countDownClock.startFrame?.let { frameNumber - it } ?: 0,
                frameStart = countDownClock.startFrame ?: -1,
                frameLength = countDownClock.toFrameLength()
            )
        }
        clockChannel.send(EvaluationClocksUpdate(clockUpdateList, frameNumber, agentId, generation))

    }

    private fun scoreStockTake(
        frameNumber: Int
    ) {
        stockTakenClock.start(frameNumber)

        val stockTakeScore = runningScore + stockTakeBonus
        scoreContributionList.add(
            EvaluationScoreContribution(
                "Stock Take Modifier",
                stockTakeScore,
                stockTakeScore - runningScore
            )
        )
        bankedScore += stockTakeScore
        runningScore = 0f
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
        !this[playerNumber].hitStun && lastMeleeFrameData?.let { it[playerNumber].hitStun } ?: false

    private fun MeleeFrameData.playerLanded(playerNumber: Int) =
        this[playerNumber].onGround && lastMeleeFrameData?.let { !it[playerNumber].onGround } ?: false

    private fun isPlayerStatusReady(playerFrameData: PlayerFrameData) =
        !playerFrameData.hitStun && playerFrameData.onGround

    private fun MeleeFrameData.isPlayerInAirFromKnockBack(playerNumber: Int) =
        !this[playerNumber].onGround && lastMeleeFrameData?.let { it[playerNumber].tookDamage } ?: false

    private fun startGraceClockOnFirstFrame(frameNumber: Int) {
        if (firstFrame && meleeState.lastMeleeFrameData?.player1?.onGround == true) {
            graceClock.start(frameNumber)
            firstFrame = false
        } else if (firstFrame) {
            idleGameClock.start(frameNumber                 )
        }
    }

    override fun finishEvaluation(): EvaluationScore {
        /*if (runningScore < 8) {
            scoreContributionList.add(EvaluationScoreContribution("Damage Minimum Threshold", 0f, runningScore * -1))
            0f
        } else {
//            val pow = runningScore.pow(2)
//            scoreContributionList.add(EvaluationScoreContribution("Finish Bonus (Score^2)", pow, pow - runningScore))
//            pow
            runningScore
        }*/
//        val cumulativeDmgRatio = max(cumulativeDamage, 1f) / max(cumulativeDamageTaken / 4, 1f)
//        val newScore = score * cumulativeDmgRatio
//        logger.info { "score Before: $runningScore : (${max(cumulativeDamage, 1f) / max(cumulativeDamageTaken, 1f)} <- max($cumulativeDamage, 1f) / max($cumulativeDamageTaken, 1f)" }
//        scoreContributionList.add(
//            EvaluationScoreContribution(
//                "Cumulative Ratio Modifier",
//                newScore,
//                newScore - runningScore
//            )
//        )
//        runningScore = newScore
//        val evaluationScore = EvaluationScore(agentId, runningScore, scoreContributionList)
        return EvaluationScore(agentId, score, scoreContributionList)
    }
}

@Serializable
data class EvaluationClocksUpdate(
    val clocks: List<EvaluationClockUpdate>,
    val frame: Int,
    val agentId: Int,
    val generation: Int
)

@Serializable
data class EvaluationClockUpdate(val clock: String, val framesLeft: Int, val frameStart: Int, val frameLength: Int)