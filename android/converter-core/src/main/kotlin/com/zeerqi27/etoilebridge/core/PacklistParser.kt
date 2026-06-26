package com.zeerqi27.etoilebridge.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

class PacklistParser {
    fun parse(file: File): Packlist = parse(file.readText(Charsets.UTF_8))

    fun parse(content: String): Packlist {
        val normalized = content
            .trim()
            .removePrefix("\uFEFF")
            .removeSuffix(",")
            .replace(Regex(""",\s*([}\]])"""), "$1")
        val element = runCatching { CoreJson.parseToJsonElement(normalized) }
            .getOrElse { CoreJson.parseToJsonElement("[$normalized]") }
        return when {
            element is JsonObject && "packs" !in element.jsonObject ->
                Packlist(packs = listOf(CoreJson.decodeFromJsonElement(PacklistPack.serializer(), element)))
            element is kotlinx.serialization.json.JsonArray ->
                Packlist(packs = element.map { CoreJson.decodeFromJsonElement(PacklistPack.serializer(), it) })
            else -> CoreJson.decodeFromJsonElement(Packlist.serializer(), element)
        }
    }
}

@Serializable
data class Packlist(
    val packs: List<PacklistPack> = emptyList(),
)

@Serializable
data class PacklistPack(
    val id: String? = null,
    val section: String? = null,
    @SerialName("plus_character")
    @Serializable(with = FlexibleIntSerializer::class)
    val plusCharacter: Int? = null,
    @SerialName("custom_banner") val customBanner: Boolean? = null,
    @SerialName("cutout_pack_image") val cutoutPackImage: Boolean? = null,
    @SerialName("name_localized") val nameLocalized: Map<String, String> = emptyMap(),
    @SerialName("description_localized") val descriptionLocalized: Map<String, String> = emptyMap(),
    @SerialName("pack_parent") val packParent: String? = null,
    @SerialName("is_extend_pack") val isExtendPack: Boolean? = null,
    @SerialName("is_active_extend_pack") val isActiveExtendPack: Boolean? = null,
    @SerialName("small_pack_image") val smallPackImage: Boolean? = null,
    val limitedSaleEndTime: Long? = null,
)

private object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val input = decoder.decodeSerializableValue(JsonPrimitive.serializer())
        return input.intOrNull ?: input.booleanOrNull?.let { if (it) 1 else -1 }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}
