package server.message.endpoints

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

typealias Pixel = List<Int>

val Pixel.r get() = get(0)
val Pixel.g get() = get(1)
val Pixel.b get() = get(2)

@Serializable
data class Info(
    val x: Int,
    val y: Int,
    val world: Int,
    val time: Int,
    val status: String,
    val stage: Int,
    val score: Int,
    val life: Int,
    val flagGet: Boolean,
    val coins: Int
)

@Serializable
data class MarioData(val state: List<Pixel>, val info: Info, val reward: Double, val done: Boolean)

@Serializable
data class MarioOutput(val inputs: List<MarioInput>)

@Serializable(with = MarioInputSerializer::class)
enum class MarioInput {
    UP, LEFT, RIGHT, DOWN, A, B
}

object MarioInputSerializer : KSerializer<MarioInput> {
    override fun deserialize(decoder: Decoder): MarioInput {
        return when (val decodeString = decoder.decodeString()) {
            "up" -> MarioInput.UP
            "down" -> MarioInput.DOWN
            "left" -> MarioInput.LEFT
            "right" -> MarioInput.RIGHT
            "A" -> MarioInput.A
            "B" -> MarioInput.B
            else -> error("No matching input for $decodeString")
        }
    }

    override val descriptor: SerialDescriptor
        get() = serialDescriptor<MarioInput>()

    override fun serialize(encoder: Encoder, value: MarioInput) {
        encoder.encodeString(
            when (value) {
                MarioInput.UP -> "up"
                MarioInput.DOWN -> "down"
                MarioInput.LEFT -> "left"
                MarioInput.RIGHT -> "right"
                MarioInput.A -> "A"
                MarioInput.B -> "B"
            }
        )
    }

}

