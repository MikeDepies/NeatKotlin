package server

import FrameUpdate
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.ActivatableNetwork
import server.message.endpoints.*
import kotlin.math.absoluteValue

private val logger = KotlinLogging.logger { }

@Serializable
data class ActionBehavior(
    val allActions: List<Int>,
    val recovery: List<List<Int>>,
    val kills: List<Int>,
    val damage: List<Int>,
    val totalDamageDone: Float
)

class NoveltyEvaluatorMultiBehavior(
//    val network: ActivatableNetwork,
    val agentId: Int,
    val evaluationId: Int,
    val generation: Int,
    val controllerId: Int,
    private val meleeState: MeleeState,
    val frameClockFactory: FrameClockFactory
) :
    Evaluator<MinimaCriteria<ActionBehavior>> {
    override val scoreContributionList = mutableListOf<EvaluationScoreContribution>()
    private val lastMeleeFrameData get() = meleeState.lastMeleeFrameData
    private var lastFrameUpdate: FrameUpdate? = null
    var framesWithoutDamage = 0
    var actionsWithoutDamage = 0
    var totalDamage = 0f
    var knocked = false
    var opponentKnocked = false
    var opponentTouchedGround = false
    var knockedOffStage = false
    var opponentHitStun = false
    var hitStun = false
    var framesSinceUnknocked = 0
    var framesSinceOpponentUnknocked = 0
    var opponentKnockedOffStage = false
    val damageActions = mutableListOf<Int>()
    val actions = mutableListOf<Int>()
    val kills = mutableListOf<Int>()
    val recoveryActionSets = mutableListOf(mutableListOf<Int>())
    var damageSinceRecovery = true
    var neverShielded = true
    var offStageTime = 0
    var totalFrames = 0

    /**
     * The finalized Score
     */
    override val score: MinimaCriteria<ActionBehavior>
        get() {
            val meleeFrameData = lastMeleeFrameData

            val met = when {
                generation < 1400 -> true
                else-> (meleeFrameData !== null && (!meleeFrameData.player1.lostStock || (meleeFrameData.player1.lostStock && knocked)))
//                generation < 1200 -> true
//                else -> totalDamage > 0 && (meleeFrameData !== null && (!meleeFrameData.player1.lostStock || (meleeFrameData.player1.lostStock && knocked)))
            }
            return MinimaCriteria(
                met,
                ActionBehavior(actions, recoveryActionSets, kills, damageActions, totalDamage)
            )// + if (meleeState.lastMeleeFrameData?.player1?.onGround == true) 0f else 0f// + bankedScore// + distanceScore// + max(-10f, moveBonus / 2f)
        }

    override fun isFinished(): Boolean {
        val meleeFrameData = lastMeleeFrameData
        val onStage = lastFrameUpdate?.let {
            it.player1.x.absoluteValue < it.stage.rightEdge || it.player1.onGround
        } ?: false
        val opponentOnGround = lastFrameUpdate?.player2?.onGround ?: false
        return (meleeFrameData !== null)  && (meleeFrameData.player1.lostStock ||
                (onStage && opponentOnGround &&
                        (framesWithoutDamage / 60 > AttackTimer.timer)) ) || totalFrames / 60 > AttackTimer.maxTime// || idleGameClock.isFinished(meleeFrameData))
    }

    var recoveryAction = mutableListOf<Int>()
    val previousActions = mutableListOf<Int>()
    var previousAction: Int? = null
    var opponentPreviousAction: Int? = null
    override suspend fun evaluateFrame(frameUpdate: FrameUpdate) {
        val frameData = meleeState.createSimulationFrame(frameUpdate)
        if (lastMeleeFrameData != null) {
            val kockbackCombinedSpeed =
                frameUpdate.player1.run { speedXAttack.absoluteValue + speedYAttack.absoluteValue }
            val offStage =
                frameUpdate.player1.x.absoluteValue > frameUpdate.stage.rightEdge
            val onStage = !offStage && frameUpdate.player1.y >= 0
            if (offStage || frameUpdate.player1.y < 0)
                offStageTime += 1
            else offStageTime = 0
            if (!knocked) {
                knocked = kockbackCombinedSpeed > 0f
            } else if (frameUpdate.player1.onGround) {
                knocked = false
            }
            if (frameUpdate.action1.isShield && neverShielded) {
                neverShielded = false
            }
            if (offStage && knocked) {
                knockedOffStage = true
//                logger.info { "Ai knocked off stage" }
            }
            //recovery time bonus
            if (onStage && knockedOffStage && damageSinceRecovery) {
                framesWithoutDamage = 0
                knockedOffStage = false
                damageSinceRecovery = false
                recoveryActionSets.add(recoveryAction)
                recoveryAction = mutableListOf()
//                logger.info { "Ai recovered to stage" }
            }
            //Opponent Stage Track
            //TODO refactor into tracker concept
            val kockbackCombinedSpeed2 =
                frameUpdate.player2.run { speedXAttack.absoluteValue + speedYAttack.absoluteValue }

            if (!opponentKnocked) {
                opponentKnocked = kockbackCombinedSpeed2 > 0f
                if (opponentKnocked) opponentTouchedGround = false
            } else if (frameUpdate.player2.onGround) {
                opponentTouchedGround = true
            }
            if (opponentTouchedGround && opponentKnocked) {
                framesSinceOpponentUnknocked += 1
            }
            if (framesSinceOpponentUnknocked > 90) {
                opponentKnocked = false
                framesSinceOpponentUnknocked = 0
            }
            val offStage2 =
                frameUpdate.player2.x.absoluteValue > frameUpdate.stage.rightEdge
            val onStage2 = !offStage2 && frameUpdate.player2.onGround && frameUpdate.player2.y >= 0
            if (offStage2 && opponentKnocked) {
                opponentKnockedOffStage = true
//                logger.info { "opponent knocked off stage" }
            }
            //recovery time bonus
            if (onStage2 && opponentKnockedOffStage) {
                opponentKnockedOffStage = false
//                logger.info { "opponent recovered onto stage" }
            }

            if (!frameUpdate.player1.invulnerable && !opponentKnocked && !frameUpdate.player2.invulnerable && !frameUpdate.player2.hitStun)
                framesWithoutDamage += 1

            if (frameData.player1.dealtDamage) {
                damageSinceRecovery = true
                if (framesWithoutDamage > 0)
                    framesWithoutDamage = 0
                actionsWithoutDamage = 0
                totalDamage += frameData.player1.damageDone
                previousAction?.let { damageActions += it }
//                actions += frameUpdate.action1.action
//                actions += frameUpdate.action2.action
//                logger.info { "damage done. Reset frame counter." }
            }
            if (previousAction != frameUpdate.action1.action) {
//                if (frameUpdate.action1.isAttack)
//                if (!frameUpdate.action1.isAttack) {
                if (previousActions.none { it == frameUpdate.action1.action }) {
                    actions += frameUpdate.action1.action
                }
                previousActions += actions
                if (previousActions.size > 2) {
                    previousActions.remove(0)
                }
//                }
                if (knockedOffStage) {
                    recoveryAction += frameUpdate.action1.action
                }
                previousAction = frameUpdate.action1.action

//                logger.trace { "new action added(${actions.size})! $previousAction" }
//                logger.info { actions }
            }
            if (frameData.player2.lostStock) {
                if (opponentKnocked) {
                    opponentPreviousAction?.let {
                        kills += it
                    }
                    damageActions.lastOrNull()?.let { kills += it }
                    framesWithoutDamage = -60 * 4
                    actionsWithoutDamage = 0
                }
//                opponentKnockedOffStage = false
//                opponentKnocked = false
//                framesSinceOpponentUnknocked = 0
//                opponentTouchedGround = false
//                logger.info { "Kill occurred. Reset frame counter." }
            }

            if (opponentPreviousAction != frameUpdate.action2.action) {
                opponentPreviousAction = frameUpdate.action2.action
            }
        }
        meleeState.lastMeleeFrameData = frameData
        lastFrameUpdate = frameUpdate
        totalFrames+=1
    }

    override fun finishEvaluation() {
        logger.info {
            """Finished eval: 
            |Actions: ${actions.size} 
            |DamageActions: ${damageActions.size} 
            |RecoveryActions: ${recoveryActionSets.size}
            |KillActions: ${kills.size}""".trimMargin()
        }
    }
}
