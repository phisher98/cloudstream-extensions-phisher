package com.MovieBlast

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

fun generateSignedUrl(url: String): String {
    return try {
        val path = URL(url).path
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val secret = base64Decode("R0o4cmV5ZGFySTdKcWF0OXJ2YkFKS05ROWdZNERvRVFGMkg1bmZ1STFnaQ==")
        val charset = StandardCharsets.UTF_8
        val key = SecretKeySpec(secret.toByteArray(charset), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val hash = mac.doFinal((path + timestamp).toByteArray(charset))
        var signature = base64Encode(hash)
        signature = URLEncoder.encode(signature, "UTF-8")
        "$url?verify=$timestamp-$signature"

    } catch (e: Exception) {
        throw RuntimeException("Error generating HMAC", e)
    }
}
