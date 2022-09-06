package server

import mu.KotlinLogging
import neat.novelty.NoveltyArchive
import kotlin.streams.toList
private val logger = KotlinLogging.logger {  }
class KNNNoveltyArchiveWeighted(
    var k: Int,
    var noveltyThreshold: Float,
    val behaviorFilter: (ActionBehavior, ActionBehavior) -> Boolean = { _, _ -> true },
    val behaviorDistanceMeasureFunction: (ActionBehavior, ActionBehavior) -> Float
) :
    NoveltyArchive<ActionBehavior> {
    override val behaviors = mutableListOf<ActionBehavior>()
    override val size: Int
        get() = behaviors.size
    var maxBehavior = ActionBehavior(listOf(), listOf(), listOf(), listOf(), 0.1f, 0f, false)
    override fun addBehavior(behavior: ActionBehavior): Float {
        val distance = measure(behavior)
        if (distance > noveltyThreshold || size == 0)
            behaviors += behavior
        return distance
    }

    override fun measure(behavior: ActionBehavior): Float {
        val value = valueForBehavior(behavior)
        if (value > valueForBehavior(maxBehavior))
            maxBehavior = behavior

        val n = k + value
        logger.info { "K = $n" }
        val distance = behaviors.parallelStream().filter { behaviorFilter(behavior, it) }
            .map { behaviorDistanceMeasureFunction(behavior, it) }
            .sorted().toList().take(n.toInt()).average()
            .toFloat()
        return if (distance.isNaN()) 0f else distance
    }

    private fun valueForBehavior(behavior: ActionBehavior) =
        behavior.totalDamageDone / 5 + (behavior.kills.size * 30) + (behavior.totalDistanceTowardOpponent / 20)
}