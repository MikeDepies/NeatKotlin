package neat.mutation

import neat.NeatExperiment

import kotlin.random.Random


fun NeatExperiment.weightPerturbation(range: Float) = (gaussian.nextGaussian().toFloat() * (range ))
class Gaussian(private val random: kotlin.random.Random, private val standardDeviation: Double) {
    private var haveNextNextGaussian: Boolean = false
    private var nextNextGaussian: Double = 0.0

    fun nextGaussian(): Double {
        // See Knuth, ACP, Section 3.4.1 Algorithm C.
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false
            return nextNextGaussian.coerceIn(-1.0, 1.0)
        } else {
            var v1: Double
            var v2: Double
            var s: Double
            do {
                v1 = 2 * random.nextDouble() - 1 // between -1 and 1
                v2 = 2 * random.nextDouble() - 1 // between -1 and 1
                s = v1 * v1 + v2 * v2
            } while (s >= 1 || s == 0.0)
            val multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s) * standardDeviation
            nextNextGaussian = (v2 * multiplier) / (2.0 * standardDeviation)
            haveNextNextGaussian = true
            val normalizedValue = nextNextGaussian / (2.0 * standardDeviation)
            return normalizedValue.coerceIn(-1.0, 1.0) // Clamp the value within -1 and 1
        }
    }
}

fun main() {

    Gaussian(Random(1), .5).run {
        repeat(100) {
            println(nextGaussian())
        }
    }
}