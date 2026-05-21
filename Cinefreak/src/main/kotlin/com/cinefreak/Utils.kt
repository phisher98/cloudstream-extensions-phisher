package com.cinefreak

import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.Qualities

fun cleanTitle(raw: String): String {
    val name = raw.substringBefore("(").trim()
        .replace(Regex("""\s+"""), " ") // collapse extra spaces
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val seasonRegex = Regex("""Season\s*\d+""", RegexOption.IGNORE_CASE)
    val yearRegex = Regex("""\b(19|20)\d{2}\b""")

    val season = seasonRegex.find(raw)?.value?.replaceFirstChar { it.uppercase() }
    val year = yearRegex.find(raw)?.value

    val parts = mutableListOf<String>()
    if (season != null) parts += season
    if (year != null) parts += year

    return if (parts.isEmpty()) {
        name
    } else {
        name + parts.joinToString("") { " ($it)" }
    }
}


data class ResponseDataLocal(val meta: MetaLocal?)

data class MetaLocal(
    val name: String? = null,
    val description: String? = null,
    val actorsData: List<ActorData>? = null,
    val year: String? = null,
    val background: String? = null,
    val genres: List<String>? = null,
    val videos: List<VideoLocal>? = null,
    val rating: Score?,
    val logo: String?,
    val imdbId: String?
)
data class VideoLocal(
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null,
    val rating: Score?
)

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}