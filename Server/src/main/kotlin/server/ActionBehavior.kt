package server

import kotlinx.serialization.Serializable

@Serializable
data class ActionBehavior(
    val allActions: List<Int>,
    val recovery: List<List<Int>>,
    val kills: List<Int>,
    val damage: List<Int>,
    val totalDamageDone: Float,
    val totalDistanceTowardOpponent: Float,
    val playerDied: Boolean,
    val totalFramesHitstunOpponent: Int
)

@Serializable
data class ActionStringedBehavior(
    val allActions: String,
    val recovery: String,
    val kills: String,
    val damage: String,
    val totalDamageDone: Float,
    val totalDistanceTowardOpponent: Float,
    val playerDied: Boolean
)

@Serializable
data class ActionSumBehavior(
    val allActionsCount: Int,
    val recoveryCount: Int,
    val kills: List<Int>,
    val totalDamageDone: Float,
    val totalDistanceTowardOpponent: Float,
    val playerDied: Boolean
)

@Serializable
data class ActionBehaviorInt(
    val allActions: List<Int>,
    val recovery: List<Int>,
    val kills: List<Int>,
    val damage: List<Int>,
    val totalDamageDone: Float,
    val totalDistanceTowardOpponent: Float,
    val playerDied: Boolean,
    val totalFramesHitstunOpponent : Int = 0
)
