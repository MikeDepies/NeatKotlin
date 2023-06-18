package neat.mutation

import neat.NeatExperiment

import kotlin.random.Random


fun NeatExperiment.weightPerturbation(range: Float) = (gaussian.nextGaussian().toFloat() * (range ))

// See Knuth, ACP, Section 3.4.1 Algorithm C.
class Gaussian(val random: kotlin.random.Random) {
    var haveNextNextGaussian : Boolean = false
    var nextNextGaussian : Double = 0.0

    fun nextGaussian(): Double {
        // See Knuth, ACP, Section 3.4.1 Algorithm C.
        if (haveNextNextGaussian){
            haveNextNextGaussian = false
            return nextNextGaussian
        } else {
            var v1: Double
            var v2: Double
            var s: Double
            do {
                v1 = 2 * random.nextDouble() - 1 // between -1 and 1
                v2 = 2 * random.nextDouble() - 1 // between -1 and 1
                s = v1 * v1 + v2 * v2
            } while (s >= 1 || s == 0.0)
            val multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s)
            nextNextGaussian = v2 * multiplier
            haveNextNextGaussian = true
            return v1 * multiplier
        }
    }
}

fun main() {

    Gaussian(Random(1)).run {
        repeat(100) {
            println(nextGaussian())
        }
    }
}