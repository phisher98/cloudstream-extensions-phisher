package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.invokeExtractors
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.MalAnime
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Recommendations
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.BuildConfig

class MyAnimeList(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "MyAnimeList"
    override var mainUrl = "https://myanimelist.net"
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.MyAnimeList)
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val api = AccountManager.malApi
    private val apiUrl = "https://api.myanimelist.net/v2"
    private final val mediaLimit = 20
    private val auth = BuildConfig.MAL_API

    protected fun Any.toStringData(): String {
        return mapper.writeValueAsString(this)
    }

    private suspend fun malAPICall(query: String): MalApiResponse {
        val res =
                app.get(query, headers = mapOf("Authorization" to "Bearer $auth"))
                        .parsedSafe<MalApiResponse>()
                        ?: throw Exception("Unable to fetch content from API")
        return res
    }

    private suspend fun MalApiResponse.MalApiData.toSearchResponse(): SearchResponse {
        val url = "$mainUrl/${this.node.id}"
        val posterUrl = this.node.picture.large
        val res = newAnimeSearchResponse(this.node.title, url) { this.posterUrl = posterUrl }
        return res
    }

    private suspend fun Recommendations.toSearchResponse(): SearchResponse {
        val node = this.node ?: throw Exception("Unable to parse Recommendation")
        val url = "$mainUrl/${node.id}"
        val posterUrl = node.mainPicture?.large
        val res = newAnimeSearchResponse(node.title, url) { this.posterUrl = posterUrl }
        return res
    }

    override val mainPage =
            mainPageOf(
                    "$apiUrl/anime/ranking?ranking_type=all&limit=$mediaLimit&offset=" to
                            "Top Anime Series",
                    "$apiUrl/anime/ranking?ranking_type=airing&limit=$mediaLimit&offset=" to
                            "Top Airing Anime",
                    "$apiUrl/anime/ranking?ranking_type=bypopularity&limit=$mediaLimit&offset=" to
                            "Popular Anime",
                    "$apiUrl/anime/ranking?ranking_type=favorite&limit=$mediaLimit&offset=" to
                            "Top Favorited Anime",
                    "$apiUrl/anime/suggestions?limit=$mediaLimit&offset=" to "Suggestions",
                    "Personal" to "Personal"
            )

    override suspend fun search(query: String): List<SearchResponse>? {
        val res = malAPICall("$apiUrl/anime?q=$query&limit=$mediaLimit")
        return res.data?.map { it.toSearchResponse() }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.name.contains("Personal")) {
            // Reading and manipulating personal library
            api.loginInfo()
                    ?: return newHomePageResponse(
                            "Login required for personal content.",
                            emptyList<SearchResponse>(),
                            false
                    )
            var homePageList =
                    api.getPersonalLibrary().allLibraryLists.mapNotNull {
                        if (it.items.isEmpty()) return@mapNotNull null
                        val libraryName =
                                it.name.asString(plugin.activity ?: return@mapNotNull null)
                        HomePageList("${request.name}: $libraryName", it.items)
                    }
            return newHomePageResponse(homePageList, false)
        } else {
            val res = malAPICall("${request.data}${(page - 1) * mediaLimit}")
            val media =
                    res.data?.map { it.toSearchResponse() }
                            ?: return newHomePageResponse(request.name, emptyList(), false)
            return newHomePageResponse(request.name, media, true)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.removeSuffix("/").substringAfterLast("/")
        val data =
                app.get(
                                "$apiUrl/anime/$id?fields=id,title,synopsis,main_picture,start_season,num_episodes,recommendations,genres",
                                headers = mapOf("Authorization" to "Bearer $auth")
                        )
                        .parsedSafe<MalAnime>()
                        ?: throw ErrorLoadingException("Unable to fetch show details")
        val year = data.startSeason?.year
        val epCount = data.numEpisodes ?: 0
        val episodes =
                (1..epCount).map { i ->
                    val linkData =
                            LinkData(
                                            title = data.title,
                                            year = year,
                                            season = 1,
                                            episode = i,
                                            isAnime = true
                                    )
                                    .toStringData()
                    newEpisode(linkData)
                    {
                        this.season= 1
                        this.episode = i
                    }
                }
        return newAnimeLoadResponse(
                data.title ?: throw NotImplementedError("Unable to parse title"),
                url,
                TvType.Anime
        ) {
            this.year = data.startSeason?.year
            this.posterUrl = data.mainPicture?.large
            this.plot = data.synopsis
            this.tags = data.genres?.map { it.name }
            addMalId(id.toInt())
            addEpisodes(DubStatus.Subbed, episodes)
            this.recommendations = data.recommendations?.map { it.toSearchResponse() }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<LinkData>(data)
        invokeExtractors(Category.ANIME, mediaData, subtitleCallback, callback)
        return true
    }

    data class MalApiResponse(
            @JsonProperty("data") val data: Array<MalApiData>? = null,
    ) {
        data class MalApiData(
                @JsonProperty("node") val node: MalApiNode,
        ) {
            data class MalApiNode(
                    @JsonProperty("id") val id: Int,
                    @JsonProperty("title") val title: String,
                    @JsonProperty("main_picture") val picture: MalApiNodePicture
            ) {
                data class MalApiNodePicture(
                        @JsonProperty("medium") val medium: String,
                        @JsonProperty("large") val large: String,
                )
            }
        }
    }
}
