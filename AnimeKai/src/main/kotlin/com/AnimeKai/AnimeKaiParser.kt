package com.AnimeKai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.CoverImage
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.LikePageInfo
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.RecommendationConnection
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.SeasonNextAiringEpisode
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Title


@JsonIgnoreProperties(ignoreUnknown = true)
data class Image(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Episode(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("airDateUtc") val airDateUtc: String?,
    @JsonProperty("runtime") val runtime: Int?,     // Keeping only one field
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaMappings(
    @JsonProperty("themoviedb_id") val themoviedbId: String? = null,
    @JsonProperty("thetvdb_id") val thetvdbId: Int? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("mal_id") val malId: Int? = null,
    @JsonProperty("anilist_id") val anilistId: Int? = null,
    @JsonProperty("kitsu_id") val kitsuid: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @JsonProperty("titles") val titles: Map<String, String>?,
    @JsonProperty("images") val images: List<Image>?,
    @JsonProperty("episodes") val episodes: Map<String, Episode>?,
    @JsonProperty("mappings") val mappings: MetaMappings? = null
)

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}

private val apiUrl = "https://graphql.anilist.co"

private val headerJSON =
    mapOf("Accept" to "application/json", "Content-Type" to "application/json")
suspend fun anilistAPICall(query: String): AnilistAPIResponse {
    val data = mapOf("query" to query)
    val test = app.post(apiUrl, headers = headerJSON, data = data)
    val res =
        test.parsedSafe<AnilistAPIResponse>()
            ?: throw Exception("Unable to fetch or parse Anilist api response")
    return res
}

data class AnilistAPIResponse(
    @JsonProperty("data") val data: AnilistData,
) {
    data class AnilistData(
        @JsonProperty("Page") val page: AnilistPage?,
        @JsonProperty("Media") val media: anilistMedia?,
    ) {
        data class AnilistPage(
            @JsonProperty("pageInfo") val pageInfo: LikePageInfo,
            @JsonProperty("media") val media: List<Media>,
        )
    }

    data class anilistMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("startDate") val startDate: StartDate,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("title") val title: Title,
        @JsonProperty("season") val season: String?,
        @JsonProperty("genres") val genres: List<String>,
        @JsonProperty("averageScore") val averageScore: Int,
        @JsonProperty("status") val status: String,
        @JsonProperty("description") val description: String?,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("bannerImage") val bannerImage: String?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("airingSchedule") val airingSchedule: AiringScheduleNodes?,
        @JsonProperty("recommendations") val recommendations: RecommendationConnection?,
        @JsonProperty("externalLinks") val externalLinks: List<ExternalLink>?,
        @JsonProperty("format") val format: String?,
    ) {
        data class StartDate(@JsonProperty("year") val year: Int)

        data class AiringScheduleNodes(
            @JsonProperty("nodes") val nodes: List<SeasonNextAiringEpisode>?
        )

        data class ExternalLink(
            val site: String,
            val url: String
        )

        fun getCoverImage(): String? {
            return coverImage.extraLarge ?: coverImage.large ?: coverImage.medium
        }
    }

    data class Media(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("seasonYear") val seasonYear: Int,
        @JsonProperty("format") val format: String?,
        @JsonProperty("averageScore") val averageScore: Int,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("title") val title: Title,
        @JsonProperty("description") val description: String?,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("synonyms") val synonyms: List<String>,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )
}
