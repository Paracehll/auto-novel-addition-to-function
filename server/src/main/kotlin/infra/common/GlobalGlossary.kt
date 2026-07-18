package infra.common

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.kotlinx.BsonDecoder
import org.bson.codecs.kotlinx.BsonEncoder
import org.bson.types.ObjectId

object RecordBySerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RecordBy", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        if (encoder is BsonEncoder) {
            if (value == "admin") {
                encoder.encodeBsonValue(BsonString("admin"))
            } else {
                val objectId = try {
                    ObjectId(value)
                } catch (e: Exception) {
                    null
                }
                if (objectId != null) {
                    encoder.encodeBsonValue(BsonObjectId(objectId))
                } else {
                    encoder.encodeBsonValue(BsonString(value))
                }
            }
        } else {
            encoder.encodeString(value)
        }
    }

    override fun deserialize(decoder: Decoder): String {
        return if (decoder is BsonDecoder) {
            when (val bsonValue = decoder.decodeBsonValue()) {
                is BsonString -> bsonValue.value
                is BsonObjectId -> bsonValue.value.toHexString()
                else -> bsonValue.toString()
            }
        } else {
            decoder.decodeString()
        }
    }
}

@Serializable
data class GlobalGlossaryDiffItem(
    val old: String?,
    val new: String?,
)

@Serializable
data class GlobalGlossaryRecord(
    @Contextual val date: Instant,
    val diff: Map<String, GlobalGlossaryDiffItem>,
    @Serializable(with = RecordBySerializer::class) val by: String = "admin",
)

@Serializable
data class GlobalGlossary(
    @Contextual @SerialName("_id") val id: ObjectId,
    val name: String,
    val content: Map<String, String> = emptyMap(),
    val termsCount: Int = 0,
    val used: List<@Contextual ObjectId> = emptyList(),
    @Contextual val update: Instant,
    val tag: List<String> = emptyList(),
    val record: List<GlobalGlossaryRecord> = emptyList(),
    val version: Long = 1,
)

fun GlobalGlossary.contentAtVersion(targetVersion: Long): Map<String, String> {
    val currentContent = this.content.toMutableMap()
    val records = this.record
    val targetIndex = (targetVersion - 1).toInt()
    if (targetIndex < 0 || targetIndex >= records.size) {
        return currentContent
    }
    for (j in records.size - 1 downTo targetIndex + 1) {
        val rec = records[j]
        for ((key, item) in rec.diff) {
            if (item.old == null || item.old.isEmpty()) {
                currentContent.remove(key)
            } else {
                currentContent[key] = item.old
            }
        }
    }
    return currentContent
}
