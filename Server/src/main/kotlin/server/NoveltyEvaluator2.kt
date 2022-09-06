package server

import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

data class MinimaCriteria<T>(val met: Boolean, val behavior: T)
//class NoveltyEvaluator(
//    val network: ActivatableNetwork,
//    val agentId: Int,
//    val evaluationId: Int,
//    val generation: Int,
//    val controllerId: Int,
//    private val meleeState: MeleeState,
//    val frameClockFactory: FrameClockFactory
//) :
//    Evaluator<MinimaCriteria<List<Int>>> {
//    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()
//    val actions = mutableListOf<Int>()
//    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
//    private var lastFrameUpdate: FrameUpdate? = null
//    var framesWithoutDamage = -60 * 2
//    var actionsWithoutDamage = 0
//    var totalDamage = 0f
//    var knockedOffStage = false
//
//    /**
//     * The finalized Score
//     */
//    override val score: MinimaCriteria<List<Int>>
//        get() {
//            val met = when {
//                generation < 100 -> true
//                else -> totalDamage > 0
//            }
//            return MinimaCriteria(
//                met,
//                actions
//            )// + if (meleeState.lastMeleeFrameData?.player1?.onGround == true) 0f else 0f// + bankedScore// + distanceScore// + max(-10f, moveBonus / 2f)
//        }
//
//    override fun isFinished(): Boolean {
//        val meleeFrameData = lastMeleeFrameData
//        val onStage = lastFrameUpdate?.let {
//            it.player1.x.absoluteValue < it.stage.rightEdge || it.player1.onGround
//        } ?: false
//        val opponentOnGround = lastFrameUpdate?.player2?.onGround ?: false
//        return (meleeFrameData !== null) && (meleeFrameData.player1.lostStock ||
//                (onStage && opponentOnGround &&
//                        (framesWithoutDamage / 60 > AttackTimer.timer)))// || idleGameClock.isFinished(meleeFrameData))
//    }
//
//    var previousAction: Int? = null
//    override suspend fun evaluateFrame(frameUpdate: FrameUpdate) {
//        val frameData = meleeState.createSimulationFrame(frameUpdate)
//        if (lastMeleeFrameData != null) {
//            framesWithoutDamage += 1
//            val kockbackCombinedSpeed = frameUpdate.player1.run { speedXAttack.absoluteValue + speedYAttack.absoluteValue }
//            val offStage = frameUpdate.player1.x.absoluteValue > frameUpdate.stage.rightEdge
//            val onStage = !offStage
//            val inKnockback = kockbackCombinedSpeed > 0f
//            if (offStage && inKnockback) {
//                knockedOffStage = true
//            }
//            //recovery time bonus
//            if (onStage && knockedOffStage) {
//                framesWithoutDamage = 0
//                knockedOffStage = false
//            }
//            if (frameData.player1.dealtDamage) {
//                framesWithoutDamage = 0
//                actionsWithoutDamage = 0
//                totalDamage += frameData.player1.damageDone
//
////                actions += frameUpdate.action1.action
////                actions += frameUpdate.action2.action
//                logger.trace { "damage done. Reset frame counter." }
//            }
//            if (previousAction != frameUpdate.action1.action) {
////                if (frameUpdate.action1.isAttack)
//                actions += frameUpdate.action1.action
//                previousAction = frameUpdate.action1.action
//                logger.trace { "new action added(${actions.size})! $previousAction" }
////                logger.info { actions }
//            }
//            if (frameData.player2.lostStock) {
//                framesWithoutDamage = 0
//                actionsWithoutDamage = 0
//                logger.trace { "Kill occurred. Reset frame counter." }
//            }
//        }
//        meleeState.lastMeleeFrameData = frameData
//        lastFrameUpdate = frameUpdate
//    }
//
//    override fun finishEvaluation() {
//        logger.info { "Finished eval: ${actions}" }
//    }
//}
