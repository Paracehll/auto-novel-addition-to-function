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

object RecordByListSerializer : KSerializer<List<ObjectId>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RecordByList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<ObjectId>) {
        if (encoder is BsonEncoder) {
            val bsonArray = org.bson.BsonArray(value.map { BsonObjectId(it) })
            encoder.encodeBsonValue(bsonArray)
        } else {
            encoder.encodeString(value.joinToString(",") { it.toHexString() })
        }
    }

    override fun deserialize(decoder: Decoder): List<ObjectId> {
        return if (decoder is BsonDecoder) {
            when (val bsonValue = decoder.decodeBsonValue()) {
                is org.bson.BsonArray -> {
                    bsonValue.mapNotNull { element ->
                        when (element) {
                            is BsonObjectId -> element.value
                            is BsonString -> {
                                try { ObjectId(element.value) } catch (e: Exception) { null }
                            }
                            else -> null
                        }
                    }
                }
                is BsonString -> {
                    try {
                        listOf(ObjectId(bsonValue.value))
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                is BsonObjectId -> {
                    listOf(bsonValue.value)
                }
                else -> emptyList()
            }
        } else {
            val str = decoder.decodeString()
            str.split(",").mapNotNull {
                try { ObjectId(it.trim()) } catch (e: Exception) { null }
            }
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
    @Serializable(with = RecordByListSerializer::class) val by: List<@Contextual ObjectId> = emptyList(),
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
