import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class Message {
    abstract val topic: String
}

@Serializable
data class SimpleMessage(
    override val topic: String,
    val data: JsonElement
) : Message()

/**
 * Simple message is the first step in the data pipeline when reading messages from the clients.
 * All messages must have a playerRef, subject and Data field. Where the data field will be resolved at a later stage
 * in the message handling pipeline.
 */
@Serializable
@SerialName("playerMessage")
data class JsonPlayerMessage(
    val playerRef: String,
    override val topic: String,
    val data: JsonElement
) : Message()

/**
 * The Typed message is a SimpleMessage that has had its data adapted
 * and resolved the playerRef string into an actual PlayerRef class.
 *
 * This is the class most message endpoint handlers will be working with.
 */
@Serializable
data class TypedPlayerMessage<T>(
    val playerRef: UserRef,
    override val topic: String,
    val data: T
) : Message()

@Serializable
data class TypedPlayerGroupMessage<T>(
    val players: List<UserRef>,
    override val topic: String,
    val data: T
) : Message()

@Serializable
data class BroadcastMessage<T>(override val topic: String, @Serializable val data: T) : Message()
