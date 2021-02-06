package server

import FrameUpdate
import mu.KotlinLogging
import neat.*
import server.message.endpoints.*
import kotlin.math.*

private val logger = KotlinLogging.logger { }

class ResourceEvaluator(
    val network: ActivatableNetwork,
    val agentId: Int,
    val evaluationId: Int,
    val generation: Int,
    val controllerId: Int,
    private val meleeState: MeleeState,
    val baseScore: Float,
    frameClockFactory: FrameClockFactory,
    var resource: Float
) :
    Evaluator {
    private var runningScore: Float = baseScore
    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
    private var firstFrame = true
    val stockTakeBonus = 600f
    var cumulativeDamage = 0f
    var cumulativeDamageTaken = 0f
    var currentStockDamage = 0f
    var comboActive = false
    var comboSequence = comboSequence().iterator()
    var bankedScore = 0f
    val xSpeedData = mutableListOf<Float>()
    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()
    private val idleGameClock = frameClockFactory.countDownClockSeconds("No Damage Clock", 14f)
    var prevX = 0f
    var prevShieldButton = false
    var prevWasAttack = false
    /**
     * The finalized Score
     */
    override val score: Float
        get() {
//            val moveBonus = (xSpeedData.average().toFloat().takeIf { !it.isNaN() } ?: 0f) * 700
//            logger.info { "$runningScore + $bankedScore + ${max(-10f, moveBonus)}" }
            val distanceScore = 100 - min(100f, lastMeleeFrameData?.distance ?: 100f)
            return runningScore + bankedScore + distanceScore// + max(-10f, moveBonus / 2f)
        }

    override fun isFinished(): Boolean {
        val meleeFrameData = meleeState.lastMeleeFrameData
        val clocksFinished = resource <= 0
        val playerStatusPass =
            meleeFrameData !== null && isPlayerStatusReady(meleeFrameData.player1) || lastFrameUpdate?.action1?.action == edgeHangAction
        val opponentStatusPass =
            meleeFrameData !== null && isPlayerStatusReady(meleeFrameData.player2) || lastFrameUpdate?.action2?.action == edgeHangAction
        val playtimeExpired = clocksFinished && playerStatusPass && opponentStatusPass
        if (meleeFrameData != null && meleeFrameData.player1.lostStock)
            xSpeedData.add(-max(50f, xSpeedData.size.toFloat()))
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

    val edgeHangAction = 253
    var lastFrameUpdate: FrameUpdate? = null
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
            val shieldButton = output.z || output.leftShoulder > 0
            val shieldUsed = shieldButton || prevShieldButton
            val frameNumber = frameData.frame
            val player1 = frameData.player1
            val player2 = frameData.player2
            val lastPlayer1 = meleeState.lastMeleeFrameData?.player1
            val lastPlayer2 = meleeState.lastMeleeFrameData?.player2
            //Perform evaluations
            val platformDropAction = 244
            if (frameUpdate.action1.action == platformDropAction) {
//                runningScore += 500
            }

            val isAttack = frameUpdate.action1.isAttack
            if (isAttack && !prevWasAttack) {
                runningScore -= 50
            }
            val rollShieldSpotDodgeActions = listOf(188, 189, 190, 196, 197, 198, 233, 234, 179, 235)
            val isShield = frameUpdate.action1.action in rollShieldSpotDodgeActions
            if (player1.winGame)
                xSpeedData.clear()
            if (!isShield) {
//                logger.info { "Not Shielding or moving? $controllerId - ${frameUpdate.action1.action} - $isShield" }

                val currentXSpeedAbs =
                    frameUpdate.player1.speedGroundX.absoluteValue + min(1f, frameUpdate.player1.speedAirX.absoluteValue)
                if (currentXSpeedAbs > 0 || frameNumber % 16 == 0) {
                    if (currentXSpeedAbs == 0f) {
                        val fastForwardSteps = 15
                        resource -= frameCost * 8 * fastForwardSteps
                        repeat(fastForwardSteps) {
                            xSpeedData += currentXSpeedAbs
                        }
                    }
                    xSpeedData += currentXSpeedAbs
                }
            } else {
                xSpeedData += 0f
                resource -= frameCost * 8
                if (runningScore > 0) {
                    runningScore -= baseScore * if (shieldUsed) .008f else .002f
                }
            }
            prevX = frameUpdate.player1.x
            if (player1.tookStock && currentStockDamage > 1) scoreStockTake(frameNumber)
            if (player1.dealtDamage && player1.damageDone > 1) {
                cumulativeDamage += player1.damageDone
//            logger.info { "DAMAGE DEALT" }
                val comboMultiplier = comboSequence.next()
                comboActive = true
                val newRunningScore = runningScore + (player1.damageDone * comboMultiplier * 25f) + 50f
                scoreContributionList += EvaluationScoreContribution(
                    "Damage Dealt (${player1.damageDone} * $comboMultiplier)",
                    newRunningScore,
                    newRunningScore - runningScore
                )
                runningScore = newRunningScore
                if (frameData.distance < 20)
                    resource += 200f
            }
//        if (player1.damageTaken > 0 || player1.tookDamage)
//            logger.info { "DAMAGE TAKEN ${player1.tookDamage}" }
            if (player1.damageTaken > 1) {
                cumulativeDamageTaken += player1.damageTaken
                resource -= player1.damageTaken * 12f
                runningScore += player1.damageTaken * 100f
            }
            if (player1.lostStock) {
//                val deathPenalty = max(baseScore, runningScore * .4f)
//                if (runningScore >= baseScore)
                runningScore = 0f
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
            prevShieldButton = shieldButton
            prevWasAttack = isAttack
        }
        //Then update to the new frame.
        meleeState.lastMeleeFrameData = frameData
        lastFrameUpdate = frameUpdate
        if (resource < 0) resource = 0f
        if (runningScore < 0) runningScore = 0f
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
        bankedScore += runningScore + stockTakeBonus/2
        logger.info { "Stock taken $bankedScore" }
        runningScore = stockTakeBonus/2
        resource += 650
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
        logger.info { "Banked $bankedScore" }
        logger.info { "Running Score $runningScore" }
        logger.info { "final $score" }
        return EvaluationScore(evaluationId, agentId, score, scoreContributionList)
    }
}

fun main() {
    var r = 70f
    repeat(6 * 60) {
        r -= 30 * .0025f
    }
    println(r)
    println(70f - r)
}