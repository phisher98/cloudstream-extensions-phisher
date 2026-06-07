package com.anineko

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListSearchResponse(@param:JsonProperty("data") val data: AniListData? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListData(@param:JsonProperty("Media") val Media: AniListMedia? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListMedia(@param:JsonProperty("id") val id: Int? = null)

suspend fun getAnilistId(title: String): Int? {
    return try {
        val query = """
            query(${'$'}search: String) {
                Media(search: ${'$'}search, type: ANIME) {
                    id
                }
            }
        """.trimIndent()

        val requestData = mapOf(
            "query" to query,
            "variables" to mapOf("search" to title)
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json")

        val res = app.post(
            "https://graphql.anilist.co",
            headers = headers,
            requestBody = requestData
        ).parsedSafe<AniListSearchResponse>()

        res?.data?.Media?.id
    } catch (e: Exception) {
        null
    }
}

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        parseJson<MetaAnimeData>(jsonString)
    } catch (_: Exception) {
        null
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @param:JsonProperty("coverType") val coverType: String? = null,
    @param:JsonProperty("url") val url: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisode(
    @param:JsonProperty("episode") val episode: String? = null,
    @param:JsonProperty("airdate") val airdate: String? = null,
    @param:JsonProperty("airDateUtc") val airDateUtc: String? = null,
    @param:JsonProperty("length") val length: Int? = null,
    @param:JsonProperty("runtime") val runtime: Int? = null,
    @param:JsonProperty("image") val image: String? = null,
    @param:JsonProperty("title") val title: Map<String, String?>? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("rating") val rating: String? = null,
    @param:JsonProperty("finaleType") val finaleType: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @param:JsonProperty("titles") val titles: Map<String, String?>? = null,
    @param:JsonProperty("images") val images: List<ImageData>? = null,
    @param:JsonProperty("episodes") val episodes: Map<String, MetaEpisode>? = null,
)
