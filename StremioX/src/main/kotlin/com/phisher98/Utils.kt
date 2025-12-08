package com.phisher98

import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.getQualityFromName

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.sequences.forEach

fun String.fixSourceUrl(): String {
    return this.replace("/manifest.json", "").replace("stremio://", "https://")
}

fun fixSourceName(name: String?, title: String?): String {
    return when {
        name?.contains("[RD+]", true) == true -> "[RD+] $title"
        name?.contains("[RD download]", true) == true -> "[RD download] $title"
        !name.isNullOrEmpty() && !title.isNullOrEmpty() -> "$name $title"
        else -> title ?: name ?: ""
    }
}

fun getQuality(qualities: List<String?>): Int {
    fun String.getQuality(): String? {
        return Regex("(\\d{3,4}[pP])").find(this)?.groupValues?.getOrNull(1)
    }
    val quality = qualities.firstNotNullOfOrNull { it?.getQuality() }
    return getQualityFromName(quality)
}

fun getEpisodeSlug(
    season: Int? = null,
    episode: Int? = null,
): Pair<String, String> {
    return if (season == null && episode == null) {
        "" to ""
    } else {
        (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
    }
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }

}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
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


suspend fun generateMagnetLink(
    trackerUrls: List<String>,
    hash: String?,
): String {
    require(hash?.isNotBlank() == true)

    val trackers = mutableSetOf<String>()

    trackerUrls.forEach { url ->
        try {
            val response = app.get(url)
            response.text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { trackers.add(it) }
        } catch (_: Exception) {
            // ignore bad sources
        }
    }

    return buildString {
        append("magnet:?xt=urn:btih:").append(hash)

        if (hash.isNotBlank()) {
            append("&dn=")
            append(URLEncoder.encode(hash, StandardCharsets.UTF_8.name()))
        }

        trackers
            .take(10) // practical limit
            .forEach { tracker ->
                append("&tr=")
                append(URLEncoder.encode(tracker, StandardCharsets.UTF_8.name()))
            }
    }
}
