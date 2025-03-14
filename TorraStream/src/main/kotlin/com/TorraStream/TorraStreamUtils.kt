package com.TorraStream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject

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
    return SubtitleHelper.fromTwoLettersToLanguage(language ?: return null)
        ?: SubtitleHelper.fromTwoLettersToLanguage(language.substringBefore("-"))
}


data class TorrentioResponse(val streams: List<TorrentioStream>)

data class TorrentioStream(
    val name: String?,
    val title: String?,
    val infoHash: String?,
    val fileIdx: Int?,
)


data class DebianRoot(
    val streams: List<Stream>,
    val cacheMaxAge: Long,
    val staleRevalidate: Long,
    val staleError: Long,
)

data class Stream(
    val name: String,
    val title: String,
    val url: String,
    val behaviorHints: BehaviorHints,
)

data class BehaviorHints(
    val bingeGroup: String,
    val filename: String?,
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

fun generateMagnetLinkFromSource(trackersList: List<String>, hash: String?): String {
    // Fetch the content of the file from the provided URL

    // Build the magnet link
    return buildString {
        append("magnet:?xt=urn:btih:$hash")
        for (index in 0 until trackersList.size - 1) {
            if (trackersList[index].isNotBlank()) {
                append("&tr=").append(trackersList[index].trim())
            }
        }
    }
}


fun getAnidbEid(jsonString: String, episodeNumber: Int?): Int? {
    if (episodeNumber == null) return null

    val jsonObject = JSONObject(jsonString)
    val episodes = jsonObject.optJSONObject("episodes") ?: return null

    return episodes.optJSONObject(episodeNumber.toString())?.optInt("anidbEid", -1)?.takeIf { it != -1 }
}

fun getImdbId(jsonString: String): String? {
    val jsonObject = JSONObject(jsonString)
    return jsonObject.optJSONObject("mappings")?.optString("imdb_id", null)
}

