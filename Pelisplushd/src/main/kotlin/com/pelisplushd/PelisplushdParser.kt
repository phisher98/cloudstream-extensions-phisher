package com.pelisplushd

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.mvvm.logError
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Results(
    @param:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class Media(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("original_title") val originalTitle: String? = null,
    @param:JsonProperty("media_type") val mediaType: String? = null,
    @param:JsonProperty("poster_path") val posterPath: String? = null,
    @param:JsonProperty("vote_average") val voteAverage: Double? = null,
)

data class Genres(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
)

data class Keywords(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
)

data class KeywordResults(
    @get:JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
    @get:JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
)

data class Seasons(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("season_number") val seasonNumber: Int? = null,
    @get:JsonProperty("air_date") val airDate: String? = null,
)

data class Cast(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("original_name") val originalName: String? = null,
    @get:JsonProperty("character") val character: String? = null,
    @get:JsonProperty("known_for_department") val knownForDepartment: String? = null,
    @get:JsonProperty("profile_path") val profilePath: String? = null,
)

data class Episodes(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("overview") val overview: String? = null,
    @get:JsonProperty("air_date") val airDate: String? = null,
    @get:JsonProperty("still_path") val stillPath: String? = null,
    @get:JsonProperty("vote_average") val voteAverage: Double? = null,
    @get:JsonProperty("episode_number") val episodeNumber: Int? = null,
    @get:JsonProperty("season_number") val seasonNumber: Int? = null,
    @get:JsonProperty("runtime") val runTime: Int? = null
)

data class MediaDetailEpisodes(
    @get:JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
)

data class Trailers(
    @get:JsonProperty("key") val key: String? = null,
    @get:JsonProperty("type") val type: String? = null,
)

data class ResultsTrailer(
    @get:JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
)

data class AltTitles(
    @get:JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
    @get:JsonProperty("title") val title: String? = null,
    @get:JsonProperty("type") val type: String? = null,
)

data class ResultsAltTitles(
    @get:JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
)

data class ExternalIds(
    @get:JsonProperty("imdb_id") val imdb_id: String? = null,
    @get:JsonProperty("tvdb_id") val tvdb_id: Int? = null,
)

data class Credits(
    @get:JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
)

data class ResultsRecommendations(
    @get:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class LastEpisodeToAir(
    @get:JsonProperty("episode_number") val episode_number: Int? = null,
    @get:JsonProperty("season_number") val season_number: Int? = null,
)

data class ProductionCountries(
    @get:JsonProperty("name") val name: String? = null,
)

data class MediaDetail(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("imdb_id") val imdbId: String? = null,
    @get:JsonProperty("title") val title: String? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("original_title") val originalTitle: String? = null,
    @get:JsonProperty("original_name") val originalName: String? = null,
    @get:JsonProperty("poster_path") val posterPath: String? = null,
    @get:JsonProperty("backdrop_path") val backdropPath: String? = null,
    @get:JsonProperty("release_date") val releaseDate: String? = null,
    @get:JsonProperty("first_air_date") val firstAirDate: String? = null,
    @get:JsonProperty("overview") val overview: String? = null,
    @get:JsonProperty("runtime") val runtime: Int? = null,
    @get:JsonProperty("vote_average") val vote_average: Any? = null,
    @get:JsonProperty("original_language") val original_language: String? = null,
    @get:JsonProperty("status") val status: String? = null,
    @get:JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
    @get:JsonProperty("keywords") val keywords: KeywordResults? = null,
    @get:JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
    @get:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
    @get:JsonProperty("videos") val videos: ResultsTrailer? = null,
    @get:JsonProperty("external_ids") val external_ids: ExternalIds? = null,
    @get:JsonProperty("credits") val credits: Credits? = null,
    @get:JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
    @get:JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
    @get:JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf(),
)

data class LoadData(
    val title: String? = null,
    val year: Int? =null,
    val isAnime: Boolean = false,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

data class Data(
    val id: Int? = null,
    val type: String? = null,
    val aniId: String? = null,
    val malId: Int? = null,
)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String,
    val monthStart: String
)

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()

    // Today
    val today = formatter.format(calendar.time)

    // Next week
    calendar.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calendar.time)

    // Last week's Monday
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    calendar.add(Calendar.WEEK_OF_YEAR, -1)
    val lastWeekStart = formatter.format(calendar.time)

    // Start of current month
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val monthStart = formatter.format(calendar.time)

    return TmdbDate(today, nextWeek, lastWeekStart, monthStart)
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }
}