package com.Kartoons

import com.fasterxml.jackson.annotation.JsonProperty

data class Home(
    val success: Boolean? = null,
    val data: List<Data>? = null,
)

data class Data(
    @get:JsonProperty("_id")
    val id: String? = null,
    val title: String? = null,
    val image: String? = null,
    val coverImage: String? = null,
    val hoverImage: String? = null,
    val releaseYear: Long? = null,
    val rating: Double? = null,
    val tags: List<String>? = null,
    val slug: String? = null,
    val type: String? = null,
)


data class Load(
    val success: Boolean? = null,
    val data: LoadData? = null,
    val related: List<Related>? = null,
    val watchHistory: List<Any?>? = null,
    val userRating: Any? = null,
    val totalRatings: Long? = null,
)

data class LoadData(
    @JsonProperty("_id")
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val coverImage: String? = null,
    val hoverImage: String? = null,
    val startYear: Long? = null,
    val endYear: Long? = null,
    val status: String? = null,
    val rating: Double? = null,
    val featured: Boolean? = null,
    val tags: List<String>? = null,
    val createdAt: CreatedAt? = null,
    val updatedAt: UpdatedAt? = null,
    val slug: String? = null,
    val seasons: List<Season>? = null,
    val viewCount: Long? = null,
    val type: String? = null,
)

data class CreatedAt(
    val iso: String? = null,
    val timestamp: Double? = null,
    val timezone: String? = null,
)

data class UpdatedAt(
    val iso: String? = null,
    val timestamp: Double? = null,
    val timezone: String? = null,
)

data class Season(
    @JsonProperty("_id")
    val id: String? = null,
    val seasonNumber: Long? = null,
    val title: String? = null,
    val releaseYear: Long? = null,
    val showId: String? = null,
    val createdAt: CreatedAt2? = null,
    val updatedAt: UpdatedAt2? = null,
    val slug: String? = null,
    val episodeCount: Long? = null,
)

data class CreatedAt2(
    val iso: String? = null,
    val timestamp: Double? = null,
    val timezone: String? = null,
)

data class UpdatedAt2(
    val iso: String? = null,
    val timestamp: Double? = null,
    val timezone: String? = null,
)

data class Related(
    @JsonProperty("_id")
    val id: String? = null,
    val title: String? = null,
    val image: String? = null,
    val startYear: Long? = null,
    val rating: Double? = null,
    val tags: List<String>? = null,
    val slug: String? = null,
    val type: String? = null,
)

//Load Shows Parser
data class SeasonEpisodes(
    val seasonNumber: Long?,
    val episodes: List<EpisodeItem>,
)


data class EpisodesRoot(
    val success: Boolean? = null,
    val data: List<EpisodeItem>? = null,
    val season: EpisodeSeasonMeta? = null,
    val show: EpisodeShowMeta? = null,
)

data class EpisodeItem(
    @JsonProperty("_id")
    val id: String? = null,
    val seasonId: String? = null,
    val episodeNumber: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val duration: String? = null,
    val createdAt: EpisodeCreatedAt? = null,
    val updatedAt: EpisodeUpdatedAt? = null,
    val durationMinutes: Long? = null,
)

data class EpisodeCreatedAt(
    val iso: String? = null,
    val timestamp: Double? = null,
    val timezone: String? = null,
)

data class EpisodeUpdatedAt(
    val iso: String? = null,
    val timestamp: Double? = null,
    val timezone: String? = null,
)

data class EpisodeSeasonMeta(
    @JsonProperty("_id")
    val id: String? = null,
    val seasonNumber: Long? = null,
)

data class EpisodeShowMeta(
    @JsonProperty("_id")
    val id: String? = null,
    val title: String? = null,
)

// Search

data class Search(
    val success: Boolean,
    val data: List<SearchDaum>,
)

data class SearchDaum(
    val id: String,
    val title: String,
    val image: String,
    val year: Long,
    val type: String,
)

//Loadlinks

data class Loadlinks(
    val success: Boolean? = null,
    val data: LoadlinksData? = null,
)

data class LoadlinksData(
    val links: List<Link>? = null,
    val title: String? = null,
    val id: String? = null,
)

data class Link(
    val name: String? = null,
    val url: String? = null,
    val subtitles: List<Any?>? = null,
    @get:JsonProperty("_link_index")
    val linkIndex: Long? = null,
)