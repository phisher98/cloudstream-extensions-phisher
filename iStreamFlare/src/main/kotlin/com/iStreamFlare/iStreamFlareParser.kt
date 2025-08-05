package com.IStreamFlare

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

data class HomeRes(
    val id: String,
    @SerializedName("TMDB_ID")
    val tmdbId: String,
    val name: String,
    val description: String,
    val genres: String,
    @SerializedName("release_date")
    val releaseDate: String,
    val runtime: String?,
    val poster: String,
    val banner: String,
    @SerializedName("youtube_trailer")
    val youtubeTrailer: String,
    val downloadable: String,
    val type: String,
    val status: String,
    @SerializedName("content_type")
    val contentType: String,
    @SerializedName("custom_tag")
    val customTag: CustomTag?
) {
    data class CustomTag(
        val id: String,
        @SerializedName("custom_tags_id")
        val customTagsId: String,
        @SerializedName("content_id")
        val contentId: String,
        @SerializedName("content_type")
        val contentType: String,
        @SerializedName("custom_tags_name")
        val customTagsName: String,
        @SerializedName("background_color")
        val backgroundColor: String,
        @SerializedName("text_color")
        val textColor: String
    )
}


data class StreamLinks(
    val id: String,
    val name: String,
    val size: String,
    val quality: String,
    @SerializedName("link_order")
    val linkOrder: String,
    @SerializedName("movie_id")
    val movieId: String,
    val url: String,
    val type: String,
    val status: String,
    @SerializedName("skip_available")
    val skipAvailable: String,
    @SerializedName("intro_start")
    val introStart: String,
    @SerializedName("intro_end")
    val introEnd: String,
    @SerializedName("end_credits_marker")
    val endCreditsMarker: String,
    @SerializedName("link_type")
    val linkType: String,
    @SerializedName("drm_uuid")
    val drmUuid: String,
    @SerializedName("drm_license_uri")
    val drmLicenseUri: String,
)

data class SeasonRes(
    val id: String,
    @SerializedName("Session_Name")
    val sessionName: String,
    @SerializedName("season_order")
    val seasonOrder: String,
    @SerializedName("web_series_id")
    val webSeriesId: String,
    val status: String
)


data class EpisodesRes(
    val id: String,
    @SerializedName("Episoade_Name")
    val episoadeName: String,
    @SerializedName("episoade_image")
    val episoadeImage: String,
    @SerializedName("episoade_description")
    val episoadeDescription: String,
    @SerializedName("episoade_order")
    val episoadeOrder: String,
    @SerializedName("season_id")
    val seasonId: String,
    val downloadable: String,
    val type: String,
    val status: String,
    val source: String,
    val url: String,
    @SerializedName("skip_available")
    val skipAvailable: String,
    @SerializedName("intro_start")
    val introStart: String,
    @SerializedName("intro_end")
    val introEnd: String,
    @SerializedName("end_credits_marker")
    val endCreditsMarker: String,
    @SerializedName("drm_uuid")
    val drmUuid: String,
    @SerializedName("drm_license_uri")
    val drmLicenseUri: String
)


data class LoadDataObject(
    val id: String,
    val tmdbId: String,
    val contentType: String?,
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