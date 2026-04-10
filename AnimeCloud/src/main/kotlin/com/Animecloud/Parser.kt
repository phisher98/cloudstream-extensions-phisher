package com.Animecloud

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Home(
    val data: List<HomeDaum>,
    val pages: Long,
    val status: Long,
)

data class HomeDaum(
    val id: Long,
    @JsonProperty("created_at")
    val createdAt: String?,
    @JsonProperty("updated_at")
    val updatedAt: String?,
    val slug: String,
    val title: String,
    @JsonProperty("alternate_titles")
    val alternateTitles: String?,
    val generes: Any?,
    val imdb: String?,
    val tmdb: Long,
    @JsonProperty("tmdb_type")
    val tmdbType: String,
    val anilist: Any?,
    val desc: String?,
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
    @JsonProperty("view_count")
    val viewCount: Long,
)

data class EpisodeParser(
    val data: Data?,
    val status: Long?,
)

data class Data(
    val id: Long?,
    @JsonProperty("created_at")
    val createdAt: String?,
    @JsonProperty("updated_at")
    val updatedAt: String?,
    @JsonProperty("last_sync")
    val lastSync: String?,
    val slug: String?,
    val title: String?,
    @JsonProperty("alternate_titles")
    val alternateTitles: String?,
    val generes: List<String>?,
    val imdb: String?,
    val tmdb: Long?,
    @JsonProperty("tmdb_type")
    val tmdbType: String?,
    val anilist: Any?,
    val desc: String?,
    val start: Long?,
    val end: Long?,
    val poster: String?,
    val backdrop: String?,
    @JsonProperty("vote_avg")
    val voteAvg: Double?,
    @JsonProperty("vote_count")
    val voteCount: Long?,
    @JsonProperty("item_type")
    val itemType: String?,
    @JsonProperty("anime_seasons")
    val animeSeasons: List<AnimeSeason>?,
)

data class AnimeSeason(
    val id: Long?,
    @JsonProperty("created_at")
    val createdAt: String?,
    @JsonProperty("updated_at")
    val updatedAt: String?,
    val season: String,
    @JsonProperty("anime_id")
    val animeId: Long?,
    @JsonProperty("anime_episodes")
    val animeEpisodes: List<AnimeEpisode>,
)

data class AnimeEpisode(
    val id: Long?,
    @JsonProperty("created_at")
    val createdAt: String?,
    @JsonProperty("updated_at")
    val updatedAt: String?,
    @JsonProperty("last_sync")
    val lastSync: String?,
    val episode: String?,
    val image: String?,
    @JsonProperty("view_count")
    val viewCount: Long?,
    @JsonProperty("anime_season_id")
    val animeSeasonId: Long?,
    @JsonProperty("has_ger_sub")
    val hasGerSub: Boolean?,
    @JsonProperty("has_ger_dub")
    val hasGerDub: Boolean?,
    @JsonProperty("has_eng_sub")
    val hasEngSub: Boolean?,
    @JsonProperty("anime_episode_links")
    val animeEpisodeLinks: Any?,
)




data class AnimecloudEP(
    val `data`: Data,
    val status: Int
) {
    data class Data(
        val anime_episode_links: List<AnimeEpisodeLink>,
        val anime_season_id: Int,
        val dislike_count: Int,
        val episode: String,
        val has_eng_sub: Boolean,
        val has_ger_dub: Boolean,
        val has_ger_sub: Boolean,
        val id: Int,
        val image: String,
        val like_count: Int,
        val view_count: Int
    ) {
        data class AnimeEpisodeLink(
            val anime_episode_id: Int,
            val created_at: String,
            val id: Int,
            val lang: String,
            val link: String,
            val name: String,
            val updated_at: String
        )
    }
}


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
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    source,
                    source,
                    url = link.url,
                    type = link.type
                ) {
                    this.referer = link.referer
                    this.quality = getQualityFromName(quality)
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}
