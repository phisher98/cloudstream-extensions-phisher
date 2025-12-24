package com.MovieBlast

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class LoadURL(
    val link: String?=null ,
    val server: String?=null,
    val lang: String?=null,
)


data class Home(
    @JsonProperty("current_page")
    val currentPage: Long? = null,
    val data: List<HomeDaum> = emptyList(),
    @JsonProperty("first_page_url")
    val firstPageUrl: String? = null,
    val from: Long? = null,
    @JsonProperty("last_page")
    val lastPage: Long? = null,
    @JsonProperty("last_page_url")
    val lastPageUrl: String? = null,
    val links: List<HomeLink> = emptyList(),
    @JsonProperty("next_page_url")
    val nextPageUrl: String? = null,
    val path: String? = null,
    @JsonProperty("per_page")
    val perPage: Long? = null,
    @JsonProperty("prev_page_url")
    val prevPageUrl: String? = null,
    val to: Long? = null,
    val total: Long? = null
)

data class HomeDaum(
    val id: Long? = null,
    val name: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    @JsonProperty("backdrop_path")
    val backdropPath: String? = null,
    @JsonProperty("backdrop_path_tv")
    val backdropPathTv: String? = null,
    @JsonProperty("vote_average")
    val voteAverage: Double? = null,
    val subtitle: String? = null,
    val overview: String? = null,
    @JsonProperty("release_date")
    val releaseDate: String? = null,
    val pinned: Long? = null,
    @JsonProperty("created_at")
    val createdAt: String? = null,
    val views: Long? = null,
    val type: String? = null,
    @JsonProperty("genre_name")
    val genreName: String? = null,
    @JsonProperty("recent_views")
    val recentViews: Long? = null,
    @JsonProperty("content_type")
    val contentType: String? = null
)

data class HomeLink(
    val url: String? = null,
    val label: String? = null,
    val active: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MediaDetailResponse(
    val id: Long? = null,
    @JsonProperty("tmdb_id")
    val tmdbId: String? = null,
    @JsonAlias("title", "name")
    val title: String? = null,
    @JsonProperty("original_name")
    val originalName: String? = null,
    @JsonProperty("imdb_external_id")
    val imdbExternalId: String? = null,
    val subtitle: String? = null,
    val overview: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    @JsonProperty("backdrop_path")
    val backdropPath: String? = null,
    @JsonProperty("backdrop_path_tv")
    val backdropPathTv: String? = null,
    @JsonProperty("preview_path")
    val previewPath: String? = null,
    @JsonProperty("vote_average")
    val voteAverage: Double? = null,
    @JsonProperty("vote_count")
    val voteCount: Long? = null,
    val popularity: Double? = null,
    val runtime: String? = null,
    val views: Long? = null,
    val featured: Boolean? = null,
    val premuim: Boolean? = null,
    val active: Boolean? = null,
    val pinned: Boolean? = null,
    @JsonAlias("release_date", "first_air_date")
    val releaseDate: String? = null,

    @JsonProperty("skiprecap_start_in")
    val skipRecapStartIn: Long? = null,

    val hasrecap: Boolean? = null,

    @JsonProperty("enable_stream")
    val enableStream: Boolean? = null,

    @JsonProperty("enable_media_download")
    val enableMediaDownload: Boolean? = null,

    @JsonProperty("enable_ads_unlock")
    val enableAdsUnlock: Boolean? = null,

    val casterslist: List<Caster> = emptyList(),
    val networkslist: List<Network> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val videos: List<Video> = emptyList(),
    val seasons: List<Season> = emptyList()
)

data class Caster(
    val id: Long? = null,
    val name: String? = null,
    @JsonProperty("original_name")
    val originalName: String? = null,
    @JsonProperty("profile_path")
    val profilePath: String? = null,
    val character: String? = null
)

data class Network(
    val id: Long? = null,
    val name: String? = null,
    @JsonProperty("logo_path")
    val logoPath: String? = null,
    @JsonProperty("origin_country")
    val originCountry: String? = null,
    @JsonProperty("created_at")
    val createdAt: String? = null,
    @JsonProperty("updated_at")
    val updatedAt: String? = null
)

data class Genre(
    val id: Long? = null,
    @JsonProperty("movie_id")
    val movieId: Long? = null,
    @JsonProperty("genre_id")
    val genreId: Long? = null,
    val name: String? = null
)

data class Season(
    val id: Long,
    @JsonProperty("tmdb_id")
    val tmdbId: Long,
    @JsonProperty("serie_id")
    val serieId: Long,
    @JsonProperty("season_number")
    val seasonNumber: Int,
    val name: String,
    val overview: Any?,
    @JsonProperty("poster_path")
    val posterPath: String,
    @JsonProperty("air_date")
    val airDate: String,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    val episodes: List<Episode>,
)

data class Episode(
    val id: Long,
    @JsonProperty("tmdb_id")
    val tmdbId: Long,
    @JsonProperty("season_id")
    val seasonId: Long,
    @JsonProperty("episode_number")
    val episodeNumber: Int,
    val name: String,
    val overview: String,
    @JsonProperty("still_path")
    val stillPath: String,
    @JsonProperty("vote_average")
    val voteAverage: Long,
    @JsonProperty("vote_count")
    val voteCount: Long,
    val views: Long,
    @JsonProperty("air_date")
    val airDate: String,
    @JsonProperty("skiprecap_start_in")
    val skiprecapStartIn: Long,
    val hasrecap: Long,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    @JsonProperty("still_path_tv")
    val stillPathTv: String,
    @JsonProperty("enable_stream")
    val enableStream: Long,
    @JsonProperty("enable_media_download")
    val enableMediaDownload: Long,
    @JsonProperty("enable_ads_unlock")
    val enableAdsUnlock: Long,
    val videos: List<Video>,
    val substitles: List<Any?>,
    val downloads: List<Any?>,
)

data class Video(
    val id: Long? = null,
    @JsonProperty("movie_id")
    val movieId: Long? = null,
    val server: String? = null,
    val link: String? = null,
    val lang: String? = null,
    val hd: Boolean? = null,
    val embed: Boolean? = null,
    val youtubelink: Boolean? = null,
    val hls: Boolean? = null,
    val drm: Boolean? = null,
    val status: Long? = null,
    @JsonProperty("created_at")
    val createdAt: String? = null,
    @JsonProperty("updated_at")
    val updatedAt: String? = null
)

//search

data class SearchRoot(
    val search: List<Search>,
)

data class Search(
    val id: Long,
    val name: String,

    @JsonProperty("original_name")
    val originalName: String?,

    @JsonProperty("poster_path")
    val posterPath: String?,

    @JsonProperty("backdrop_path")
    val backdropPath: String?,

    @JsonProperty("backdrop_path_tv")
    val backdropPathTv: String?,

    @JsonProperty("vote_average")
    val voteAverage: Double?,

    val subtitle: Any?,

    val overview: String?,

    @JsonProperty("release_date")
    val releaseDate: String?,

    val pinned: Int?,

    @JsonProperty("created_at")
    val createdAt: String?,

    @JsonProperty("updated_at")
    val updatedAt: String?,

    val views: Long?,

    val type: String,

    @JsonProperty("genre_name")
    val genreName: String?,

    @JsonProperty("match_score")
    val matchScore: Double?
)


