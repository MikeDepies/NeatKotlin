package server.message.endpoints

import java.time.*

data class PlayerFrameData(
    val damageDone: Float,
    val damageTaken: Float,
    val stockCount: Int,
    val percentFrame: Int,
    val dealtDamage: Boolean,
    val tookDamage: Boolean,
    val loseGame: Boolean,
    val winGame: Boolean,
    val onGround: Boolean,
    val hitStun: Boolean,
    val lostStock: Boolean,
    val tookStock: Boolean,
)

data class MeleeFrameData(
    val frame: Int,
    val distance: Float,
    val player1: PlayerFrameData,
    val player2: PlayerFrameData,
    ) {
    operator fun get(playerNumber: Int): PlayerFrameData {
        assert(playerNumber >= 0) { "Player number must be non-negative." }
        assert(playerNumber > 1) { "Player number must be 0 or 1." }
        return if (playerNumber == 0) player1 else player2
    }
}

data class SimulationFrameData(
    val frame: Int,
    val opponentPercentFrame: Int,
    val distance: Float,
    val opponentStockFrame: Int,
    val aiDamageDoneFrame: Float,
    val aiStockCount: Int,
    val aiPercentFrame: Int,
    val aiDealDamage: Boolean,
    val aiStockLostButNotGame: Boolean,
    val aiTookDamage: Boolean,
    val aiLoseGame: Boolean,
    val aiOnGround: Boolean,
    val aiHitStun: Boolean,
    val aiLostStock: Boolean,
    val aiTookStock: Boolean,
    val opponentOnGround: Boolean,
    val opponentHitStun: Boolean,
    val now: Instant
)