package com.Animecloud

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor

data class AnimecloudEP(
    val data: Data,
    val status: Long,
)

data class Data(
    val id: Long,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    @JsonProperty("last_sync")
    val lastSync: String,
    val episode: String,
    val image: String,
    @JsonProperty("view_count")
    val viewCount: Long,
    @JsonProperty("anime_season_id")
    val animeSeasonId: Long,
    @JsonProperty("anime_episode_links")
    val animeEpisodeLinks: List<AnimeEpisodeLink>,
)

data class AnimeEpisodeLink(
    val id: Long,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    val link: String,
    val lang: String,
    val name: String,
    @JsonProperty("anime_episode_id")
    val animeEpisodeId: Long,
)


//SearchParser

data class SearchParser(
    val data: List<Daum>,
    val pages: Long,
    val status: Long,
)

data class Daum(
    val id: Long,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    @JsonProperty("last_sync")
    val lastSync: String,
    val slug: String,
    val title: String,
    @JsonProperty("alternate_titles")
    val alternateTitles: String,
    val generes: List<String>,
    val imdb: String?,
    val tmdb: Long,
    @JsonProperty("tmdb_type")
    val tmdbType: String,
    val anilist: Any?,
    val desc: String,
    val start: Long,
    val end: Long?,
    val poster: String,
    val backdrop: String,
    @JsonProperty("vote_avg")
    val voteAvg: Double,
    @JsonProperty("vote_count")
    val voteCount: Long,
    @JsonProperty("item_type")
    val itemType: String,
    @JsonProperty("anime_seasons")
    val animeSeasons: Any?,
)



suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: String? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        callback.invoke(
            ExtractorLink(
                source,
                source,
                link.url,
                link.referer,
                getQualityFromName(quality),
                link.type,
                link.headers,
                link.extractorData
            )
        )
    }
}
