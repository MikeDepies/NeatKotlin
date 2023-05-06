import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.Species
import neat.novelty.NoveltyArchive
import server.ActionBehaviorInt
import server.squared
import kotlin.streams.toList
private val logger = KotlinLogging.logger {  }
@Serializable
data class Behavior<T>(val behavior : T, val species : Int)
class KNNNoveltyArchiveWeighted(
    var k: Int,
    val multiplier: Int,
    var noveltyThreshold: Float,
    val behaviorDistanceMeasureFunction: (ActionBehaviorInt, ActionBehaviorInt) -> Float
) : NoveltyArchive<Behavior<ActionBehaviorInt>> {
    override val behaviors = mutableListOf<Behavior<ActionBehaviorInt>>()
//    var maxDiscovery: MarioDiscovery = MarioDiscovery("", 0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, )
    override val size: Int
        get() = behaviors.size

    override fun addBehavior(behavior: Behavior<ActionBehaviorInt>): Float {
        val distance = measure(behavior)
        if (distance > noveltyThreshold || size == 0) behaviors += behavior
        return distance
    }

    override fun measure(b: Behavior<ActionBehaviorInt>): Float {
        val behavior = b.behavior
        val newK = k + (behavior.kills.size.squared() * multiplier) + ( behavior.allActions.size / 10) + ((behavior.totalFrames.toInt() / 60) / 15).squared()
        val distance = behaviors.parallelStream()
            .map { behaviorDistanceMeasureFunction(behavior, it.behavior) }.sorted().toList()
            .take(newK).average()
            .toFloat()
        logger.info { "K: $newK" }
        return if (distance.isNaN()) 0f else distance
    }
}
