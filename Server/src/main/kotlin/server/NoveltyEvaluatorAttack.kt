//package server
//
//import FrameUpdate
//import mu.KotlinLogging
//import neat.ActivatableNetwork
//import server.message.endpoints.*
//import java.lang.Float.min
//import kotlin.math.absoluteValue
//import kotlin.math.max
//import kotlin.math.pow
//
//private val logger = KotlinLogging.logger { }
//
//class NoveltyEvaluatorAttack(
//    val network: ActivatableNetwork,
//    val agentId: Int,
//    val evaluationId: Int,
//    val generation: Int,
//    val controllerId: Int,
//    private val meleeState: MeleeState,
//    val frameClockFactory: FrameClockFactory
//) :
//    Evaluator<List<Int>> {
//    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()
//    val actions = mutableListOf<Int>()
//    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
//    private var lastFrameUpdate: FrameUpdate? = null
//    var framesWithoutDamage = -60 * 2
//    var actionsWithoutDamage = 0
//
//    /**
//     * The finalized Score
//     */
//    override val score: List<Int>
//        get() {
//            return actions// + if (meleeState.lastMeleeFrameData?.player1?.onGround == true) 0f else 0f// + bankedScore// + distanceScore// + max(-10f, moveBonus / 2f)
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
//                        (framesWithoutDamage / 60 > 30)))// || idleGameClock.isFinished(meleeFrameData))
//    }
//
//    var previousAction: Int? = null
//    override suspend fun evaluateFrame(frameUpdate: FrameUpdate) {
//        val frameData = meleeState.createSimulationFrame(frameUpdate)
//        if (lastMeleeFrameData != null) {
//            framesWithoutDamage += 1
//            if (frameData.player1.dealtDamage) {
//                framesWithoutDamage = 0
//                actionsWithoutDamage = 0
//                lastFrameUpdate?.let {
//                    actions += it.action1.action
//                    actions += it.action2.action
//                }
////                actions += frameUpdate.action1.action
////                actions += frameUpdate.action2.action
//                logger.info { "damage done. Reset frame counter." }
//            }
//            if (previousAction != frameUpdate.action1.action) {
////                if (frameUpdate.action1.isAttack)
//                previousAction = frameUpdate.action1.action
//                logger.trace { "new action added(${actions.size})! $previousAction" }
////                logger.info { actions }
//            }
//            if (frameData.player2.lostStock) {
//                lastFrameUpdate?.let {
//                    actions += it.action1.action
//                    actions += it.action2.action
//                }
//                framesWithoutDamage = -60 * 4
//                actionsWithoutDamage = 0
//                logger.info { "Kill occurred. Reset frame counter." }
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
