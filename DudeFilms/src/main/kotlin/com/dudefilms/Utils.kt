package com.dudefilms

import com.lagradost.cloudstream3.SearchQuality
import org.jsoup.nodes.Element
import java.text.Normalizer

fun cleanTitle(raw: String?): String {
    val regex = Regex("""S(\d+)[Ee](\d+)(?:-(\d+))?""")
    val match = regex.find(raw ?: "") ?: return raw!!.trim()

    val season = match.groupValues[1].toInt()
    val epStart = match.groupValues[2].toInt()
    val epEnd = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toInt()

    val showName = raw?.substringBefore(match.value)!!.trim()
    val year = Regex("""\((\d{4})\)""").find(raw)?.groupValues?.get(1)

    val titleBase = if (year != null) showName else showName
    val episodes = if (epEnd != null) "Episodes $epStart–$epEnd" else "Episode $epStart"

    return "$titleBase Season $season | $episodes"
}

data class Meta(
    val id: String?,
    val imdb_id: String?,
    val type: String?,
    val poster: String?,
    val logo: String?,
    val background: String?,
    val moviedb_id: Int?,
    val name: String?,
    val description: String?,
    val genre: List<String>?,
    val releaseInfo: String?,
    val status: String?,
    val runtime: String?,
    val cast: List<String>?,
    val language: String?,
    val country: String?,
    val imdbRating: String?,
    val slug: String?,
    val year: String?,
    val videos: List<EpisodeDetails>?
)

data class EpisodeDetails(
    val id: String?,
    val name: String?,
    val title: String?,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val overview: String?,
    val thumbnail: String?,
    val moviedb_id: Int?
)

data class ResponseData(
    val meta: Meta?
)

fun isBlockedButton(a: Element): Boolean {
    val text = a.selectFirst("span.mb-text")
        ?.text()
        ?.lowercase()
        ?: a.text().lowercase()

    return listOf("zipfile", "torrent", "rar", "7z").any { text.contains(it) }
}


/**
 * Determines the search quality based on the presence of specific keywords in the input string.
 *
 * @param check The string to check for keywords.
 * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
 */
fun getSearchQuality(check: String?): SearchQuality? {
    val s = check ?: return null
    val u = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
    val patterns = listOf(
        Regex("\\b(4k|ds4k|uhd|2160p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.FourK,

        // CAM / THEATRE SOURCES FIRST
        Regex("\\b(hdts|hdcam|hdtc)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HdCam,
        Regex("\\b(camrip|cam[- ]?rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip,
        Regex("\\b(cam)\\b", RegexOption.IGNORE_CASE) to SearchQuality.Cam,

        // WEB / RIP
        Regex("\\b(web[- ]?dl|webrip|webdl)\\b", RegexOption.IGNORE_CASE) to SearchQuality.WebRip,

        // BLURAY
        Regex("\\b(bluray|bdrip|blu[- ]?ray)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,

        // RESOLUTIONS
        Regex("\\b(1440p|qhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,
        Regex("\\b(1080p|fullhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
        Regex("\\b(720p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.SD,

        // GENERIC HD LAST
        Regex("\\b(hdrip|hdtv)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,

        Regex("\\b(dvd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.DVD,
        Regex("\\b(hq)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HQ,
        Regex("\\b(rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip
    )


    for ((regex, quality) in patterns) if (regex.containsMatchIn(u)) return quality
    return null
}