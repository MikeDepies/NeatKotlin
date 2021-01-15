package server.message.endpoints

import ActionData
import EnvironmentalCollisionBox
import FrameUpdate
import PlayerDataUpdate
import Position

private fun Boolean.toFloat() = if (this) 1f else 0f
suspend fun FrameUpdate.flatten() = sequence<Float> {
    yieldPlayerData(player1)
    yieldPlayerData(player2)
    yieldActionData(action1)
    yieldActionData(action2)
    yield(distance)
}.toList()

private suspend fun SequenceScope<Float>.yieldActionData(actionData: ActionData) {
    with(actionData) {
        yield(action.toFloat())
        yield(isAttack.toFloat())
        yield(isBMove.toFloat())
        yield(isGrab.toFloat())
        yield(isShield.toFloat())
        yield(rangeForward)
        yield(rangeBackward)
        yield(hitBoxCount.toFloat())
    }
}

private suspend fun SequenceScope<Float>.yieldPlayerData(playerDataUpdate: PlayerDataUpdate) {
    with(playerDataUpdate) {
        yield(x)
        yield(y)
        yield(speedAirX)
        yield(speedGroundX)
        yield(speedXAttack)
        yield(speedY)
        yield(speedYAttack)
        yield(facingRight.toFloat())
        yield(percent.toFloat())
        yieldEnvironmentalCollisionBox(ecb)
    }
}

private suspend fun SequenceScope<Float>.yieldEnvironmentalCollisionBox(environmentalCollisionBox: EnvironmentalCollisionBox) {
    yieldPosition(environmentalCollisionBox.bottom)
    yieldPosition(environmentalCollisionBox.left)
    yieldPosition(environmentalCollisionBox.right)
    yieldPosition(environmentalCollisionBox.top)
}

private suspend fun SequenceScope<Float>.yieldPosition(position: Position) {
    yield(position.x)
    yield(position.y)
}