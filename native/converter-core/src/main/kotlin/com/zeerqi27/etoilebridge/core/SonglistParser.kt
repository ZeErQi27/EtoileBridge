package com.zeerqi27.etoilebridge.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal val CoreJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = true
}

class SonglistParser {
    fun parse(file: File): Songlist {
        val utf8 = runCatching { parse(file.readTextStrict(Charsets.UTF_8)) }
        if (utf8.isSuccess) return utf8.getOrThrow()
        val gbk = runCatching { parse(file.readTextStrict(Charset.forName("GBK"))) }
        return gbk.getOrElse { throw SonglistParseException(file.name, utf8.exceptionOrNull() ?: it) }
    }

    fun parse(content: String): Songlist {
        val normalized = normalizeJson(content)
        val element = runCatching { CoreJson.parseToJsonElement(normalized) }
            .getOrElse { CoreJson.parseToJsonElement("[$normalized]") }
        return when {
            element is JsonObject && "songs" !in element.jsonObject ->
                Songlist(songs = listOf(CoreJson.decodeFromJsonElement(SonglistSong.serializer(), element)))
            element is kotlinx.serialization.json.JsonArray ->
                Songlist(songs = element.map { CoreJson.decodeFromJsonElement(SonglistSong.serializer(), it) })
            else -> CoreJson.decodeFromJsonElement(Songlist.serializer(), element)
        }
    }

    private fun normalizeJson(content: String): String =
        content
            .trim()
            .removePrefix("\uFEFF")
            .removeSuffix(",")
            .replace(Regex(""",\s*([}\]])"""), "$1")
}

private fun File.readTextStrict(charset: Charset): String {
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return inputStream().use { stream ->
        InputStreamReader(stream, decoder).use { it.readText() }
    }
}

class SonglistParseException(fileName: String, cause: Throwable) :
    RuntimeException("Unable to parse songlist/slst: $fileName", cause)

@Serializable
data class Songlist(
    val songs: List<SonglistSong> = emptyList(),
)

@Serializable
data class SonglistSong(
    val idx: Int? = null,
    val id: String? = null,
    val title: String? = null,
    @SerialName("title_localized") val titleLocalized: Map<String, String> = emptyMap(),
    val artist: String? = null,
    val composer: String? = null,
    @SerialName("search_title") val searchTitle: Map<String, List<String>> = emptyMap(),
    @SerialName("search_artist") val searchArtist: Map<String, List<String>> = emptyMap(),
    @SerialName("bpm") val bpmText: String? = null,
    @SerialName("bpm_base") val bpmBase: Float? = null,
    val set: String? = null,
    val audioPreview: Long? = null,
    val audioPreviewEnd: Long? = null,
    val side: Int? = null,
    val bg: String? = null,
    @SerialName("bg_inverse") val bgInverse: String? = null,
    @SerialName("bg_daynight") val bgDaynight: String? = null,
    val date: Long? = null,
    val version: String? = null,
    @SerialName("world_unlock") val worldUnlock: Boolean? = null,
    @SerialName("remote_dl") val remoteDl: Boolean? = null,
    @SerialName("source_localized") val sourceLocalized: Map<String, String> = emptyMap(),
    @SerialName("source_copyright") val sourceCopyright: String? = null,
    val category: String? = null,
    @SerialName("additional_files") val additionalFiles: List<String> = emptyList(),
    val purchase: String? = null,
    val limitedSaleEndTime: Long? = null,
    val deleted: Boolean? = null,
    @SerialName("songlist_hidden") val songlistHidden: Boolean? = null,
    @SerialName("no_stream") val noStream: Boolean? = null,
    @SerialName("jacket_localized") val jacketLocalized: Boolean? = null,
    @SerialName("byd_local_unlock") val bydLocalUnlock: Boolean? = null,
    val difficulties: List<SonglistDifficulty> = emptyList(),
)

@Serializable
data class SonglistDifficulty(
    val ratingClass: Int? = null,
    val chartDesigner: String? = null,
    val jacketDesigner: String? = null,
    val rating: Int? = null,
    val chartConstant: Float? = null,
    val ratingReal: Float? = null,
    @SerialName("chart_constant") val chartConstantSnake: Float? = null,
    @SerialName("rating_real") val ratingRealSnake: Float? = null,
    var ratingPlus: Boolean? = null,
    var jacketOverride: Boolean? = null,
    var audioOverride: Boolean? = null,
    var bg: String? = null,
    @SerialName("bg_inverse") var bgInverse: String? = null,
    @SerialName("title_localized") var titleLocalized: Map<String, String> = emptyMap(),
    var artist: String? = null,
    @SerialName("bpm") var bpmText: String? = null,
    @SerialName("bpm_base") var bpmBase: Float? = null,
    @SerialName("hidden_until") var hiddenUntil: String? = null,
    @SerialName("hidden_until_unlocked") var hiddenUntilUnlocked: Boolean? = null,
    var date: Long? = null,
    var version: String? = null,
    @SerialName("world_unlock") var worldUnlock: Boolean? = null,
    @SerialName("jacket_night") var jacketNight: Boolean? = null,
    var legacy11: Boolean? = null,
    var plusFingers: Boolean? = null,
)
