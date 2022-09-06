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
    val playerDied: Boolean
)