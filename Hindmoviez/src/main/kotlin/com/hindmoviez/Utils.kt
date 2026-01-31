package com.hindmoviez

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
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

fun parseCredits(jsonText: String?): List<ActorData> {
    if (jsonText.isNullOrBlank()) return emptyList()
    val list = ArrayList<ActorData>()
    val root = JSONObject(jsonText)
    val castArr = root.optJSONArray("cast") ?: return list
    for (i in 0 until castArr.length()) {
        val c = castArr.optJSONObject(i) ?: continue
        val name = c.optString("name").takeIf { it.isNotBlank() } ?: c.optString("original_name").orEmpty()
        val profile = c.optString("profile_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/original$it" }
        val character = c.optString("character").takeIf { it.isNotBlank() }
        val actor = Actor(name, profile)
        list += ActorData(actor, roleString = character)
    }
    return list
}

data class ResponseData(
    val meta: Meta? = null
) {

    data class Meta(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        @JsonProperty("imdb_id")
        val imdbId: String? = null,

        val slug: String? = null,

        val director: String? = null,
        val writer: String? = null,

        val description: String? = null,
        val year: String? = null,
        val releaseInfo: String? = null,
        val released: String? = null,
        val runtime: String? = null,
        val status: String? = null,
        val country: String? = null,
        val imdbRating: String? = null,
        val genres: List<String>? = null,
        val poster: String? = null,
        @JsonProperty("_rawPosterUrl")
        val rawPosterUrl: String? = null,

        val background: String? = null,
        val logo: String? = null,

        val videos: List<EpisodeDetails>? = null,
        val trailers: List<Trailer>? = null,
        val trailerStreams: List<TrailerStream>? = null,
        val links: List<Link>? = null,
        val behaviorHints: BehaviorHints? = null,
        @SerializedName("app_extras")
        val appExtras: AppExtras? = null
    ) {

        data class BehaviorHints(
            val defaultVideoId: Any? = null,
            val hasScheduledVideos: Boolean? = null
        )

        data class Link(
            val name: String? = null,
            val category: String? = null,
            val url: String? = null
        )

        data class Trailer(
            val source: String? = null,
            val type: String? = null,
            val name: String? = null
        )

        data class TrailerStream(
            val ytId: String? = null,
            val title: String? = null
        )

        data class EpisodeDetails(
            val id: String? = null,
            val title: String? = null,
            val season: Int? = null,
            val episode: Int? = null,
            val thumbnail: String? = null,
            val overview: String? = null,
            val released: String? = null,
            val available: Boolean? = null,
            val runtime: String? = null
        )

        data class AppExtras(
            val cast: List<Cast>? = null,
            val directors: List<Any?>? = null,
            val writers: List<Any?>? = null,
            val seasonPosters: List<String?>? = null,
            val certification: String? = null
        )

        data class Cast(
            val name: String? = null,
            val character: String? = null,
            val photo: String? = null
        )
    }
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

fun buildExtractedTitle(extracted: Map<String, List<String>>): String {
    val orderedCategories = listOf("quality", "codec", "audio", "hdr", "language")

    val specs = orderedCategories
        .flatMap { extracted[it] ?: emptyList() }
        .distinct()
        .joinToString(" ")

    val size = extracted["size"]?.firstOrNull()

    return if (size != null) {
        "$specs [$size]"
    } else {
        specs
    }
}

val SPEC_OPTIONS = mapOf(
    "quality" to listOf(
        mapOf("value" to "BluRay", "label" to "BluRay"),
        mapOf("value" to "BluRay REMUX", "label" to "BluRay REMUX"),
        mapOf("value" to "BRRip", "label" to "BRRip"),
        mapOf("value" to "BDRip", "label" to "BDRip"),
        mapOf("value" to "WEB-DL", "label" to "WEB-DL"),
        mapOf("value" to "HDRip", "label" to "HDRip"),
        mapOf("value" to "DVDRip", "label" to "DVDRip"),
        mapOf("value" to "HDTV", "label" to "HDTV"),
        mapOf("value" to "CAM", "label" to "CAM"),
        mapOf("value" to "TeleSync", "label" to "TeleSync"),
        mapOf("value" to "SCR", "label" to "SCR"),
        mapOf("value" to "10bit", "label" to "10bit"),
        mapOf("value" to "8bit", "label" to "8bit"),
    ),
    "codec" to listOf(
        mapOf("value" to "x264", "label" to "x264"),
        mapOf("value" to "x265", "label" to "x265 (HEVC)"),
        mapOf("value" to "h.264", "label" to "H.264 (AVC)"),
        mapOf("value" to "h.265", "label" to "H.265 (HEVC)"),
        mapOf("value" to "hevc", "label" to "HEVC"),
        mapOf("value" to "avc", "label" to "AVC"),
        mapOf("value" to "mpeg-2", "label" to "MPEG-2"),
        mapOf("value" to "mpeg-4", "label" to "MPEG-4"),
        mapOf("value" to "vp9", "label" to "VP9")
    ),
    "audio" to listOf(
        mapOf("value" to "AAC", "label" to "AAC"),
        mapOf("value" to "AC3", "label" to "AC3 (Dolby Digital)"),
        mapOf("value" to "DTS", "label" to "DTS"),
        mapOf("value" to "DTS-HD MA", "label" to "DTS-HD MA"),
        mapOf("value" to "TrueHD", "label" to "Dolby TrueHD"),
        mapOf("value" to "Atmos", "label" to "Dolby Atmos"),
        mapOf("value" to "DD+", "label" to "DD+"),
        mapOf("value" to "Dolby Digital Plus", "label" to "Dolby Digital Plus"),
        mapOf("value" to "DTS Lossless", "label" to "DTS Lossless")
    ),
    "hdr" to listOf(
        mapOf("value" to "DV", "label" to "Dolby Vision"),
        mapOf("value" to "HDR10+", "label" to "HDR10+"),
        mapOf("value" to "HDR", "label" to "HDR"),
        mapOf("value" to "SDR", "label" to "SDR")
    ),
    "language" to listOf(
        mapOf("value" to "HIN", "label" to "Hindi🇮🇳"),
        mapOf("value" to "Hindi", "label" to "Hindi🇮🇳"),
        mapOf("value" to "Tamil", "label" to "Tamil🇮🇳"),
        mapOf("value" to "ENG", "label" to "English🇺🇸"),
        mapOf("value" to "English", "label" to "English🇺🇸"),
        mapOf("value" to "Korean", "label" to "Korean🇰🇷"),
        mapOf("value" to "KOR", "label" to "Korean🇰🇷"),
        mapOf("value" to "Japanese", "label" to "Japanese🇯🇵"),
        mapOf("value" to "Chinese", "label" to "Chinese🇨🇳"),
        mapOf("value" to "Telugu", "label" to "Telugu🇮🇳"),
    )
)

fun extractSpecs(inputString: String): Map<String, List<String>> {
    val results = mutableMapOf<String, List<String>>()

    SPEC_OPTIONS.forEach { (category, options) ->
        val matches = options.filter { option ->
            val value = option["value"] as String
            val regexPattern = "\\b${Regex.escape(value)}\\b".toRegex(RegexOption.IGNORE_CASE)
            regexPattern.containsMatchIn(inputString)
        }.map { it["label"] as String }

        results[category] = matches
    }

    val fileSizeRegex = """(\d+(?:\.\d+)?\s?(?:MB|GB))""".toRegex(RegexOption.IGNORE_CASE)
    val sizeMatch = fileSizeRegex.find(inputString)
    if (sizeMatch != null) {
        results["size"] = listOf(sizeMatch.groupValues[1])
    }

    return results.toMap()
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}
