import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = UserRefSerializer::class)
data class UserRef(val ref: String)

object UserRefSerializer : KSerializer<UserRef> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("PlayerRef", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UserRef {
        return UserRef(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UserRef) {
        encoder.encodeString(value.ref)
    }
}