package server.message.endpoints

import ActionData
import EnvironmentalCollisionBox
import FrameUpdate
import PlayerDataUpdate
import Position

private fun Boolean.facingDirectionToFloat() = if (this) 1f else -1f
private fun Boolean.toFloat() = if (this) 1f else 0f
suspend fun FrameUpdate.flatten2() = sequence<Float> {
    yieldPlayerData(player1)
    yieldPlayerData(player2)
    yieldActionData(action1)
    yieldActionData(action2)
    yield(distance)
}.toList()

private suspend fun SequenceScope<Float>.yieldActionData(actionData: ActionData) {
    with(actionData) {
        yield(action.toFloat())
        yield(isAttack.facingDirectionToFloat())
        yield(isBMove.facingDirectionToFloat())
        yield(isGrab.facingDirectionToFloat())
        yield(isShield.facingDirectionToFloat())
        yield(rangeForward)
        yield(rangeBackward)
        yield(hitBoxCount.toFloat())
    }
}

private suspend fun SequenceScope<Float>.yieldPlayerData(playerDataUpdate: PlayerDataUpdate) {
    with(playerDataUpdate) {
        yield(x)
        yield(y)
        yield(onGround.toFloat())
        yield(hitStun.toFloat())
        yield(invulnerable.toFloat())
        yield(offStage.toFloat())
        yield(speedAirX)
        yield(speedGroundX)
        yield(speedXAttack)
        yield(speedY)
        yield(speedYAttack)
        yield(facingRight.facingDirectionToFloat())
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