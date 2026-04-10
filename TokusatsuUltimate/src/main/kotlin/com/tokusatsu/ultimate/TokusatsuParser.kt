package com.tokusatsu.ultimate

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

// Data classes for parsing JSON responses from tokusatsu sources
data class TokusatsuSearchResult(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("description") val description: String? = null
)

data class TokusatsuDetail(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("banner") val banner: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("genres") val genres: List<String>? = null,
    @JsonProperty("episodes") val episodes: List<TokusatsuEpisode>? = null,
    @JsonProperty("rating") val rating: String? = null
)

data class TokusatsuEpisode(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("thumbnail") val thumbnail: String? = null,
    @JsonProperty("air_date") val airDate: String? = null
)

data class TokusatsuLinks(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("links") val links: List<TokusatsuVideoLink>? = null,
    @JsonProperty("subtitles") val subtitles: List<TokusatsuSubtitle>? = null
)

data class TokusatsuVideoLink(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("is_m3u8") val isM3u8: Boolean? = null
)

data class TokusatsuSubtitle(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("format") val format: String? = null
)