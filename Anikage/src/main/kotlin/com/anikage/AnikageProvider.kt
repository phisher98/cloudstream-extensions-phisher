package com.anikage


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AnikageProvider : MainAPI() {
    override var mainUrl = "https://anikage.cc"
    override var name = "Anikage"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    override val mainPage = mainPageOf(
        "$mainUrl/api/media/anime/advanced-search?sort=trending&per_page=25&include_adult=true&page=" to "Trending",
        "$mainUrl/api/media/anime/advanced-search?sort=popularity&per_page=25&page=" to "Popular",
        "$mainUrl/api/media/anime/advanced-search?sort=popularity&per_page=25&include_adult=true&formats=MOVIE&page=" to "Popular Movies",
        "$mainUrl/api/media/anime/advanced-search?sort=popularity&per_page=25&include_adult=true&formats=OVA&page=" to "Popular OVAs",
        "$mainUrl/api/media/anime/advanced-search?sort=updated&per_page=25&page=" to "Latest Updates"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val responseText = app.get(url).text
        val parsed = parseJson<AnikageResponse>(responseText)

        val home = parsed.results.map { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
            ),
            hasNext = parsed.hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/media/anime/advanced-search?per_page=25&page=1&query=$query"
        val responseText = app.get(url).text
        val parsed = parseJson<AnikageResponse>(responseText)

        return parsed.results.map { it.toSearchResponse() }
    }

    private fun AnimeResult.toSearchResponse(): AnimeSearchResponse {
        val titleName = title.english ?: title.romaji
        return newAnimeSearchResponse(titleName, slug) {
            this.posterUrl = coverImage.extraLarge ?: coverImage.large
            this.year = this@toSearchResponse.year
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.substringAfterLast("/")
        
        val episodesUrl = "$mainUrl/api/media/anime/$slug/episodes"
        val episodesText = app.get(episodesUrl).text
        val episodesList = try {
            parseJson<List<EpisodeResult>>(episodesText)
        } catch (_: Exception) {
            emptyList()
        }

        val infoUrl = "$mainUrl/api/media/anime/$slug"
        val infoParsed = parseJson<AnimeDetailsResponse>(app.get(infoUrl).text)
        val animeInfo = infoParsed.anime

        val titleName = animeInfo.title.english ?: animeInfo.title.romaji
        
        val tvType = if (animeInfo.type.contains("movie", true)) TvType.AnimeMovie else TvType.Anime

        return newAnimeLoadResponse(titleName, url, tvType) {
            this.posterUrl = animeInfo.coverImage.extraLarge ?: animeInfo.coverImage.large
            this.backgroundPosterUrl = animeInfo.bannerImage
            this.year = animeInfo.year
            
            animeInfo.anilistId?.let { addAniListId(it) }
            animeInfo.idMal?.let { addMalId(it) }

            this.plot = animeInfo.description?.replace(Regex("<.*?>"), "") ?: "Format: ${animeInfo.format}"
            this.showStatus = when (animeInfo.status) {
                "FINISHED" -> ShowStatus.Completed
                "RELEASING" -> ShowStatus.Ongoing
                else -> null
            }
            this.tags = animeInfo.genres

            val subEpisodes = episodesList.map { ep ->
                newEpisode(EpisodeData(slug, ep.number, false).toJson()) {
                    this.episode = ep.number
                    this.name = ep.title ?: "Episode ${ep.number}"
                    this.posterUrl = ep.img
                    this.description = ep.description
                    ep.rating?.let { this.score = Score.from10(it.toString()) }
                    this.addDate(ep.airDate)
                }
            }
            addEpisodes(DubStatus.Subbed, subEpisodes)

            val dubEpisodes = episodesList.map { ep ->
                newEpisode(EpisodeData(slug, ep.number, true).toJson()) {
                    this.episode = ep.number
                    this.name = ep.title ?: "Episode ${ep.number}"
                    this.posterUrl = ep.img
                    this.description = ep.description
                    ep.rating?.let { this.score = Score.from10(it.toString()) }
                    this.addDate(ep.airDate)
                }
            }
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val epData = parseJson<EpisodeData>(data)
        
        val langs = if (epData.isDub) listOf("dub") else listOf("sub")
        val lang = langs.first()
        
        val serversUrl = "$mainUrl/api/media/anime/${epData.slug}/episodes/${epData.number}/servers?lang=$lang"
        val serversData = try {
            parseJson<List<ServerData>>(app.get(serversUrl).text)
        } catch (_: Exception) {
            emptyList()
        }
        val providers = serversData.amap { it.id }.takeIf { it.isNotEmpty() } ?: listOf("megg", "miko", "anya", "verse", "neko")

        providers.amap { provider ->
            async {
                val sourceUrl = "$mainUrl/api/media/anime/${epData.slug}/episodes/${epData.number}/sources?lang=$lang&provider=$provider"
                    try {
                        val res = app.get(sourceUrl).text
                        val sourceData = parseJson<EpisodeSource>(res)
                        
                        sourceData.subtitles?.amap { sub ->
                            subtitleCallback(newSubtitleFile(sub.label ?: lang, "https://prox.anikage.cc/vtt/${sub.file}"))
                        }

                        val subType = if (lang == "sub") {
                            if (!sourceData.subtitles.isNullOrEmpty()) "Softsub" else "Hardsub"
                        } else ""
                        val baseNameStr = "Anikage ${provider.replaceFirstChar { it.uppercase() }} $subType".trim()

                        sourceData.sources?.amap { src ->
                            val isM3u8 = src.isM3U8 == true
                            val videoUrl = "https://prox.anikage.cc/${if(isM3u8) "m3u8" else "stream"}/${src.url}"
                            
                            val nameStr = "$baseNameStr ${src.quality?.replaceFirstChar { it.uppercase() } ?: ""}".trim()

                            callback(
                                newExtractorLink(
                                    "Anikage",
                                    nameStr,
                                    videoUrl,
                                    if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = getQualityFromName(src.quality)
                                    this.referer = "$mainUrl/"
                                }
                            )
                        }
                        
                        sourceData.embeds?.amap { embed ->
                            loadExtractor(embed.url, "$mainUrl/", subtitleCallback, callback)
                        }
                    } catch (_: Exception) {
                        // Ignore failed providers
                    }
                }
        }.awaitAll()
        
        true
    }

    private fun getQualityFromName(quality: String?): Int {
        return when {
            quality?.contains("1080") == true -> Qualities.P1080.value
            quality?.contains("720") == true -> Qualities.P720.value
            quality?.contains("480") == true -> Qualities.P480.value
            quality?.contains("360") == true -> Qualities.P360.value
            else -> Qualities.P1080.value
        }
    }

    data class EpisodeData(val slug: String, val number: Int, val isDub: Boolean = false)

    data class AnikageResponse(
        val hasNextPage: Boolean,
        val results: List<AnimeResult>
    )

    data class AnimeDetailsResponse(
        val anime: AnimeResult
    )

    data class AnimeResult(
        val slug: String,
        val title: AnimeTitle,
        val coverImage: CoverImage,
        val type: String,
        val format: String,
        val status: String,
        val genres: List<String>?,
        val year: Int?,
        val description: String?,
        val anilistId: Int?,
        val idMal: Int?,
        val bannerImage: String?
    )

    data class AnimeTitle(
        val romaji: String,
        val english: String?
    )

    data class CoverImage(
        val large: String?,
        val extraLarge: String?
    )

    data class EpisodeResult(
        val number: Int,
        val title: String?,
        val description: String?,
        val img: String?,
        val rating: Double?,
        val airDate: String?
    )

    data class EpisodeSource(
        val sources: List<SourceData>?,
        val subtitles: List<SubtitleData>?,
        val embeds: List<EmbedData>?
    )

    data class ServerData(val id: String)

    data class EmbedData(
        val url: String,
        val type: String?,
        val server: String?
    )

    data class SourceData(
        val url: String,
        val quality: String?,
        val isM3U8: Boolean?
    )

    data class SubtitleData(
        val file: String,
        val label: String?
    )
}
