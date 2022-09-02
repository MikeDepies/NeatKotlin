package neat.novelty

import kotlin.streams.toList

interface NoveltyArchive<BEHAVIOR> {
    fun addBehavior(behavior: BEHAVIOR): Float
    fun measure(behavior: BEHAVIOR): Float
    val size : Int
    val behaviors : List<BEHAVIOR>
}

class KNNNoveltyArchive<B>(var k: Int, var noveltyThreshold : Float, val behaviorFilter : (B, B) -> Boolean = {_,_ -> true}, val behaviorDistanceMeasureFunction: (B, B) -> Float) : NoveltyArchive<B> {
    override val behaviors = mutableListOf<B>()
    override val size: Int
        get() = behaviors.size
    override fun addBehavior(behavior: B): Float {
        val distance = measure(behavior)
        if (distance > noveltyThreshold || size == 0)
            behaviors += behavior
        return distance
    }

    override fun measure(behavior: B): Float {

        return behaviors.parallelStream().filter { behaviorFilter(behavior, it) }.map { behaviorDistanceMeasureFunction(behavior, it) }
            .sorted().toList().take(k).average()
            .toFloat()
    }
}

fun main() {
    val a = listOf(1, 2, 4, 3).map {
        it.toChar()
    }
    val b = listOf(1, 2, 3, 4).map {
        it.toChar()
    }
    KNNNoveltyArchive<List<Int>>(4, 3f) { a, b ->
        levenshtein(a.map { it.toChar() }.joinToString(""), b.map { it.toChar() }.joinToString("")).toFloat()
    }
    val l = levenshtein(a.joinToString(""), b.joinToString(""))
    println(l)
    println(euclidean(a.map { it.toFloat() }, b.map { it.toFloat() }))
}
//fun List<Int>.toUtf8String() = joinToString(",") { utf8Decode(it)}