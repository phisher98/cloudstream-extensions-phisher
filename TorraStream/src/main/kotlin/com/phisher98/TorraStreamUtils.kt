package com.phisher98

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
        ?: Qualities.Unknown.value
}


fun getQuality(str: String): Int {
    return when (str) {
        "360p" -> Qualities.P240.value
        "480p" -> Qualities.P360.value
        "HD" -> Qualities.P720.value
        "HEVC" -> Qualities.P1440.value
        "UHD" -> Qualities.P2160.value
        else -> getQualityFromName(str)
    }
}

fun getLanguage(language: String?): String? {
    return SubtitleHelper.fromTagToEnglishLanguageName(language ?: return null)
        ?: SubtitleHelper.fromTagToEnglishLanguageName(language.substringBefore("-"))
}


data class TorrentioResponse(
    @SerializedName("streams") val streams: List<TorrentioStream> = emptyList()
)

data class TorrentioStream(
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int? = null
)

data class DebianRoot(
    @SerializedName("streams") val streams: List<Stream> = emptyList(),
    @SerializedName("cacheMaxAge") val cacheMaxAge: Long = 0,
    @SerializedName("staleRevalidate") val staleRevalidate: Long = 0,
    @SerializedName("staleError") val staleError: Long = 0
)

data class Stream(
    @SerializedName("name") val name: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("behaviorHints") val behaviorHints: BehaviorHints = BehaviorHints()
)

data class BehaviorHints(
    @SerializedName("bingeGroup") val bingeGroup: String? = null,
    @SerializedName("filename") val filename: String? = null
)

//Subtitles

data class Subtitles(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)

data class Subtitle(
    val id: String,
    val url: String,
    @JsonProperty("SubEncoding")
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String,
)


fun getAnidbEid(jsonString: String, episodeNumber: Int?): Int? {
    if (episodeNumber == null) return null

    return try {
        val jsonObject = JSONObject(jsonString)
        val episodes = jsonObject.optJSONObject("episodes") ?: return null

        episodes.optJSONObject(episodeNumber.toString())
            ?.optInt("anidbEid", -1)
            ?.takeIf { it != -1 }
    } catch (e: Exception) {
        e.printStackTrace() // Logs the error but prevents breaking the app
        null
    }
}


fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}


@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisode(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("airDateUtc") val airDateUtc: String?,
    @JsonProperty("length") val length: Int?,
    @JsonProperty("runtime") val runtime: Int?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @JsonProperty("titles") val titles: Map<String, String>? = null,
    @JsonProperty("images") val images: List<ImageData>? = null,
    @JsonProperty("episodes") val episodes: Map<String, MetaEpisode>? = null,
)

fun parseStreamsToMagnetLinks(jsonString: String): List<MagnetStream> {
    val json = JSONObject(jsonString)
    val streams = json.getJSONArray("streams")

    return (0 until streams.length()).mapNotNull { i ->
        val item = streams.getJSONObject(i)

        val infoHash = item.optString("infoHash")
        if (infoHash.isBlank()) return@mapNotNull null

        val originalName = item.optString("name", "Unnamed")
        val sources = item.optJSONArray("sources") ?: return@mapNotNull null

        val behaviorHints = item.optJSONObject("behaviorHints")
        val bingeGroup = behaviorHints?.optString("bingeGroup").orEmpty()
        bingeGroup.split("|").filter { it.isNotBlank() && it != "Unknown" }

        // Extract quality (simple regex to match "720p", "1080p", "WEB-DL", etc.)
        val qualityRegex = Regex("""\b(4K|2160p|1080p|720p|WEB[-\s]?DL|BluRay|HDRip|DVDRip)\b""", RegexOption.IGNORE_CASE)
        val qualityMatch = qualityRegex.find(originalName)?.value ?: "Unknown"

        // Build magnet link
        val encodedName = URLEncoder.encode(originalName, "UTF-8")
        val trackers = (0 until sources.length()).joinToString("&") {
            val tracker = sources.optString(it)
            "tr=${URLEncoder.encode(tracker, "UTF-8")}"
        }

        val magnet = "magnet:?xt=urn:btih:$infoHash&dn=$encodedName&$trackers"

        MagnetStream(
            title = originalName,
            quality = qualityMatch,
            magnet = magnet
        )
    }
}

fun extractResolutionFromDescription(description: String?): String? {
    if (description.isNullOrBlank()) return null
    val regex = Regex("""\b(2160p|1440p|1080p|720p|480p|360p)\b""", RegexOption.IGNORE_CASE)
    return regex.find(description)?.value
}

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()

    // Today
    val today = formatter.format(calendar.time)

    // Next week
    calendar.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calendar.time)

    // Last week's Monday
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    calendar.add(Calendar.WEEK_OF_YEAR, -1)
    val lastWeekStart = formatter.format(calendar.time)

    // Start of current month
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val monthStart = formatter.format(calendar.time)

    return TmdbDate(today, nextWeek, lastWeekStart, monthStart)
}