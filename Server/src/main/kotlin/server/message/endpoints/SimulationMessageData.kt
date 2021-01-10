import kotlinx.serialization.Serializable

@Serializable
data class PlayerDataUpdate(
    val stock: Int,
    val x: Float,
    val y: Float,
    val speedAirX: Float,
    val speedGroundX: Float,
    val speedXAttack: Float,
    val speedYAttack: Float,
    val speedY: Float,
    val percent: Int,
    val facingRight: Boolean,//10
    val ecb: EnvironmentalCollisionBox, //<-8
    val isGround: Boolean,
    val isHitStun: Boolean//20
)

@Serializable
data class Position(val x: Float, val y: Float)

@Serializable
data class EnvironmentalCollisionBox(val left: Position, val top: Position, val right: Position, val bottom: Position)

@Serializable
data class BlastZone(val left: Float, val top: Float, val right: Float, val bottom: Float)

@Serializable
data class ActionData(//8
    val action: Int,
    val isAttack: Boolean,
    val isGrab: Boolean,
    val isBMove: Boolean,
    val isShield: Boolean,
    val rangeBackward: Float,
    val rangeForward: Float,
    val hitBoxCount: Int,
)

@Serializable
data class PlatformPosition(val height: Float, val leftEdge: Float, val rightEdge: Float)//3

@Serializable
data class FrameUpdate(
    val player1: PlayerDataUpdate, //18
    val player2: PlayerDataUpdate, //18
    val action1: ActionData, //8
    val action2: ActionData, //8
    val distance: Float //1
)

@Serializable
data class StageData(val stageId: Int, val blastZone: BlastZone, val edgePosition: Float)

@Serializable
data class StagePlatformUpdateData(
    val leftPlatform: PlatformPosition?,
    val rightPlatform: PlatformPosition?,
    val topPlatform: PlatformPosition?,
    val randallPlatform: PlatformPosition?
)

@Serializable
data class FrameOutput(
    val a: Boolean,
    val b: Boolean,
    val y: Boolean,
    val z: Boolean,
    val cStickX: Float,
    val cStickY: Float,
    val mainStickX: Float,
    val mainStickY: Float,
    val leftShoulder: Float,
    val rightShoulder: Float
)

