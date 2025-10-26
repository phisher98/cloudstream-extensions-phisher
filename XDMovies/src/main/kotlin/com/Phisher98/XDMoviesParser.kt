package com.phisher98

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
    @SerializedName("_id")
    val id: String? = null,

    @SerializedName("air_date")
    val airDate: String? = null,

    val episodes: List<TMDBEpisode>? = null,

    val name: String? = null,

    val networks: List<Network>? = null,

    val overview: String? = null,

    @SerializedName("id")
    val id2: Long? = null,

    @SerializedName("poster_path")
    val posterPath: String? = null,

    @SerializedName("season_number")
    val seasonNumber: Int? = null,

    @SerializedName("vote_average")
    val voteAverage: Double? = null
)

data class TMDBEpisode(
    @SerializedName("air_date")
    val airDate: String? = null,

    @SerializedName("episode_number")
    val episodeNumber: Int? = null,

    @SerializedName("episode_type")
    val episodeType: String? = null,

    val id: Long? = null,

    val name: String? = null,

    val overview: String? = null,

    @SerializedName("production_code")
    val productionCode: String? = null,

    val runtime: Int? = null,

    @SerializedName("season_number")
    val seasonNumber: Int? = null,

    @SerializedName("show_id")
    val showId: Long? = null,

    @SerializedName("still_path")
    val stillPath: String? = null,

    @SerializedName("vote_average")
    val voteAverage: Double? = null,

    @SerializedName("vote_count")
    val voteCount: Int? = null,

    val crew: List<Crew>? = null,

    @SerializedName("guest_stars")
    val guestStars: List<GuestStar>? = null
)

data class Network(
    val id: Long? = null,
    @SerializedName("logo_path")
    val logoPath: String? = null,
    val name: String? = null,
    @SerializedName("origin_country")
    val originCountry: String? = null
)

data class Crew(
    val id: Long? = null,
    val name: String? = null,
    val job: String? = null
)

data class GuestStar(
    val id: Long? = null,
    val name: String? = null,
    val character: String? = null,
    val order: Int? = null
)