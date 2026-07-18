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

object RecordBySingleSerializer : KSerializer<ObjectId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RecordBySingle", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ObjectId) {
        if (encoder is BsonEncoder) {
            encoder.encodeBsonValue(BsonObjectId(value))
        } else {
            encoder.encodeString(value.toHexString())
        }
    }

    override fun deserialize(decoder: Decoder): ObjectId {
        val fallback = ObjectId("000000000000000000000000")
        return if (decoder is BsonDecoder) {
            when (val bsonValue = decoder.decodeBsonValue()) {
                is BsonObjectId -> bsonValue.value
                is BsonString -> {
                    try { ObjectId(bsonValue.value) } catch (e: Exception) { fallback }
                }
                is org.bson.BsonArray -> {
                    val first = bsonValue.firstOrNull()
                    when (first) {
                        is BsonObjectId -> first.value
                        is BsonString -> {
                            try { ObjectId(first.value) } catch (e: Exception) { fallback }
                        }
                        else -> fallback
                    }
                }
                else -> fallback
            }
        } else {
            val str = decoder.decodeString()
            try { ObjectId(str.trim()) } catch (e: Exception) { fallback }
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
    @Serializable(with = RecordBySingleSerializer::class) val by: @Contextual ObjectId = ObjectId("000000000000000000000000"),
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
