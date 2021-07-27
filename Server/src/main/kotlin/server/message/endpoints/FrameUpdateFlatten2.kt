package server.message.endpoints

import ActionData
import BlastZone
import EnvironmentalCollisionBox
import FrameUpdate
import Platform
import PlayerDataUpdate
import Position
import StageData

private fun Boolean.facingDirectionToFloat() = if (this) 1f else -1f
private fun Boolean.toFloat() = if (this) 1f else 0f
suspend fun FrameUpdate.flatten2() = sequence<Float> {
    yieldPlayerData(player1, stage.rightEdge, stage.blastzone.top)
    yieldPlayerData(player2, stage.rightEdge, stage.blastzone.top)
    yieldActionData(action1)
    yieldActionData(action2)
    yieldStageData(stage)
    yield(distance)
}.toList()

private suspend fun SequenceScope<Float>.yieldStageData(stageData: StageData) = with(stageData){
    yield(stage.toFloat())
//    yield(leftEdge)
    yield(rightEdge)
    yieldBlastzones(blastzone)
    yieldPlatform(platformLeft ?: Platform(0f, 0f, 0f))
    yieldPlatform(platformTop ?: Platform(0f, 0f, 0f))
    yieldPlatform(platformRight ?: Platform(0f, 0f, 0f))
}

private suspend fun SequenceScope<Float>.yieldBlastzones(blastzone: BlastZone) = with(blastzone){
    yield(left)
    yield(top)
    yield(right)
    yield(left)
}
private suspend fun SequenceScope<Float>.yieldPlatform(platform: Platform) = with(platform){
    yield(left)
    yield(height)
    yield(right)
}



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
        yield(attackState.toFloat())
        yield(actionFrame.toFloat())
//        yield(actionState)
//        yield(actionFrame)

    }
}

private suspend fun SequenceScope<Float>.yieldPlayerData(playerDataUpdate: PlayerDataUpdate, edge : Float, top : Float) {
    with(playerDataUpdate) {
        yield(character.toFloat())
        yield(x)
        yield(y)
        yield(onGround.toFloat())
        yield(hitStun.toFloat())
        yield(invulnerable.toFloat())
//        yield(offStage.toFloat())
        yield(speedAirX)
        yield(speedGroundX)
        yield(speedXAttack)
        yield(speedY)
        yield(speedYAttack)
        yield(facingRight.facingDirectionToFloat())
        yield(percent.toFloat())
//        yieldEnvironmentalCollisionBox(ecb)
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