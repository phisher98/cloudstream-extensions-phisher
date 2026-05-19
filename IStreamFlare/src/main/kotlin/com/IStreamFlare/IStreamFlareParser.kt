package com.IStreamFlare

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.phisher98.BuildConfig
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class HomeRes(

    @JsonProperty("TMDB_ID")
    val tmdbId: String? = null,

    @JsonProperty("banner")
    val banner: String? = null,

    @JsonProperty("content_type")
    val contentType: String? = null,

    @JsonProperty("custom_tag")
    val customTag: CustomTag? = null,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("downloadable")
    val downloadable: String? = null,

    @JsonProperty("genres")
    val genres: String? = null,

    @JsonProperty("id")
    val id: String = "",

    @JsonProperty("name")
    val name: String = "",

    @JsonProperty("poster")
    val poster: String? = null,

    @JsonProperty("release_date")
    val releaseDate: String? = null,

    @JsonProperty("runtime")
    val runtime: String? = null,

    @JsonProperty("status")
    val status: String? = null,

    @JsonProperty("type")
    val type: String? = null,

    @JsonProperty("youtube_trailer")
    val youtubeTrailer: String? = null,

    @JsonProperty("url")
    val url: String? = null,
)

data class CustomTag(

    @JsonProperty("background_color")
    val backgroundColor: String? = null,

    @JsonProperty("content_id")
    val contentId: String? = null,

    @JsonProperty("content_type")
    val contentType: String? = null,

    @JsonProperty("custom_tags_id")
    val customTagsId: String? = null,

    @JsonProperty("custom_tags_name")
    val customTagsName: String? = null,

    @JsonProperty("id")
    val id: String? = null,

    @JsonProperty("text_color")
    val textColor: String? = null,
)

data class StreamLinks(

    @JsonProperty("id")
    val id: String = "",

    @JsonProperty("name")
    val name: String = "",

    @JsonProperty("size")
    val size: String = "",

    @JsonProperty("quality")
    val quality: String = "",

    @JsonProperty("link_order")
    val linkOrder: String = "",

    @JsonProperty("movie_id")
    val movieId: String = "",

    @JsonProperty("url")
    val url: String = "",

    @JsonProperty("type")
    val type: String = "",

    @JsonProperty("status")
    val status: String = "",

    @JsonProperty("skip_available")
    val skipAvailable: String = "",

    @JsonProperty("intro_start")
    val introStart: String = "",

    @JsonProperty("intro_end")
    val introEnd: String = "",

    @JsonProperty("end_credits_marker")
    val endCreditsMarker: String = "",

    @JsonProperty("link_type")
    val linkType: String = "",

    @JsonProperty("drm_uuid")
    val drmUuid: String = "",

    @JsonProperty("drm_license_uri")
    val drmLicenseUri: String = "",
)

data class SeasonRes(

    @JsonProperty("id")
    val id: String = "",

    @JsonProperty("Session_Name")
    val sessionName: String = "",

    @JsonProperty("season_order")
    val seasonOrder: String = "",

    @JsonProperty("web_series_id")
    val webSeriesId: String = "",

    @JsonProperty("status")
    val status: String = "",
)

data class EpisodesRes(

    @JsonProperty("id")
    val id: String = "",

    @JsonProperty("Episoade_Name")
    val episoadeName: String = "",

    @JsonProperty("episoade_image")
    val episoadeImage: String = "",

    @JsonProperty("episoade_description")
    val episoadeDescription: String = "",

    @JsonProperty("episoade_order")
    val episoadeOrder: String = "",

    @JsonProperty("season_id")
    val seasonId: String = "",

    @JsonProperty("downloadable")
    val downloadable: String = "",

    @JsonProperty("type")
    val type: String = "",

    @JsonProperty("status")
    val status: String = "",

    @JsonProperty("source")
    val source: String = "",

    @JsonProperty("url")
    val url: String = "",

    @JsonProperty("skip_available")
    val skipAvailable: String = "",

    @JsonProperty("intro_start")
    val introStart: String = "",

    @JsonProperty("intro_end")
    val introEnd: String = "",

    @JsonProperty("end_credits_marker")
    val endCreditsMarker: String = "",

    @JsonProperty("drm_uuid")
    val drmUuid: String = "",

    @JsonProperty("drm_license_uri")
    val drmLicenseUri: String = "",
)

data class LoadDataObject(
    val id: String,
    val tmdbId: String?,
    val contentType: String?,
    val url: String?
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

private val SECRET_KEY = base64Decode(BuildConfig.iStreamFlareSecretKey)
private const val SALT = BuildConfig.iStreamFlareSalt

fun decryptPayload(encryptedBase64: String): String {
    val decoded = base64DecodeArray(encryptedBase64)
    require(decoded.size >= 28) { "Invalid encrypted payload" }

    val iv = decoded.copyOfRange(0, 12)
    val tag = decoded.copyOfRange(12, 28)
    val ciphertext = decoded.copyOfRange(28, decoded.size)

    val key = deriveKey()

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        key,
        GCMParameterSpec(128, iv)
    )

    // Java uses ciphertext || tag
    val plaintext = cipher.doFinal(ciphertext + tag)
    return String(plaintext, Charsets.UTF_8)
}

private fun deriveKey(): SecretKey {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(
        SECRET_KEY.toCharArray(),
        SALT.toByteArray(Charsets.UTF_8),
        10_000,
        256
    )
    return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
}

data class Response(
    val encrypted: Boolean,
    val data: String,
)
