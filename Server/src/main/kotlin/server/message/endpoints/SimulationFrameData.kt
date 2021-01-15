package server.message.endpoints

import java.time.*


data class SimulationFrameData(
    val frame: Int,
    val damageDoneFrame: Float,
    val opponentPercentFrame: Int,
    val percentFrame: Int,
    val aiStockFrame: Int,
    val opponentStockFrame: Int,
    val wasDamageDealt: Boolean,
    val distance: Float,
    val wasStockButNotGameLost: Boolean,
    val tookDamage: Boolean,
    val wasGameLost: Boolean,
    val stockLoss: Boolean,
    val aiOnGround: Boolean,
    val opponentOnGround: Boolean,
    val hitStun: Boolean,
    val opponentHitStun: Boolean,
    val wasOneStockTaken: Boolean,
    val now: Instant
)