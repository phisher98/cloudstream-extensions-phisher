package com.phisher98

import android.content.SharedPreferences
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class StreamPlayStremioAddon(
    val id: Long,
    val name: String,
    val url: String,
    val type: StreamPlayStremioAddonType
)

enum class StreamPlayStremioAddonType {
    SUBTITLE,
    TORRENT,
    HTTPS,
    DEBRID
}

object StreamPlayStremioAddonSettings {
    const val PREF_KEY_LINKS = "streamplay_stremio_addon_saved_links"

    fun getStremioAddons(sharedPref: SharedPreferences?): List<StreamPlayStremioAddon> {
        val json = sharedPref?.getString(PREF_KEY_LINKS, null) ?: return emptyList()
        val list = mutableListOf<StreamPlayStremioAddon>()

        return try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val link = obj.optString("link", "").trim()
                if (link.isEmpty()) continue

                list.add(
                    StreamPlayStremioAddon(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        name = obj.optString("name", link).ifBlank { link },
                        url = link.fixSourceUrl().trimEnd('/'),
                        type = StreamPlayStremioAddonType.values().firstOrNull {
                            it.name.equals(obj.optString("type", "HTTPS"), ignoreCase = true)
                        } ?: StreamPlayStremioAddonType.HTTPS
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun stremioAddonKey(name: String): String {
        val key = name
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "addon" }
        return "stremio_$key"
    }

    fun getDynamicStremioMap(
        sharedPref: SharedPreferences?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Map<String, suspend () -> Unit> {
        return getStremioAddons(sharedPref).associate { addon ->
            val key = stremioAddonKey(addon.name)
            key to suspend {
                when (addon.type) {
                    StreamPlayStremioAddonType.SUBTITLE -> invokeStremioSubtitlesGlobal(addon.name, addon.url, imdbId, season, episode, subtitleCallback)
                    StreamPlayStremioAddonType.TORRENT -> invokeStremioTorrentsGlobal(addon.name, addon.url, imdbId, season, episode, callback)
                    StreamPlayStremioAddonType.HTTPS, StreamPlayStremioAddonType.DEBRID -> invokeStreamioStreamsGlobal(addon.name, addon.url, imdbId, season, episode, subtitleCallback, callback)
                }
            }
        }
    }
}

suspend fun invokeStremioTorrentsGlobal(
    sourceName: String,
    api: String,
    imdbId: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit,
) {
    if (imdbId.isNullOrBlank()) return
    val url = if (season == null) "$api/stream/movie/$imdbId.json" else "$api/stream/series/$imdbId:$season:$episode.json"
    // OPTIMIZED: Reduced timeout from 50s to 15s with adaptive behavior
    val timeout = StreamPlayConcurrency.getAdaptiveTimeout("stremio_$sourceName", 15000L)
    val res = app.get(url, timeout = timeout).parsedSafe<StreamPlayStremioResponse>()

    res?.streams?.forEach { stream ->
        val title = stream.description ?: stream.title ?: stream.name ?: ""
        val magnet = buildMagnetString(stream).takeIf { it.isNotBlank() } ?: return@forEach

        callback.invoke(
            newExtractorLink(
                "$sourceName Magnet",
                "[$sourceName] Magnet $title",
                magnet,
                ExtractorLinkType.MAGNET,
            ) {
                this.quality = getIndexQuality(title)
            }
        )
    }
}

suspend fun invokeStreamioStreamsGlobal(
    sourceName: String,
    api: String,
    imdbId: String? = null,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    if (imdbId.isNullOrBlank()) return
    val url = if (season == null) "$api/stream/movie/$imdbId.json" else "$api/stream/series/$imdbId:$season:$episode.json"
    // OPTIMIZED: Reduced timeout from 50s to 15s with adaptive behavior
    val timeout = StreamPlayConcurrency.getAdaptiveTimeout("stremio_$sourceName", 15000L)
    val res = app.get(url, timeout = timeout).parsedSafe<StreamPlayStremioResponse>()

    res?.streams?.forEach { s ->
        val title = s.description ?: s.title ?: s.name ?: ""
        val streamUrl = s.url

        if (!streamUrl.isNullOrBlank()) {
            val type = when {
                streamUrl.startsWith("magnet:", ignoreCase = true) -> ExtractorLinkType.MAGNET
                streamUrl.endsWith(".torrent", ignoreCase = true) -> ExtractorLinkType.TORRENT
                streamUrl.endsWith(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                streamUrl.contains(".m3u8", ignoreCase = true) || streamUrl.contains("hls", ignoreCase = true) -> ExtractorLinkType.M3U8
                else -> INFER_TYPE
            }
            val proxyReq = s.behaviorHints?.proxyHeaders?.request
            val stdHeaders = s.behaviorHints?.headers

            callback.invoke(
                newExtractorLink(
                    sourceName,
                    "[$sourceName] $title",
                    streamUrl,
                    type
                ) {
                    this.quality = getIndexQuality(title)
                    this.headers = mapOf(
                        "User-Agent" to (proxyReq.getHeader("User-Agent") ?: stdHeaders.getHeader("User-Agent") ?: USER_AGENT),
                        "Referer" to (proxyReq.getHeader("Referer") ?: stdHeaders.getHeader("Referer") ?: ""),
                        "Origin" to (proxyReq.getHeader("Origin") ?: stdHeaders.getHeader("Origin") ?: "")
                    ).filterValues { it.isNotBlank() }
                }
            )
        }

        s.externalUrl?.takeIf { it.isNotBlank() }?.let { loadExtractor(it, sourceName, subtitleCallback, callback) }
        s.ytId?.takeIf { it.isNotBlank() }?.let { loadExtractor("https://www.youtube.com/watch?v=$it", subtitleCallback, callback) }
        s.subtitles.forEach { emitStremioSubtitle(it, subtitleCallback) }
    }
}

suspend fun invokeStremioSubtitlesGlobal(
    sourceName: String,
    api: String,
    imdbId: String? = null,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
) {
    if (imdbId.isNullOrBlank()) return
    val url = if (season != null) "$api/subtitles/series/$imdbId:$season:$episode.json" else "$api/subtitles/movie/$imdbId.json"
    // OPTIMIZED: Reduced timeout from 50s to 15s for subtitles
    val timeout = StreamPlayConcurrency.getAdaptiveTimeout("stremio_sub_$sourceName", 15000L)
    val subtitleResponse = app.get(url, timeout = timeout).parsedSafe<StreamPlayStremioSubtitleResponse>()
    subtitleResponse?.subtitles?.forEach { emitStremioSubtitle(it, subtitleCallback) }
}

private suspend fun emitStremioSubtitle(subtitle: StreamPlayStremioSubtitle, subtitleCallback: (SubtitleFile) -> Unit) {
    val lang = subtitle.lang ?: subtitle.langCode ?: return
    val fileUrl = subtitle.url ?: return
    subtitleCallback.invoke(newSubtitleFile(getLanguage(lang).takeIf { it != "UnKnown" } ?: lang, fileUrl))
}

private fun buildMagnetString(stream: StreamPlayStremioStream): String {
    val url = stream.url.orEmpty()
    if (url.startsWith("magnet:", ignoreCase = true)) return url
    val infoHash = stream.infoHash?.takeIf { it.isNotBlank() } ?: return ""
    val title = stream.description ?: stream.title ?: stream.name ?: infoHash

    return buildString {
        append("magnet:?xt=urn:btih:").append(infoHash)
        append("&dn=").append(URLEncoder.encode(title, StandardCharsets.UTF_8.name()))
        stream.fileIdx?.let { append("&so=").append(it) }
        stream.sources.filter { it.startsWith("tracker:", ignoreCase = true) }
            .map { it.removePrefix("tracker:") }
            .filter { it.isNotBlank() }
            .forEach { append("&tr=").append(URLEncoder.encode(it, StandardCharsets.UTF_8.name())) }
    }
}

private fun Map<String, String>?.getHeader(name: String): String? {
    return this?.entries?.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

data class StreamPlayStremioResponse(@SerializedName("streams") val streams: List<StreamPlayStremioStream> = emptyList())
data class StreamPlayStremioBehaviorHints(@SerializedName("proxyHeaders") val proxyHeaders: StreamPlayStremioProxyHeaders? = null, @SerializedName("headers") val headers: Map<String, String>? = null)
data class StreamPlayStremioProxyHeaders(@SerializedName("request") val request: Map<String, String>? = null)
data class StreamPlayStremioSubtitleResponse(@SerializedName("subtitles") val subtitles: List<StreamPlayStremioSubtitle> = emptyList())
data class StreamPlayStremioSubtitle(@SerializedName("url") val url: String? = null, @SerializedName("lang") val lang: String? = null, @SerializedName("lang_code") val langCode: String? = null)
data class StreamPlayStremioStream(
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("externalUrl") val externalUrl: String? = null,
    @SerializedName("ytId") val ytId: String? = null,
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int? = null,
    @SerializedName("sources") val sources: List<String> = emptyList(),
    @SerializedName("behaviorHints") val behaviorHints: StreamPlayStremioBehaviorHints? = null,
    @SerializedName("subtitles") val subtitles: List<StreamPlayStremioSubtitle> = emptyList()
)
