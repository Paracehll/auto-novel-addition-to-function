package infra.common

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class GlobalGlossaryDiffItem(
    val type: String, // "add", "delete", "modify"
    val old: String? = null,
    val new: String? = null,
)

@Serializable
data class GlobalGlossaryRecord(
    @Contextual val date: Instant,
    val diff: Map<String, GlobalGlossaryDiffItem>,
)

@Serializable
data class GlobalGlossary(
    @Contextual @SerialName("_id") val id: ObjectId,
    val uid: String,
    val name: String,
    val content: Map<String, String> = emptyMap(),
    val termsCount: Int = 0,
    val used: List<@Contextual ObjectId> = emptyList(),
    @Contextual val update: Instant,
    val tag: List<String> = emptyList(),
    val record: List<GlobalGlossaryRecord> = emptyList(),
)
