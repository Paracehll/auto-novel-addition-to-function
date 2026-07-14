package infra.common

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class GlobalGlossaryDiffItem(
    val old: String?,
    val new: String?,
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
    val content: Map<String, String>,
    val used: List<String> = emptyList(),
    @Contextual val update: Instant,
    val tag: List<String> = emptyList(),
    val record: List<GlobalGlossaryRecord> = emptyList(),
)
