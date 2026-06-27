package com.movies4u

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import kotlin.sequences.forEach

data class VideoLocal(
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null,
    val rating: Double? = null
)

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

    val lang = appLangCode?.trim()?.lowercase()?.substringBefore("-")

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

private val extractorTitleExtensionRegex = Regex("\\.[a-zA-Z0-9]{2,4}$")
private val extractorTitlePatterns = listOf(
    Regex("(WEB[- ]?DL|WEB[- ]?RIP|WEBDL|WEBRIP|BLURAY|BDRIP|BRRIP|REMUX|HDRIP|DVDRIP|HDTV|UHD|CAM|TS|TC)", RegexOption.IGNORE_CASE),
    Regex("(H[ .]?264|H[ .]?265|X264|X265|HEVC|AVC|AV1|VP9|XVID)", RegexOption.IGNORE_CASE),
    Regex("(DDP?[ .]?[0-9]\\.[0-9]|DD[ .]?[0-9]\\.[0-9]|AAC[ .]?[0-9]\\.[0-9]|AC3|DTS[- ]?HD|DTS|EAC3|TRUEHD|ATMOS|FLAC|MP3|OPUS)", RegexOption.IGNORE_CASE),
    Regex("(HDR10\\+?|HDR|DV|DOLBY[ .]?VISION)", RegexOption.IGNORE_CASE),
    Regex("\\b(NF|AMZN|DSNP|HULU|CRAV|ATVP|HMAX|PCOK|STAN)\\b", RegexOption.IGNORE_CASE),
    Regex("\\b(REPACK|PROPER|REAL|EXTENDED|UNCUT|REMASTERED|LIMITED|MULTI|DUAL)\\b", RegexOption.IGNORE_CASE)
)
private val extractorNormalizeWebDlRegex = Regex("WEB[-_. ]?DL")
private val extractorNormalizeWebRipRegex = Regex("WEB[-_. ]?RIP")
private val extractorNormalizeH265Regex = Regex("H[ .]?265")
private val extractorNormalizeH264Regex = Regex("H[ .]?264")
private val extractorNormalizeDolbyVisionRegex = Regex("DOLBY[ .]?VISION")
private val extractorQualityRegex = Regex("(\\d{3,4})[pP]")

fun extractIndexQuality(str: String?, defaultQuality: Int = Qualities.Unknown.value): Int {
    return extractorQualityRegex.find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: defaultQuality
}

fun extractCleanTitle(title: String): String {
    val name = title.replace(extractorTitleExtensionRegex, "")
    val results = linkedSetOf<String>()

    for (pattern in extractorTitlePatterns) {
        pattern.findAll(name).forEach { match ->
            val value = match.value.uppercase()
                .replace(extractorNormalizeWebDlRegex, "WEB-DL")
                .replace(extractorNormalizeWebRipRegex, "WEBRIP")
                .replace(extractorNormalizeH265Regex, "H265")
                .replace(extractorNormalizeH264Regex, "H264")
                .replace(extractorNormalizeDolbyVisionRegex, "DOLBYVISION")
                .replace("2160P", "4K")
            results.add(value)
        }
    }

    return results.joinToString(" ")
}
