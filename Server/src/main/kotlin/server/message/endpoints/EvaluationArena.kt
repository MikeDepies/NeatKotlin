//package server.message.endpoints
//
//import FrameOutput
//import FrameUpdate
//import mu.KotlinLogging
//import neat.ActivatableNetwork
//import neat.FitnessModel
//import neat.model.NeatMutator
//import neat.toNetwork
//import java.time.Duration
//import java.time.Instant
//import kotlin.math.max
//import kotlin.math.pow
//
//private val log = KotlinLogging.logger { }
//
//data class EvaluationData(val simulationFrameData: SimulationFrameData)
//
//fun List<Float>.toFrameOutput(controllerId : Int): FrameOutput {
//    fun bool(index: Int) = get(index).let {
//        when {
//            it.isNaN() -> false
//            it.isInfinite() -> true
//            else -> it > .5
//        }
//    }
//    fun clamp(index: Int) = get(index).let {
//        when {
//            it < 0 -> 0f
//            it > 1 -> 1f
//            it.isNaN() -> 0f
//            it.isInfinite() -> 1f
//            else -> it
//        }
//    }
//    val leftShoulderActivation = clamp(7)
//    return FrameOutput(
//        controllerId = controllerId,
//        a = bool(0),
//        b = bool(1),
//        y = bool(2),
//        z = false/*bool(3)*/,
//        cStickX = clamp(3),
//        cStickY = clamp(4),
//        mainStickX = clamp(5),
//        mainStickY = clamp(6),
//        leftShoulder = when {
//            leftShoulderActivation > .8f -> 1f
//            leftShoulderActivation < .2f -> 0f
//            else -> ((leftShoulderActivation - .2f)/.6f)
//        },
//        rightShoulder = 0f//clamp(9)
//    )
//}
