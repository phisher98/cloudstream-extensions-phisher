package com.hikaritv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.annotations.SerializedName

data class HomePage(
    val count: Long,
    val next: String,
    val previous: Any?,
    val results: List<Result>,
)

data class Result(
    val id: Long,
    val external: List<External>,
    val relations: Any?,
    @JsonProperty("ani_release")
    val aniRelease: Long,
    @JsonProperty("view_count")
    val viewCount: Long,
    @JsonProperty("view_count_month")
    val viewCountMonth: Long,
    @JsonProperty("view_count_years")
    val viewCountYears: Long,
    @JsonProperty("ani_score")
    val aniScore: Double?,
    val uid: String,
    @JsonProperty("ani_name")
    val aniName: String,
    @JsonProperty("ani_jname")
    val aniJname: String,
    @JsonProperty("ani_synonyms")
    val aniSynonyms: String,
    @JsonProperty("ani_genre")
    val aniGenre: String,
    @JsonProperty("ani_type")
    val aniType: Long,
    @JsonProperty("ani_country")
    val aniCountry: String,
    @JsonProperty("ani_stats")
    val aniStats: Long,
    @JsonProperty("ani_source")
    val aniSource: String,
    @JsonProperty("ani_ep")
    val aniEp: String,
    @JsonProperty("ani_synopsis")
    val aniSynopsis: String,
    @JsonProperty("ani_poster")
    val aniPoster: String,
    @JsonProperty("ani_release_season")
    val aniReleaseSeason: Long,
    @JsonProperty("ani_rate")
    val aniRate: String,
    @JsonProperty("ani_quality")
    val aniQuality: String,
    @JsonProperty("ani_time")
    val aniTime: String,
    @JsonProperty("ani_pv")
    val aniPv: String,
    @JsonProperty("ani_aired")
    val aniAired: String?,
    @JsonProperty("ani_aired_fin")
    val aniAiredFin: String?,
    @JsonProperty("ani_studio")
    val aniStudio: String,
    @JsonProperty("ani_producers")
    val aniProducers: String,
    @JsonProperty("ani_manga_url")
    val aniMangaUrl: String,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    @JsonProperty("ani_ename")
    val aniEname: String?,
)

data class External(
    val name: String,
    val url: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodeData(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("airDate") val airDate: String?,
    @JsonProperty("airDateUtc") val airDateUtc: String?,
    @JsonProperty("length") val length: Int?,
    @JsonProperty("runtime") val runtime: Int?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class AnimeData(
    @JsonProperty("titles") val titles: Map<String, String>?,
    @JsonProperty("images") val images: List<ImageData>?,
    @JsonProperty("episodes") val episodes: Map<String, EpisodeData>?
)

fun parseAnimeData(jsonString: String): AnimeData {
    val objectMapper = ObjectMapper()
    return objectMapper.readValue(jsonString, AnimeData::class.java)
}


data class Load(
    val id: Long,
    val external: List<External>,
    @JsonProperty("ani_release")
    val aniRelease: Int?,
    @JsonProperty("view_count")
    val viewCount: Long,
    @JsonProperty("view_count_month")
    val viewCountMonth: Long,
    @JsonProperty("view_count_years")
    val viewCountYears: Long,
    @JsonProperty("ani_score")
    val aniScore: Double,
    val uid: String,
    @JsonProperty("ani_name")
    val aniName: String,
    @JsonProperty("ani_jname")
    val aniJname: String,
    @JsonProperty("ani_synonyms")
    val aniSynonyms: String,
    @JsonProperty("ani_genre")
    val aniGenre: String,
    @JsonProperty("ani_type")
    val aniType: Int,
    @JsonProperty("ani_country")
    val aniCountry: String,
    @JsonProperty("ani_stats")
    val aniStats: Long,
    @JsonProperty("ani_source")
    val aniSource: String,
    @JsonProperty("ani_ep")
    val aniEp: String,
    @JsonProperty("ani_synopsis")
    val aniSynopsis: String,
    @JsonProperty("ani_poster")
    val aniPoster: String,
    @JsonProperty("ani_release_season")
    val aniReleaseSeason: Long,
    @JsonProperty("ani_rate")
    val aniRate: String,
    @JsonProperty("ani_quality")
    val aniQuality: String,
    @JsonProperty("ani_time")
    val aniTime: String,
    @JsonProperty("ani_pv")
    val aniPv: String,
    @JsonProperty("ani_aired")
    val aniAired: String,
    @JsonProperty("ani_aired_fin")
    val aniAiredFin: String,
    @JsonProperty("ani_studio")
    val aniStudio: String,
    @JsonProperty("ani_producers")
    val aniProducers: String,
    @JsonProperty("ani_manga_url")
    val aniMangaUrl: String,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    @JsonProperty("ani_ename")
    val aniEname: String,
)

data class EpisodeItem(
    val ep_id_name: String,
    val ep_name: String
)

data class EmbedData(
    @SerializedName("embed_type") val embedType: String,
    @SerializedName("embed_name") val embedName: String,
    @SerializedName("embed_frame") val embedFrame: String
)
