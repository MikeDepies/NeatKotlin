package neat.novelty

import kotlin.math.*

fun euclidean(a : List<Float>, b : List<Float>): Float {
    return sqrt(a.indices.map {
        (a[it] - b[it]).pow(2)
    }.sum())
}