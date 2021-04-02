package server

import FrameUpdate
import mu.KotlinLogging
import neat.ActivatableNetwork
import server.message.endpoints.*
import java.lang.Float.min
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
    val frameClockFactory: FrameClockFactory,
    var resource: Float = 1250f
) :
    Evaluator {
    private val startingResource = resource
    private var runningScore: Float = baseScore
    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
    private var firstFrame = true
    val stockTakeBonus = 200f
    var cumulativeDamage = 0f
    var cumulativeDamageTaken = 0f
    var currentStockDamage = 0f
    var comboActive = false
    var comboSequence = comboSequence().iterator()
    var bankedScore = 0f
    val xSpeedData = mutableListOf<Float>()
    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()
    private val idleGameClock = frameClockFactory.countDownClockSeconds("No Damage Clock", 4f)
    var prevX = 0f
    var prevShieldButton = false
    var prevWasAttack = false
    var initialDistance: Float? = null
    var turnOffDistanceBonus = false
    var numberOfAttacksWithoutHit = 0

    /**
     * The finalized Score
     */
    override val score: Float
        get() {
//            val moveBonus = (xSpeedData.average().toFloat().takeIf { !it.isNaN() } ?: 0f) * 700
//            logger.info { "$runningScore + $bankedScore + ${max(-10f, moveBonus)}" }
            return runningScore// + if (meleeState.lastMeleeFrameData?.player1?.onGround == true) 0f else 0f// + bankedScore// + distanceScore// + max(-10f, moveBonus / 2f)
        }

    override fun isFinished(): Boolean {
        val meleeFrameData = meleeState.lastMeleeFrameData
        val clocksFinished = resource <= 0
        val playerStatusPass =
            meleeFrameData !== null && isPlayerStatusReady(meleeFrameData.player1) && !(lastFrameUpdate?.action1?.isAttack
                ?: false) || lastFrameUpdate?.action1?.action == edgeHangAction
        val opponentStatusPass =
            meleeFrameData !== null && !meleeFrameData.player2.hitStun && !opponentInAirKnockback
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

    var opponentInAirKnockback = false
    val edgeHangAction = 253
    var lastFrameUpdate: FrameUpdate? = null
    var resourceWell = startingResource * 1000
    var framesSinceLastDamage = 0f
    var scoreWell = 1f
    private val frameCost = 100 * frameClockFactory.frameTime
    override suspend fun evaluateFrame(frameUpdate: FrameUpdate) {

//        if (controllerId == 1) logger.info { "getting data?" }
        val frameData = meleeState.createSimulationFrame(frameUpdate)
        if (lastMeleeFrameData != null) {
            if (initialDistance === null && (frameUpdate.player1.onGround)) {
                initialDistance = frameUpdate.distance
                logger.info { "$controllerId - Initial distance: $initialDistance " }
            }
//            if (initialDistance === null) {
//                resource = startingResource
//            }
//            resource -= frameCost

            val output = network.output().toFrameOutput(controllerId)
            output.run {
                if (a) resource -= frameCost * 4
                if (b) resource -= frameCost * 4
                if (y) resource -= frameCost * 4
                if (z) resource -= frameCost * 4
                if ((cStickX - .5f).absoluteValue > .01) resource -= frameCost * 4
                if ((cStickY - .5f).absoluteValue > .01) resource -= frameCost * 4
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
            if (lastPlayer2?.tookDamage == true && !player2.onGround && !opponentInAirKnockback) {
                logger.info { "$controllerId - Knocked opponent in air" }
                opponentInAirKnockback = true
                initialDistance = null
            }
            if (player2.onGround && opponentInAirKnockback) {
                logger.info { "$controllerId - Opponent returned to ground from knockback" }
                opponentInAirKnockback = false
            }
            if (player2.onGround && turnOffDistanceBonus) {
                logger.info { "$controllerId - Turn on distance bonus" }
                turnOffDistanceBonus = false
            }
            if (player2.onGround && comboActive) {
                logger.info { "reset combo sequence" }
                comboSequence = comboSequence().iterator()
                comboActive = false
            }
            val timeCost = 300 * 16
            val peachParasol = 103
            val peachParasolFall = 370
            val isAttack = frameUpdate.action1.isAttack
            val rollShieldSpotDodgeActions = listOf(188, 189, 190, 196, 197, 198, 233, 234, 179, 235)
            val isShield = frameUpdate.action1.action in rollShieldSpotDodgeActions
            val currentXSpeedAbs =
                frameUpdate.player1.speedGroundX.absoluteValue + min(1f, frameUpdate.player1.speedAirX.absoluteValue)
            if (frameUpdate.player1.y >=0 && frameUpdate.player1.x.absoluteValue < (frameUpdate.stage.rightEdge - 5)
                && scoreWell > 0
            ) {
                val score = max(.001f, scoreWell * .01f * currentXSpeedAbs) / if (!isAttack
                        && !isShield) 1 else 10

                runningScore += score
                scoreWell -= score
            }

            if (isAttack /*&& listOf(
                    peachParasol,
                    peachParasolFall,
                    363,
                    369
                ).none { it == frameUpdate.action1.action }*/
            ) {
//                logger.info { "attack captured ${frameUpdate.action1.action}" }
//                runningScore -= .0125f

                val fastForwardSteps = 1f
//                resource -= frameCost * 150  * fastForwardSteps * numberOfAttacksWithoutHit
//                runningScore -= .01f
                if (frameUpdate.action1.actionFrame == 1) {
                    resource -= 4_000 * numberOfAttacksWithoutHit
                    numberOfAttacksWithoutHit++
                    logger.info { "Attack registered: $numberOfAttacksWithoutHit - $resource/$resourceWell" }
                }
            } else if (currentXSpeedAbs == 0f) {
                val fastForwardSteps = 1f
                resource -= frameCost * 300
//                        repeat(fastForwardSteps) {
//                            xSpeedData += currentXSpeedAbs
//                        }
//                    runningScore -= .2f
            }
            prevWasAttack = isAttack

//            if (player1.winGame)
//                xSpeedData.clear()

            if (isShield) {
//                xSpeedData += 0f
                resource -= frameCost * 300
//                runningScore -= .1f

            }
            val moveTimeBonus = currentXSpeedAbs * 2000
            if (resourceWell > moveTimeBonus && !isShield && frameData.distance > 15) {
                resource += moveTimeBonus
                resourceWell -= moveTimeBonus
            }
            if (framesSinceLastDamage > 60 * 30) {
                resource -= 1_00f * (framesSinceLastDamage / 60)
            }
//            if (frameNumber % 16 == 0) {

//                    xSpeedData += currentXSpeedAbs
//            }
            prevX = frameUpdate.player1.x
            if (player1.tookStock && currentStockDamage > 1 && opponentInAirKnockback) scoreStockTake(
                frameNumber,
                player2
            )
            if (player1.dealtDamage && (player1.damageDone > 1 || frameData.distance < 40)) {
                numberOfAttacksWithoutHit = 0
                framesSinceLastDamage = 0f
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
//                runningScore = newRunningScore
                scoreWell += (player1.damageDone * comboMultiplier)
                if (resourceWell > 0) {
                    val cost = (player1.damageDone * 5_000) /// max(1f, (frameData.distance - 40))
                    resource += cost
                    resourceWell -= cost
                    logger.info { "resource: $resource well: $resourceWell" }
                }
//                if (frameData.distance < 20)
//                resource += 45f * player1.damageDone
            }
//        if (player1.damageTaken > 0 || player1.tookDamage)
//            logger.info { "DAMAGE TAKEN ${player1.tookDamage}" }
            if (player1.damageTaken > 1) {
                cumulativeDamageTaken += player1.damageTaken
//                resource -= player1.damageTaken * 12f
//                runningScore -= player1.damageTaken / 4
            }
            if (player1.lostStock) {
//                val deathPenalty = max(baseScore, runningScore * .4f)
//                if (runningScore >= baseScore)
//                runningScore = min(0f, runningScore - 2000f)
//                runningScore -= (stockTakeBonus - player1.percentFrame) /4
//                runningScore -= (stockTakeBonus - player1.percentFrame) / 2
            }
            if (player2.lostStock) {
                currentStockDamage = 0f
                turnOffDistanceBonus = true
                logger.info { "$controllerId - Turn off distance bonus" }
            } else if (player1.damageDone > 1) currentStockDamage += player1.damageDone //Hack for damage tick offcamera
//            if (player1.tookDamage && comboActive) {
//                logger.info { "reset combo sequence" }
//                comboSequence = comboSequence().iterator()
//                comboActive = false
//            }
            if (player1.winGame) {
                firstFrame = true
            }
            prevShieldButton = shieldButton
            prevWasAttack = isAttack
            framesSinceLastDamage++
            //                logger.info { "Not Shielding or moving? $controllerId - ${frameUpdate.action1.action} - $isShield" }
        }
        //Then update to the new frame.
        meleeState.lastMeleeFrameData = frameData
        lastFrameUpdate = frameUpdate
//        if (resource < 0) resource = 0f
//        if (runningScore < 0) runningScore = 0f
//        if (frameNumber % 4 == 0)
//            sendClockUpdates(frameNumber)
    }

    private fun scoreStockTake(
        frameNumber: Int, player2: PlayerFrameData
    ) {
        val stockTakeScore = runningScore + stockTakeBonus
//        scoreContributionList.add(
//            EvaluationScoreContribution(
//                "Stock Take Modifier",
//                stockTakeScore,
//                stockTakeScore - runningScore
//            )
//        )
//        bankedScore += runningScore + stockTakeBonus / 2
//        logger.info { "Stock taken $bankedScore" }
//        runningScore += (stockTakeBonus - player2.percentFrame) ///12.5f
        opponentInAirKnockback = false
        if (resourceWell > 0) {
            val cost = min(160_000f, resourceWell)
            resource += cost
            resourceWell -= cost
        }
        scoreWell += (stockTakeBonus - player2.percentFrame)
        val scoreBonus = scoreWell * .5f
        scoreWell -= scoreBonus
        runningScore += scoreBonus
//        resource += 650
    }

    private fun MeleeFrameData.playerExitingHitStun(playerNumber: Int) =
        !this[playerNumber].hitStun && lastMeleeFrameData?.let { it[playerNumber].hitStun } ?: false

    private fun MeleeFrameData.playerLanded(playerNumber: Int) =
        this[playerNumber].onGround && lastMeleeFrameData?.let { !it[playerNumber].onGround } ?: false

    private fun isPlayerStatusReady(playerFrameData: PlayerFrameData) =
        (!playerFrameData.hitStun && playerFrameData.onGround)

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
        return EvaluationScore(agentId, score, scoreContributionList)
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