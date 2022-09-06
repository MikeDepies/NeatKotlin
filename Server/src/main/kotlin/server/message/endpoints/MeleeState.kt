//package server.message.endpoints
//
//import FrameUpdate
//import mu.*
//import java.time.*
//
//private val log = KotlinLogging.logger {}
//
//
//fun MeleeState.createSimulationFrame(lastFrame: FrameUpdate): MeleeFrameData {
//    val aiStockFrame = lastFrame.player1.stock
//    val percentFrame = lastFrame.player1.percent
//    val lastMeleeFrameData1 = lastMeleeFrameData
//    val wasGameLost = lastMeleeFrameData1 !== null && (aiStockFrame) == 0 && lastMeleeFrameData1.player1.stockCount == 1
//    val opponentStockFrame = lastFrame.player2.stock
//    val opponentPercentFrame = lastFrame.player2.percent
//    val wasGameWon = lastMeleeFrameData1 !== null && (opponentStockFrame) == 0 && lastMeleeFrameData1.player2.stockCount == 1
//    val aiTookStock = lastMeleeFrameData1 !== null && (lastMeleeFrameData1.player2.stockCount - opponentStockFrame) == 1
//    val aiLostStock = lastMeleeFrameData1 !== null && (lastMeleeFrameData1.player1.stockCount - aiStockFrame) == 1
//    val damageDoneFrame = if (lastMeleeFrameData1 !== null && !aiTookStock) (opponentPercentFrame - lastMeleeFrameData1.player2.percentFrame).toFloat() else 0f
//    val opponentDamageDoneFrame = if (lastMeleeFrameData1 !== null && !aiLostStock) (percentFrame - lastMeleeFrameData1.player1.percentFrame).toFloat() else 0f
//    return MeleeFrameData(
//        frame = lastFrame.frame,
//        distance = lastFrame.distance,
//        player1 = PlayerFrameData(
//            damageDone = damageDoneFrame,
//            damageTaken = opponentDamageDoneFrame,
//            stockCount = aiStockFrame,
//            percentFrame = percentFrame,
//            dealtDamage = damageDoneFrame > 0,
//            tookDamage = opponentDamageDoneFrame > 0,
//            loseGame = wasGameLost,
//            winGame = wasGameWon,
//            onGround = lastFrame.player1.onGround,
//            hitStun = lastFrame.player1.hitStun,
//            lostStock = aiLostStock,
//            tookStock = aiTookStock,
//            offStage = lastFrame.player1.offStage
//        ),
//        player2 = PlayerFrameData(
//            damageDone = opponentDamageDoneFrame,
//            damageTaken = opponentDamageDoneFrame,
//            stockCount = opponentStockFrame,
//            percentFrame = opponentPercentFrame,
//            dealtDamage = opponentDamageDoneFrame > 0,
//            tookDamage = damageDoneFrame > 0,
//            loseGame = wasGameWon,
//            winGame = wasGameLost,
//            onGround = lastFrame.player2.onGround,
//            hitStun = lastFrame.player2.hitStun,
//            lostStock = aiTookStock,
//            tookStock = aiLostStock,
//            offStage = lastFrame.player2.offStage
//        ),
//    )
//
//}
//
//data class MeleeState(
//    var lastMeleeFrameData: MeleeFrameData?,
////    var agentStart: Instant
//)
//
//fun createEmptyFrameData(): MeleeFrameData {
//    return MeleeFrameData(
//        frame = -1,
//        distance = 0f,
//        player1 = emptyPlayerFrameData(),
//        player2 = emptyPlayerFrameData(),
//    )
//
//}
//
//private fun emptyPlayerFrameData() = PlayerFrameData(
//    damageDone = 0f,
//    damageTaken = 0f,
//    stockCount = -10,
//    percentFrame = 0,
//    dealtDamage = false,
//    tookDamage = false,
//    loseGame = false,
//    winGame = false,
//    onGround = false,
//    hitStun = false,
//    lostStock = false,
//    tookStock = false,
//    offStage = false
//)