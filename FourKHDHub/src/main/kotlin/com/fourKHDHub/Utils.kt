package com.fourKHDHub

import android.util.Base64
import com.fourKHDHub.FourKHDHub.Companion.TMDBAPI
import com.fourKHDHub.FourKHDHub.Companion.TMDBIMAGEBASEURL
import com.fourKHDHub.FourKHDHub.Companion.TMDB_API_KEY
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer

private val REDIRECT_REGEX =
    Regex("s\\('o','([A-Za-z0-9+/=]+)'|ck\\('_wp_http_\\d+','([^']+)'")

suspend fun getRedirectLinks(url: String): String {
    val html = runCatching {
        app.get(url).text
    }.getOrElse {
        Log.e("Error", "Failed to load redirect page: ${it.message}")
        return ""
    }

    // Faster than buildString + forEach
    val combined = StringBuilder(128)
    for (m in REDIRECT_REGEX.findAll(html)) {
        m.groups[1]?.value?.let(combined::append)
            ?: m.groups[2]?.value?.let(combined::append)
    }

    if (combined.isEmpty()) return ""

    return runCatching {
        val decoded = base64Decode(
            pen(
                base64Decode(
                    base64Decode(combined.toString())
                )
            )
        )

        val json = JSONObject(decoded)

        val encodedUrl = base64Decode(json.optString("o"))
        if (encodedUrl.isNotBlank()) return@runCatching encodedUrl.trim()

        val data = encode(json.optString("data"))
        val wp = json.optString("blog_url")
        if (wp.isBlank() || data.isBlank()) return@runCatching ""

        app.get("$wp?re=$data")
            .documentLarge
            .body()
            .text()
            .trim()
    }.getOrElse {
        Log.e("Error", "Error processing redirect: ${it.message}")
        ""
    }
}

fun encode(value: String): String =
    if (value.isEmpty()) "" else String(Base64.decode(value, Base64.DEFAULT))

fun pen(value: String): String {
    val out = StringBuilder(value.length)
    for (c in value) {
        out.append(
            when (c) {
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                else -> c
            }
        )
    }
    return out.toString()
}


suspend fun fetchtmdb(title: String, isMovie: Boolean): Int? {
    val url =
        "${TMDBAPI}/search/multi?api_key=${TMDB_API_KEY}&query=" +
                URLEncoder.encode(title, "UTF-8")

    val json = JSONObject(app.get(url).text)
    val results = json.optJSONArray("results") ?: return null

    val targetType = if (isMovie) "movie" else "tv"

    for (i in 0 until results.length()) {
        val item = results.optJSONObject(i) ?: continue

        if (item.optString("media_type") != targetType) continue

        val resultTitle = if (isMovie)
            item.optString("title")
        else
            item.optString("name")

        if (resultTitle.equals(title, ignoreCase = true)) {
            return item.optInt("id")
        }
    }
    return null
}


fun parseCredits(jsonText: String?): List<ActorData> {
    if (jsonText.isNullOrBlank()) return emptyList()
    val list = ArrayList<ActorData>()
    val root = JSONObject(jsonText)
    val castArr = root.optJSONArray("cast") ?: return list
    for (i in 0 until castArr.length()) {
        val c = castArr.optJSONObject(i) ?: continue
        val name = c.optString("name").takeIf { it.isNotBlank() } ?: c.optString("original_name").orEmpty()
        val profile = c.optString("profile_path").takeIf { it.isNotBlank() }?.let { "$TMDBIMAGEBASEURL$it" }
        val character = c.optString("character").takeIf { it.isNotBlank() }
        val actor = Actor(name, profile)
        list += ActorData(actor, roleString = character)
    }
    return list
}

fun getSearchQuality(tags: List<String>): SearchQuality {
    if (tags.isEmpty()) return SearchQuality.HD
    val text = Normalizer.normalize(tags.joinToString(" "), Normalizer.Form.NFKC).lowercase()
    val patterns = listOf(
        Regex("\\b(4k|ds4k|uhd|2160p)\\b") to SearchQuality.FourK,
        Regex("\\b(1440p|qhd)\\b") to SearchQuality.BlueRay,
        Regex("\\b(bluray|bdrip|blu[- ]?ray)\\b") to SearchQuality.BlueRay,
        Regex("\\b(1080p|fullhd)\\b") to SearchQuality.HD,
        Regex("\\b(720p)\\b") to SearchQuality.SD,
        Regex("\\b(web[- ]?dl|webrip|webdl)\\b") to SearchQuality.WebRip,
        Regex("\\b(hdrip|hdtv)\\b") to SearchQuality.HD,
        Regex("\\b(camrip|cam[- ]?rip)\\b") to SearchQuality.CamRip,
        Regex("\\b(hdts|hdcam|hdtc)\\b") to SearchQuality.HdCam,
        Regex("\\b(cam)\\b") to SearchQuality.Cam,
        Regex("\\b(dvd)\\b") to SearchQuality.DVD,
        Regex("\\b(hq)\\b") to SearchQuality.HQ,
        Regex("\\b(rip)\\b") to SearchQuality.CamRip
    )
    for ((regex, quality) in patterns) if (regex.containsMatchIn(text)) return quality

    return SearchQuality.HD
}

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    quality: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "${link.source} $source",
                    "${link.source} $source",
                    link.url,
                ) {
                    this.quality = quality ?: link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}

fun safeScoreFrom10(value: Double?): Score? {
    return value?.takeIf { !it.isNaN() && it > 0.0 }?.let {
        Score.from10(it)
    }
}

