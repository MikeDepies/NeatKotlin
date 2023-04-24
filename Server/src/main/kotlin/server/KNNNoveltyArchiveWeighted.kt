import mu.KotlinLogging
import neat.novelty.NoveltyArchive
import server.ActionBehaviorInt
import kotlin.streams.toList
private val logger = KotlinLogging.logger {  }
class KNNNoveltyArchiveWeighted(
    var k: Int,
    val multiplier: Int,
    var noveltyThreshold: Float,
    val behaviorDistanceMeasureFunction: (ActionBehaviorInt, ActionBehaviorInt) -> Float
) : NoveltyArchive<ActionBehaviorInt> {
    override val behaviors = mutableListOf<ActionBehaviorInt>()
//    var maxDiscovery: MarioDiscovery = MarioDiscovery("", 0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, )
    override val size: Int
        get() = behaviors.size

    override fun addBehavior(behavior: ActionBehaviorInt): Float {
        val distance = measure(behavior)
        if (distance > noveltyThreshold || size == 0) behaviors += behavior
        return distance
    }

    override fun measure(behavior: ActionBehaviorInt): Float {
        val newK = k + (behavior.kills.size * multiplier) + ( behavior.allActions.size / 2)
        val distance = behaviors.parallelStream()
            .map { behaviorDistanceMeasureFunction(behavior, it) }.sorted().toList()
            .take(newK).average()
            .toFloat()
        logger.info { "K: $newK" }
        return if (distance.isNaN()) 0f else distance
    }
}
