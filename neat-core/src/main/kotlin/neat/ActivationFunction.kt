package neat

import kotlin.math.*

typealias ActivationFunction = (Float) -> Float

class ActivationGene(val name: String, val activationFunction: ActivationFunction)

fun sigmoidalTransferFunction(x: Float): Float = min(4f, max(-4f, x)).let { 1.div(1 + exp(-4.9f * it)) }

fun baseActivationFunctions(): List<ActivationGene> {
    return with(Activation) {
        listOf(
            identity,
            sigmoidal,
            )
    }
}

val SigmoidalTransferFunction: ActivationFunction = ::sigmoidalTransferFunction

object Activation {
    val identity = ActivationGene("identity", identity())
    val sigmoidal = ActivationGene("sigmoidal", SigmoidalTransferFunction)

    object CPPN {
        val sine = ActivationGene("sine") { sin(2 * it) }
        val bipolarGaussian = ActivationGene("bipolarGaussian") { (2f * exp((2.5f * it).pow(2) * -1)) - 1f }
        val bipolarSigmoid = ActivationGene("bipolarSigmoid") { (2f / (1f + exp(-4.9f * it))) - 1f }
        val gaussian = ActivationGene("gaussian") { exp(-((2.5f * it).pow(2))) }
        val linear = ActivationGene("linear") {
            when {
                it < -1f -> -1f
                it > 1f -> 1f
                else -> it
            }
        }
        val functions = listOf(sine, bipolarGaussian, bipolarSigmoid, gaussian, linear)
    }

    val ramp = ActivationGene("ramp") { 1f - (2f * (it - floor(it))) }
    val activationMap = baseActivationFunctions().toMap { it.name }
}

fun main() {
    (0 until 820).forEach { x ->
        (0 until 360).forEach { y ->
//            val xVal = Activation.bipolarSigmoid.activationFunction(x.toFloat())
//            val yVal = Activation.bipolarSigmoid.activationFunction(y.toFloat())
//            println("$xVal, $yVal")
        }
    }
}