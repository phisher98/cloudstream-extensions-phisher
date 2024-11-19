package com.TorraStream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.Qualities

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
        ?: Qualities.Unknown.value
}

data class TorrentioResponse(val streams: List<TorrentioStream>)

data class TorrentioStream(
    val name: String?,
    val title: String?,
    val infoHash: String?,
    val fileIdx: Int?,
)

data class LinkData(
    @JsonProperty("simklId") val simklId: Int? = null,
    @JsonProperty("traktId") val traktId: Int? = null,
    @JsonProperty("imdbId") val imdbId: String? = null,
    @JsonProperty("tmdbId") val tmdbId: Int? = null,
    @JsonProperty("tvdbId") val tvdbId: Int? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
    @JsonProperty("aniId") val aniId: String? = null,
    @JsonProperty("malId") val malId: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("orgTitle") val orgTitle: String? = null,
    @JsonProperty("isAnime") val isAnime: Boolean = false,
    @JsonProperty("airedYear") val airedYear: Int? = null,
    @JsonProperty("lastSeason") val lastSeason: Int? = null,
    @JsonProperty("epsTitle") val epsTitle: String? = null,
    @JsonProperty("jpTitle") val jpTitle: String? = null,
    @JsonProperty("date") val date: String? = null,
    @JsonProperty("airedDate") val airedDate: String? = null,
    @JsonProperty("isAsian") val isAsian: Boolean = false,
    @JsonProperty("isBollywood") val isBollywood: Boolean = false,
    @JsonProperty("isCartoon") val isCartoon: Boolean = false,
)