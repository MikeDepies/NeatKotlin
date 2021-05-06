package neat.novelty

interface NoveltyArchive<BEHAVIOR> {
    fun addBehavior(behavior: BEHAVIOR): Float
    fun measure(behavior: BEHAVIOR): Float
    val size : Int
    val behaviors : List<BEHAVIOR>
}

class KNNNoveltyArchive<B>(val k: Int, val behaviorDistanceMeasureFunction: (B, B) -> Float) : NoveltyArchive<B> {
    override val behaviors = mutableListOf<B>()
    override val size: Int
        get() = behaviors.size
    override fun addBehavior(behavior: B): Float {
        val distance = measure(behavior)
        behaviors += behavior
        return distance
    }

    override fun measure(behavior: B): Float {
        return behaviors.map { behaviorDistanceMeasureFunction(behavior, it) }
            .sortedDescending().take(k).average()
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
    KNNNoveltyArchive<List<Int>>(4) { a, b ->
        levenshtein(a.map { it.toChar() }.joinToString(""), b.map { it.toChar() }.joinToString("")).toFloat()
    }
    val l = levenshtein(a.joinToString(""), b.joinToString(""))
    println(l)
    println(euclidean(a.map { it.toFloat() }, b.map { it.toFloat() }))
}
//fun List<Int>.toUtf8String() = joinToString(",") { utf8Decode(it)}