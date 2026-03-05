package com.IStreamFlare

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
    @SerializedName("TMDB_ID")
    val tmdbId: String?,

    @SerializedName("banner")
    val banner: String?,

    @SerializedName("content_type")
    val contentType: String,

    @SerializedName("custom_tag")
    val customTag: CustomTag?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("downloadable")
    val downloadable: String?,

    @SerializedName("genres")
    val genres: String?,

    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("poster")
    val poster: String?,

    @SerializedName("release_date")
    val releaseDate: String?,

    @SerializedName("runtime")
    val runtime: String?,

    @SerializedName("status")
    val status: String?,

    @SerializedName("type")
    val type: String?,

    @SerializedName("youtube_trailer")
    val youtubeTrailer: String?,
    @SerializedName("url") val url: String?,
)

data class CustomTag(
    @SerializedName("background_color")
    val backgroundColor: String?,

    @SerializedName("content_id")
    val contentId: String?,

    @SerializedName("content_type")
    val contentType: String?,

    @SerializedName("custom_tags_id")
    val customTagsId: String?,

    @SerializedName("custom_tags_name")
    val customTagsName: String?,

    @SerializedName("id")
    val id: String?,

    @SerializedName("text_color")
    val textColor: String?
)


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
