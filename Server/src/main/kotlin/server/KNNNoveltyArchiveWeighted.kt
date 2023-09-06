import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.Species
import neat.novelty.NoveltyArchive
import server.ActionBehaviorInt
import server.squared
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.streams.toList

private val logger = KotlinLogging.logger { }
val behaviorMeasureThreadPool = ForkJoinPool(8)

@Serializable
data class Behavior<T>(val behavior: T, val species: Int)
class KNNNoveltyArchiveWeighted(
    var k: Int,
    val multiplier: Int,
    var noveltyThreshold: Float,
    val behaviorDistanceMeasureFunction: (ActionBehaviorInt, ActionBehaviorInt) -> Float
) : NoveltyArchive<Behavior<ActionBehaviorInt>> {
    override val behaviors = mutableListOf<Behavior<ActionBehaviorInt>>()


    override val size: Int
        get() = behaviors.size

    override fun addBehavior(behavior: Behavior<ActionBehaviorInt>): Float {
        val distance = measure(behavior)
//        logger.info { "DISTANCE: $distance" }
        if (distance > noveltyThreshold || size == 0) behaviors += behavior
        return distance
    }

    override fun measure(b: Behavior<ActionBehaviorInt>): Float {
        val behavior = b.behavior
        val damageK = if (b.behavior.totalDamageDone > 0) 1 else 0
        val newK =
            k + (behavior.kills.size.squared() * multiplier)// + (behavior.totalDamageDone.toInt() / 10) + (behavior.recovery.size * 3)
        val task  = behaviorMeasureThreadPool.submit(measure(behavior, newK))
        val distance = task.get()
        logger.info { "K: $newK => $distance" }
        return if (distance.isNaN()) 0f else distance
    }

    fun measure(behavior: ActionBehaviorInt, newK : Int): () -> Float {
        return {
            behaviors.parallelStream().map { behaviorDistanceMeasureFunction(behavior, it.behavior) }.sorted().toList().take(newK).average().toFloat()
        }
    }
}
