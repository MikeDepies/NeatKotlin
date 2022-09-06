//import kotlinx.serialization.Serializable
//
//@Serializable
//data class PlayerDataUpdate(
//    val stock: Int,
//    val x: Float,
//    val y: Float,
//    val speedAirX: Float,
//    val speedGroundX: Float,
//    val speedXAttack: Float,
//    val speedYAttack: Float,
//    val speedY: Float,
//    val percent: Int,
//    val facingRight: Boolean,//10
//    val ecb: EnvironmentalCollisionBox, //<-8
//    val onGround: Boolean,
//    val hitStun: Boolean,//20
//    val invulnerable: Boolean,
//    val offStage: Boolean,
//    val character: Int
//)
//
//@Serializable
//data class Position(val x: Float, val y: Float)
//
//@Serializable
//data class EnvironmentalCollisionBox(val left: Position, val top: Position, val right: Position, val bottom: Position)
//
//@Serializable
//data class BlastZone(val left: Float, val top: Float, val right: Float, val bottom: Float)
//
//@Serializable
//data class Platform(val left: Float, val height: Float, val right: Float)
//
//@Serializable
//data class ActionData(
////8
//    val action: Int,
//    val isAttack: Boolean,
//    val isGrab: Boolean,
//    val isBMove: Boolean,
//    val isShield: Boolean,
//    val rangeBackward: Float,
//    val rangeForward: Float,
//    val hitBoxCount: Int,
//    val attackState: Int,
//    val actionFrame: Int,
//)
//
//@Serializable
//data class FrameUpdate(
//    val player1: PlayerDataUpdate, //18
//    val player2: PlayerDataUpdate, //18
//    val action1: ActionData, //10
//    val action2: ActionData, //10
//    val stage: StageData, //19
//    val distance: Float, //1
//    val frame: Int
//)
//
//@Serializable
//data class StageData(
//    val stage: Int,
//    val blastzone: BlastZone, //5
//    val leftEdge: Float,
//    val rightEdge: Float,//7
//    val randall: Platform, //10
//    val platformLeft: Platform, //13
//    val platformRight: Platform,//16
//    val platformTop: Platform//19
//)
//
//
//@Serializable
//data class FrameOutput(
//    val controllerId: Int,
//    val a: Boolean,
//    val b: Boolean,
//    val y: Boolean,
//    val z: Boolean,
//    val cStickX: Float,
//    val cStickY: Float,
//    val mainStickX: Float,
//    val mainStickY: Float,
//    val leftShoulder: Float,
//    val rightShoulder: Float,
//    val start: Boolean = false
//)
//
