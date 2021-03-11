package neat

import kotlin.math.*

typealias ActivationFunction = (Float) -> Float

class ActivationGene(val name: String, val activationFunction: ActivationFunction)

fun sigmoidalTransferFunction(x: Float): Float = 1.div(1 + exp(-4.9f * x))
fun stepFunction(x: Float): Float = if (x <= 0) 0f else 1f
fun piecewiseLinearFunction(xMin: Float, xMax: Float): (Float) -> Float = {
    when {
        it < xMin -> 0f
        it > xMax -> 1f
        else -> 0f
    }
}
fun lnSafe(x : Float) = if (x <= 0f) ln(.0000000001f) else ln(x)
fun bipolarSigmoid(x: Float): Float = (1 - exp(x)) / (1 + exp(x))
fun leCunTanh(x: Float) = (1.7159f) * tanh((2f / 3f) * x)
fun hardTanh(x: Float) = max(-1f, min(1f, x))
fun relu(x: Float) = max(0f, x)
fun complementaryLogLog(x: Float) = if (x != 0f) 1 - lnSafe(-1 * lnSafe(x)) else 1 - lnSafe(-1 * lnSafe(x))
fun reluCos(x: Float) = max(0f, x) + cos(x)
fun reluSin(x: Float) = max(0f, x) + sin(x)
fun smoothRectifier(x: Float): Float = lnSafe(1 + exp(x))
fun logit(x: Float): Float = lnSafe(x / ((1 - x).takeIf { it != 0f } ?: .0001f))
val Identity: ActivationFunction = { it }
val SigmoidalTransferFunction: ActivationFunction = ::sigmoidalTransferFunction
val StepFunction: ActivationFunction = ::stepFunction
val ComplementaryLogLogFunction = ::complementaryLogLog
val BiPolarSigmoidFunction = ::bipolarSigmoid
val TanhFunction: ActivationFunction = ::tanh
val LeCunTanh: ActivationFunction = ::leCunTanh
val HardTanhFunction: ActivationFunction = ::hardTanh
val AbsoluteFunction: ActivationFunction = ::abs
val ReluFunction: ActivationFunction = ::relu
val ReluCosFunction: ActivationFunction = ::reluCos
val CosineFunction: ActivationFunction = ::cos
val ReluSinFunction: ActivationFunction = ::reluSin
val SmoothRectifierFunction: ActivationFunction = ::smoothRectifier
val LogitFunction: ActivationFunction = ::logit
fun baseActivationFunctions(): List<ActivationGene> {
    return with(Activation) {
        listOf(
            identity,
            sigmoidal,
            step,
            complementaryLogLog,
            bipolarSigmoid,
            tanh,
            tanhLeCun,
            hardTanh,
            absolute,
            relu,
            reluCos,
            reluSin,
            cosine,
            smoothRectifier,
            logit
        )
    }
}

object Activation {
    val identity = ActivationGene("identity", identity())
    val sigmoidal = ActivationGene("sigmoidal", SigmoidalTransferFunction)
    val step = ActivationGene("step", StepFunction)
    val complementaryLogLog = ActivationGene("complementaryLogLog", ComplementaryLogLogFunction)
    val bipolarSigmoid = ActivationGene("biPolarSigmoid", BiPolarSigmoidFunction)
    val tanh = ActivationGene("tanh", TanhFunction)
    val tanhLeCun = ActivationGene("tanhLeCun", LeCunTanh)
    val hardTanh = ActivationGene("hardTanh", HardTanhFunction)
    val absolute = ActivationGene("absolute", AbsoluteFunction)
    val relu = ActivationGene("relu", ReluFunction)
    val reluCos = ActivationGene("reluCos", ReluCosFunction)
    val reluSin = ActivationGene("reluSin", ReluSinFunction)
    val cosine = ActivationGene("cosine", CosineFunction)
    val smoothRectifier = ActivationGene("smoothRectifier", SmoothRectifierFunction)
    val logit = ActivationGene("logit", LogitFunction)
    val gaussian = ActivationGene("gaussian") { exp(-1 * (it * it)) }
    val ramp = ActivationGene("ramp") { 1f - (2f * (it - floor(it))) }
    val activationMap = baseActivationFunctions().toMap { it.name }
}

fun main() {
    (0 until 820).forEach { x->
        (0 until 360).forEach { y ->
            val xVal = Activation.bipolarSigmoid.activationFunction(x.toFloat())
            val yVal = Activation.bipolarSigmoid.activationFunction(y.toFloat())
            println("$xVal, $yVal")
        }
    }
}