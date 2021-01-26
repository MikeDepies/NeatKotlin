package server

import FrameUpdate
import mu.KotlinLogging
import neat.ActivatableNetwork
import server.message.endpoints.*
import kotlin.math.absoluteValue
import kotlin.math.max

private val logger = KotlinLogging.logger { }

class ResourceEvaluator(
    val network: ActivatableNetwork,
    val agentId: Int,
    val generation: Int,
    val controllerId: Int,
    private val meleeState: MeleeState,
    val baseScore: Float,
    frameClockFactory: FrameClockFactory,
    var resource: Float = 150f
) :
    Evaluator {
    var runningScore: Float = baseScore
    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
    private var firstFrame = true
    val stockTakeBonus = 30
    var cumulativeDamage = 0f
    var cumulativeDamageTaken = 0f
    var currentStockDamage = 0f
    var comboActive = false
    var comboSequence = comboSequence().iterator()
    var bankedScore = 0f
    val xSpeedData = mutableListOf<Float>()
    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()
    private val idleGameClock = frameClockFactory.countDownClockSeconds("No Damage Clock", 8f)
    var prevX = 0f
    var prevShieldButton = false

    /**
     * The finalized Score
     */
    override val score: Float
        get() {
            return runningScore + bankedScore + (xSpeedData.average().toFloat().takeIf { !it.isNaN() } ?: 0f) * 5f
        }

    override fun isFinished(): Boolean {
        val meleeFrameData = meleeState.lastMeleeFrameData
        val clocksFinished = resource <= 0
        val playerStatusPass = meleeFrameData !== null && isPlayerStatusReady(meleeFrameData.player1)
        val opponentStatusPass = meleeFrameData !== null && isPlayerStatusReady(meleeFrameData.player2)
        val playtimeExpired = clocksFinished && playerStatusPass && opponentStatusPass

        if (meleeFrameData?.player1?.lostStock == true || meleeFrameData?.player2?.lostStock == true || clocksFinished) {
            logger.trace { "$controllerId - Clocks Finished: $clocksFinished" }
            logger.trace { "$controllerId - Status Check: $playerStatusPass - $opponentStatusPass" }
            logger.trace { "$controllerId - playerLostStock: ${meleeFrameData?.player1?.lostStock} - ${meleeFrameData?.player2?.lostStock}" }
        }
        if (!clocksFinished)
            idleGameClock.start(meleeFrameData?.frame ?: -200)

        return playtimeExpired || (meleeFrameData !== null && meleeFrameData.player1.lostStock) || (meleeFrameData !== null && idleGameClock.isFinished(
            meleeFrameData
        ))
    }

    private val frameCost = 10 * frameClockFactory.frameTime
    override suspend fun evaluateFrame(frameUpdate: FrameUpdate) {
//        if (controllerId == 1) logger.info { "getting data?" }
        val frameData = meleeState.createSimulationFrame(frameUpdate)
        if (lastMeleeFrameData != null) {
            resource -= frameCost

            val output = network.output().toFrameOutput(controllerId)
            output.run {
                if (a) resource -= frameCost
                if (b) resource -= frameCost
                if (y) resource -= frameCost
                if (z) resource -= frameCost * 4
                if ((cStickX - .5f).absoluteValue > .2) resource -= frameCost
                if ((cStickY - .5f).absoluteValue > .2) resource -= frameCost
                if ((leftShoulder > 0)) resource -= frameCost * 4
            }
            val shieldUsed = output.z || output.leftShoulder > 0
            val frameNumber = frameData.frame
            val player1 = frameData.player1
            val player2 = frameData.player2
            val lastPlayer1 = meleeState.lastMeleeFrameData?.player1
            val lastPlayer2 = meleeState.lastMeleeFrameData?.player2
            //Perform evaluations

            if ((prevX - frameUpdate.player1.x).absoluteValue > 1 && !shieldUsed && !prevShieldButton) {
                xSpeedData += frameUpdate.player1.speedGroundX.absoluteValue
                prevX = frameUpdate.player1.x
            } else xSpeedData += 0f
            if (player1.tookStock && currentStockDamage > 1) scoreStockTake(frameNumber)
            if (player1.dealtDamage && player1.damageDone > 1) {
                cumulativeDamage += player1.damageDone
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
                resource += (player1.damageDone * comboMultiplier) * 4f
            }
//        if (player1.damageTaken > 0 || player1.tookDamage)
//            logger.info { "DAMAGE TAKEN ${player1.tookDamage}" }
            if (player1.damageTaken > 1) {
                cumulativeDamageTaken += player1.damageTaken
                resource -= player1.damageTaken / 4f
                if (player1.damageTaken >= 4)
                    runningScore += player1.damageTaken / 4f
            }
            if (player1.lostStock) {
                val deathPenalty = max(baseScore, runningScore * .2f)
                if (runningScore >= baseScore)
                    runningScore -= deathPenalty
            }
            if (player2.lostStock)
                currentStockDamage = 0f
            else if (player1.damageDone > 1) currentStockDamage += player1.damageDone //Hack for damage tick offcamera
            if (player1.tookDamage && comboActive) {
                logger.info { "reset combo sequence" }
                comboSequence = comboSequence().iterator()
                comboActive = false
            }
            if (player1.winGame) {
                firstFrame = true
            }
            prevShieldButton = shieldUsed
        }
        //Then update to the new frame.
        meleeState.lastMeleeFrameData = frameData

        if (resource < 0) resource = 0f
//        if (frameNumber % 4 == 0)
//            sendClockUpdates(frameNumber)
    }

    private fun scoreStockTake(
        frameNumber: Int
    ) {
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
        resource += 250
    }

    private fun MeleeFrameData.playerExitingHitStun(playerNumber: Int) =
        !this[playerNumber].hitStun && lastMeleeFrameData?.let { it[playerNumber].hitStun } ?: false

    private fun MeleeFrameData.playerLanded(playerNumber: Int) =
        this[playerNumber].onGround && lastMeleeFrameData?.let { !it[playerNumber].onGround } ?: false

    private fun isPlayerStatusReady(playerFrameData: PlayerFrameData) =
        !playerFrameData.hitStun && playerFrameData.onGround

    private fun MeleeFrameData.isPlayerInAirFromKnockBack(playerNumber: Int) =
        !this[playerNumber].onGround && lastMeleeFrameData?.let { it[playerNumber].tookDamage } ?: false

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