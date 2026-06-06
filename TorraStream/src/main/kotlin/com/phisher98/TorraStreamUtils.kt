package com.phisher98

import android.content.SharedPreferences
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
        ?: Qualities.Unknown.value
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
    val bingeGroup: String?,
    val filename: String?,
    val videoSize: Long?,
    val videoHash: String?,
)


//Subtitles

data class Subtitles(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)

data class Subtitle(
    val id: String,
    val url: String,
    @param:JsonProperty("SubEncoding")
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
data class MetaMappings(
    @param:JsonProperty("themoviedb_id") val themoviedbId: String? = null,
    @param:JsonProperty("thetvdb_id") val thetvdbId: Int? = null,
    @param:JsonProperty("imdb_id") val imdbId: String? = null,
    @param:JsonProperty("mal_id") val malId: Int? = null,
    @param:JsonProperty("anilist_id") val anilistId: Int? = null,
    @param:JsonProperty("kitsu_id") val kitsuid: String? = null,
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @param:JsonProperty("coverType") val coverType: String?,
    @param:JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisode(
    @param:JsonProperty("episode") val episode: String?,
    @param:JsonProperty("airdate") val airdate: String?,
    @param:JsonProperty("airDateUtc") val airDateUtc: String?,
    @param:JsonProperty("length") val length: Int?,
    @param:JsonProperty("runtime") val runtime: Int?,
    @param:JsonProperty("image") val image: String?,
    @param:JsonProperty("title") val title: Map<String, String>?,
    @param:JsonProperty("overview") val overview: String?,
    @param:JsonProperty("rating") val rating: String?,
    @param:JsonProperty("finaleType") val finaleType: String?
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @param:JsonProperty("titles") val titles: Map<String, String>? = null,
    @param:JsonProperty("images") val images: List<ImageData>? = null,
    @param:JsonProperty("episodes") val episodes: Map<String, MetaEpisode>? = null,
    @param:JsonProperty("mappings") val mappings: MetaMappings? = null
)

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

suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {

    if (tmdbId == null) return null

    val url = if (type == TvType.Movie)
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    else
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()

    fun path(o: JSONObject) = o.optString("file_path")
    fun isSvg(o: JSONObject) = path(o).endsWith(".svg", true)
    fun urlOf(o: JSONObject) = "https://image.tmdb.org/t/p/w500${path(o)}"

    // Language match
    var svgFallback: JSONObject? = null

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        val p = path(logo)
        if (p.isBlank()) continue

        val l = logo.optString("iso_639_1").trim().lowercase()
        if (l == lang) {
            if (!isSvg(logo)) return urlOf(logo)
            if (svgFallback == null) svgFallback = logo
        }
    }
    svgFallback?.let { return urlOf(it) }

    // Highest voted fallback
    var best: JSONObject? = null
    var bestSvg: JSONObject? = null

    fun voted(o: JSONObject) = o.optDouble("vote_average", 0.0) > 0 && o.optInt("vote_count", 0) > 0

    fun better(a: JSONObject?, b: JSONObject): Boolean {
        if (a == null) return true
        val aAvg = a.optDouble("vote_average", 0.0)
        val aCnt = a.optInt("vote_count", 0)
        val bAvg = b.optDouble("vote_average", 0.0)
        val bCnt = b.optInt("vote_count", 0)
        return bAvg > aAvg || (bAvg == aAvg && bCnt > aCnt)
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (!voted(logo)) continue

        if (isSvg(logo)) {
            if (better(bestSvg, logo)) bestSvg = logo
        } else {
            if (better(best, logo)) best = logo
        }
    }

    best?.let { return urlOf(it) }
    bestSvg?.let { return urlOf(it) }

    // No language match & no voted logos
    return null
}

data class TorrentsDBResponse(
    val streams: List<TorrentsDBStream>?
)

data class TorrentsDBStream(
    val name: String?,
    val title: String?,
    val infoHash: String,
    val fileIdx: Int?,
    val behaviorHints: TorrentsDBBehaviorHints?,
    val sources: List<String>?
)

data class TorrentsDBBehaviorHints(
    val bingeGroup: String?,
    val filename: String?
)

fun filteredCallback(
    sharedPref: SharedPreferences,
    callback: (ExtractorLink) -> Unit
): (ExtractorLink) -> Unit {

    val excludedQualities = sharedPref.getString("qualityfilter", "")
        ?.lowercase()
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    val maxSize = sharedPref.getString("sizefilter", "")?.toDoubleOrNull()
    val limit = sharedPref.getString("limit", "")?.toIntOrNull() ?: 0
    var resultCount = 0

    return fun(link: ExtractorLink) {

        if (limit in 1..resultCount) return

        val detectedQuality = when (link.quality) {
            in 2000..3000 -> "4k"
            in 1080..1999 -> "1080p"
            in 720..1079 -> "720p"
            in 480..719 -> "480p"
            else -> "other"
        }

        if (detectedQuality in excludedQualities) return

        val sizeMatch = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|MB)""", RegexOption.IGNORE_CASE)
            .find(link.name)

        val sizeValue = sizeMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val sizeUnit = sizeMatch?.groupValues?.get(2)

        val sizeGB = when (sizeUnit?.uppercase()) {
            "GB" -> sizeValue
            "MB" -> sizeValue?.div(1024)
            else -> null
        }
        if (maxSize != null && sizeGB != null && sizeGB > maxSize) return
        callback(link)
        resultCount++
    }
}

suspend fun generateMagnetLink(
    trackerUrls: List<String>,
    hash: String?,
): String {
    require(hash?.isNotBlank() == true)

    val trackers = mutableSetOf<String>()

    trackerUrls.amap { url ->
        runCatching {
            app.get(url).text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }.getOrElse { emptyList() }
    }.flatten().toMutableSet()

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