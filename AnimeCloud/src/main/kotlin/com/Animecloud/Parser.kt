package com.animecloud

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
    val slug: String,
    val title: String,
    val alternateTitles: String,
    val generes: List<String>,
    val imdb: String?,
    val tmdb: Long,
    val desc: String,
    val start: Long,
    val end: Long?,
    val poster: String,
    val voteAvg: Double,
    val voteCount: Long,
    val createdAt: String,
    val updatedAt: String,
    val lastSync: String,
    val tmdbType: String,
    val anilist: Long,
    val anilistSyncAttempts: Long,
    val anilistSuggestedScore: Long,
    val anilistReviewRequired: Boolean,
    val backdrop: String,
    val itemType: String,
    val blockSync: Boolean,
    val blockEpisodeSync: Boolean,
    val excludeFromAnilist: Boolean,
    val autoCache: Boolean,
    val takedownExpiresAt: String?,
    val takedownAuthOnly: Boolean?,
)

data class EpisodeParser(
    val data: Data,
    val status: Long,
)

data class Data(
    val id: Long,
    val slug: String,
    val title: String,
    val alternateTitles: String? = null,
    val generes: List<String> = emptyList(),
    val imdb: String? = null,
    val tmdb: Long? = null,
    val desc: String? = null,
    val start: Long? = null,
    val end: Long? = null,
    val poster: String? = null,
    val voteAvg: Double? = null,
    val voteCount: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastSync: String? = null,
    val tmdbType: String? = null,
    val anilist: Long? = null,
    val anilistSyncAttempts: Long? = null,
    val anilistSuggestedScore: Long? = null,
    val anilistReviewRequired: Boolean? = null,
    val backdrop: String? = null,
    val itemType: String? = null,
    val blockSync: Boolean? = null,
    val blockEpisodeSync: Boolean? = null,
    val excludeFromAnilist: Boolean? = null,
    val autoCache: Boolean? = null,
    val animeSeasons: List<AnimeSeason> = emptyList(),
)

data class AnimeSeason(
    val id: Long,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val season: String,
    val animeId: Long,
    val animeEpisodes: List<AnimeEpisode> = emptyList(),
)

data class AnimeEpisode(
    val id: Long,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val episode: String,
    val image: String? = null,
    val animeSeasonId: Long,
    val lastSync: String? = null,
)



data class Loadlinks(
    val data: LoadlinksData,
    val status: Long,
)

data class LoadlinksData(
    val id: Long,
    val episode: String,
    val animeSeasonId: Long,
    val hasGerSub: Boolean,
    val hasEngSub: Boolean,
    val hasGerDub: Boolean,
    val likeCount: Long,
    val animeEpisodeLinks: List<AnimeEpisodeLink>,
)

data class AnimeEpisodeLink(
    val id: Long,
    val createdAt: String,
    val updatedAt: String,
    val link: String,
    val lang: String,
    val animeEpisodeId: Long?,
    val name: String,
)

//SearchParser

data class SearchDaum(
    val data: List<Search>,
    val pages: Long,
    val status: Long,
)

data class Search(
    val id: Long,
    val slug: String,
    val title: String,
    val alternateTitles: String,
    val generes: List<String>,
    val imdb: String?,
    val tmdb: Long,
    val desc: String,
    val start: Long,
    val end: Long?,
    val poster: String,
    val voteAvg: Double,
    val voteCount: Long,
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
