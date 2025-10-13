package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

data class Home(
    val `data`: List<Data>,
    val status: String
) {
    data class Data(
        val last_updated: String,
        val poster_path: String,
        val release_date: String,
        val title: String,
        val tmdb_id: Int,
        val type: String
    )
}

data class HomePageHome(
    @SerializedName("tmdb_id")
    val tmdbId: Long,
    val title: String,
    @SerializedName("release_date")
    val releaseDate: String,
    @SerializedName("poster_path")
    val posterPath: String,
    val type: String,
    val timestamp: String,
)


class SearchData : ArrayList<SearchData.SearchDataItem>(){
    data class SearchDataItem(
        val exact_match: Int,
        val id: Int,
        val poster: String,
        val release_year: String,
        val title: String,
        val tmdb_id: Int,
        val type: String
    )
}

data class Media(
    val id: Int,
    val tmdb_id: Int?,
    val title: String,
    val name: String,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val genres: String?,
    val language: String?,
    val rating: Double?,
    val popularity: Double?,
    val cast: String?,
    val firstAirDate: String,

    // Movie-specific fields
    val release_date: String? = null,
    val runtime: Int? = null,
    val movie_source: String? = null,
    val download_links: List<Download>? = null,

    // Series-specific fields
    val first_air_date: String? = null,
    val total_seasons: Int? = null,
    val total_episodes: Int? = null,
    val download_data: SeriesDownloadData? = null
)

data class Download(
    val version_id: Int? = null,
    val resolution: String? = null,
    val custom_title: String? = null,
    val size: String? = null,
    val download_link: String? = null,
    val title: String? = null,
    val audio_languages: String? = null,
    val source: String? = null
)

data class SeriesDownloadData(
    val id: Int? = null,
    val title: String? = null,
    val audio_languages: String? = null,
    val source: String? = null,
    val seasons: List<Season>? = null
)

data class Season(
    val id: Int? = null,
    val season_num: Int? = null,
    val episodes: List<Episode>? = null
)

data class Episode(
    val episode_number: Int? = null,
    val versions: List<Download>? = null
)


data class IMDB(
    @JsonProperty("imdb_id")
    val imdbId: String,
)

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


data class TMDBRes(
    @JsonProperty("_id")
    val id: String,
    @JsonProperty("air_date")
    val airDate: String,
    val episodes: List<TMDBResEpisode>,
    val name: String,
    val overview: String,
    @JsonProperty("id")
    val id2: Long,
    @JsonProperty("poster_path")
    val posterPath: String,
    @JsonProperty("season_number")
    val seasonNumber: Long,
    @JsonProperty("vote_average")
    val voteAverage: Long,
)

data class TMDBResEpisode(
    @JsonProperty("air_date")
    val airDate: String,
    @JsonProperty("episode_number")
    val episodeNumber: Long,
    @JsonProperty("episode_type")
    val episodeType: String,
    val id: Long,
    val name: String,
    val overview: String,
    @JsonProperty("production_code")
    val productionCode: String,
    val runtime: Long,
    @JsonProperty("season_number")
    val seasonNumber: Long,
    @JsonProperty("show_id")
    val showId: Long,
    @JsonProperty("still_path")
    val stillPath: String,
    @JsonProperty("vote_average")
    val voteAverage: Double,
    @JsonProperty("vote_count")
    val voteCount: Long,
    val crew: List<Any?>,
)