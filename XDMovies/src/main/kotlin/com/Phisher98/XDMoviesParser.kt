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
    val networks: List<Network>,
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
    val episodeNumber: Int,
    @JsonProperty("episode_type")
    val episodeType: String,
    val id: Long,
    val name: String,
    val overview: String,
    @JsonProperty("production_code")
    val productionCode: String,
    val runtime: Long?,
    @JsonProperty("season_number")
    val seasonNumber: Long,
    @JsonProperty("show_id")
    val showId: Long,
    @JsonProperty("still_path")
    val stillPath: String?,
    @JsonProperty("vote_average")
    val voteAverage: Double,
    @JsonProperty("vote_count")
    val voteCount: Long,
    val crew: List<Crew>,
    @JsonProperty("guest_stars")
    val guestStars: List<GuestStar>,
)

data class Crew(
    val job: String,
    val department: String,
    @JsonProperty("credit_id")
    val creditId: String,
    val adult: Boolean,
    val gender: Long,
    val id: Long,
    @JsonProperty("known_for_department")
    val knownForDepartment: String,
    val name: String,
    @JsonProperty("original_name")
    val originalName: String,
    val popularity: Double,
    @JsonProperty("profile_path")
    val profilePath: String?,
)

data class GuestStar(
    val character: String,
    @JsonProperty("credit_id")
    val creditId: String,
    val order: Long,
    val adult: Boolean,
    val gender: Long,
    val id: Long,
    @JsonProperty("known_for_department")
    val knownForDepartment: String,
    val name: String,
    @JsonProperty("original_name")
    val originalName: String,
    val popularity: Double,
    @JsonProperty("profile_path")
    val profilePath: String?,
)

data class Network(
    val id: Long,
    @JsonProperty("logo_path")
    val logoPath: String,
    val name: String,
    @JsonProperty("origin_country")
    val originCountry: String,
)
