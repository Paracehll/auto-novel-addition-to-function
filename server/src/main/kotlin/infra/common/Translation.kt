package infra.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TranslatorId {
    @SerialName("baidu")
    Baidu,

    @SerialName("youdao")
    Youdao,

    @SerialName("gpt")
    Gpt,

    @SerialName("sakura")
    Sakura,
}

data class Glossary(
    val id: String,
    val map: Map<String, String>,
)

@Serializable
data class GlossaryUpdatePayload(
    val glossary: Map<String, String>,
    val linkedGlossaries: List<String> = emptyList(),
)