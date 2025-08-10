package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.invokeExtractors
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi.Companion.MediaObject
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi.Companion.getPosterUrl
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.BuildConfig

class Simkl(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "Simkl"
    override var mainUrl = "https://simkl.com"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Simkl)
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val api = AccountManager.simklApi
    private val apiUrl = "https://api.simkl.com"
    private final val mediaLimit = 20

    protected fun Any.toStringData(): String {
        return mapper.writeValueAsString(this)
    }

    private fun SimklMediaObject.toSearchResponse(): SearchResponse {
        val poster = getPosterUrl(poster ?: "")
        return newMovieSearchResponse(title, "$mainUrl/shows/${ids?.simkl}") {
            this.posterUrl = poster
        }
    }

    private suspend fun MainPageRequest.toSearchResponseList(
            page: Int
    ): Pair<List<SearchResponse>, Boolean> {
        val emptyData = emptyList<SearchResponse>() to false
        val res =
                app.get(this.data + page).parsedSafe<Array<SimklMediaObject>>() ?: return emptyData
        return res.map {
            newMovieSearchResponse("${it.title}", "$mainUrl/shows/${it.ids?.simkl2}") {
                this.posterUrl = getPosterUrl(it.poster.toString())
            }
        } to res.size.equals(mediaLimit)
    }

    private fun SimklMediaObject.toLinkData(): LinkData {
        return LinkData(
                simklId = ids?.simkl,
                imdbId = ids?.imdb,
                tmdbId = ids?.tmdb,
                aniId = ids?.anilist,
                malId = ids?.mal,
                title = title,
                year = year,
                type = type,
                isAnime = type.equals("anime")
        )
    }

    private fun SimklEpisodeObject.toLinkData(
            showName: String,
            ids: SimklIds?,
            year: Int?,
            isAnime: Boolean
    ): LinkData {
        return LinkData(
                simklId = ids?.simkl,
                imdbId = ids?.imdb,
                tmdbId = ids?.tmdb,
                aniId = ids?.anilist,
                malId = ids?.mal,
                title = showName,
                year = year,
                season = season,
                episode = episode,
                type = type,
                isAnime = isAnime
        )
    }

    private fun SimklEpisodeObject.toEpisode(
            showName: String,
            ids: SimklIds?,
            year: Int?,
            isAnime: Boolean
    ): Episode {
        val poster = "https://simkl.in/episodes/${img}_c.webp"
        val linkData = this.toLinkData(showName, ids, year, isAnime).toStringData()
        return newEpisode(linkData)
        {
            this.name = title
            this.description = desc
            this.posterUrl = poster
        }
    }

    // this method is added to tackle current API limitation of 100 req per day
    private fun MediaObject.toSimklMediaObject(): SimklMediaObject? {
        return parseJson<SimklMediaObject>(this.toStringData())
    }

    // this method is added to tackle current API limitation of 100 req per day
    private fun buildSimklEpisodes(total: Int?): Array<SimklEpisodeObject>? {
        if (total == null) return null
        var data = emptyArray<SimklEpisodeObject>()
        (1..total).forEach {
            data += SimklEpisodeObject(season = 1, episode = it, ids = null, type = "episode")
        }
        return data
    }

    override val mainPage =
            mainPageOf(
                    "$apiUrl/tv/trending/month?type=series&client_id=&extended=overview&limit=$mediaLimit&page=" to
                            "Trending TV Shows",
                    "$apiUrl/movies/trending/month?client_id=&extended=overview&limit=$mediaLimit&page=" to
                            "Trending Movies",
                    "$apiUrl/tv/best/all?type=series&client_id=&extended=overview&limit=$mediaLimit&page=" to
                            "Best TV Shows",
                    //"$apiUrl/movies/best/all?client_id=&extended=overview&limit=$mediaLimit&page=" to
                    //       "Best Movies",
                    "Personal" to "Personal"
            )

    override suspend fun search(query: String): List<SearchResponse>? {
        //return api.search(query)
        return null
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
            val homePageList =
                    api.getPersonalLibrary()?.allLibraryLists?.mapNotNull {
                        if (it.items.isEmpty()) return@mapNotNull null
                        val libraryName =
                                it.name.asString(plugin.activity ?: return@mapNotNull null)
                        HomePageList("${request.name}: $libraryName", it.items)
                    }
                            ?: return null
            return newHomePageResponse(homePageList, false)
        } else {
            // Other new sections will be generated if toSearchResponseList() is overridden
            val data = request.toSearchResponseList(page)
            return newHomePageResponse(request.name, data.first, data.second)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.removeSuffix("/").substringAfterLast("/")
        val data =
                app.get("$apiUrl/tv/$id?client_id=&extended=full")
                        .parsedSafe<SimklMediaObject>()
                        ?: throw ErrorLoadingException("Unable to load data")
        val year = data.year
        val posterUrl = getPosterUrl(data.poster ?: "")
        return if (data.type.equals("movie")) {
            val linkData = data.toLinkData().toStringData()
            newMovieLoadResponse(data.title, url, TvType.Movie, linkData) {
                this.addSimklId(id.toInt())
                this.year = year
                this.posterUrl = posterUrl
                this.plot = data.overview
                this.recommendations = data.recommendations?.map { it.toSearchResponse() }
            }
        } else {
            val eps =
                    app.get("$apiUrl/tv/episodes/$id?client_id=&extended=full")
                            .parsedSafe<Array<SimklEpisodeObject>>()
                            ?: buildSimklEpisodes(data.total_episodes)
                                    ?: throw Exception("Unable to fetch episodes")
            val episodes =
                    eps.filter { it.type.equals("episode") }.map {
                        it.toEpisode(data.title, data.ids, year, data.type.equals("anime"))
                    }
            newTvSeriesLoadResponse(data.title, url, TvType.TvSeries, episodes) {
                this.addSimklId(id.toInt())
                this.year = year
                this.posterUrl = posterUrl
                this.plot = data.overview
                this.recommendations = data.recommendations?.map { it.toSearchResponse() }
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = parseJson<LinkData>(data)
        if (mediaData.isAnime)
                invokeExtractors(Category.ANIME, mediaData, subtitleCallback, callback)
        else invokeExtractors(Category.MEDIA, mediaData, subtitleCallback, callback)
        return true
    }

    open class SimklMediaObject(
            @JsonProperty("title") val title: String,
            @JsonProperty("year") val year: Int? = null,
            @JsonProperty("ids") val ids: SimklIds?,
            @JsonProperty("total_episodes") val total_episodes: Int? = null,
            @JsonProperty("status") val status: String? = null,
            @JsonProperty("poster") val poster: String? = null,
            @JsonProperty("type") val type: String? = null,
            @JsonProperty("overview") val overview: String? = null,
            @JsonProperty("genres") val genres: List<String>? = null,
            @JsonProperty("users_recommendations")
            val recommendations: List<SimklMediaObject>? = null,
    )

    open class SimklEpisodeObject(
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("description") val desc: String? = null,
            @JsonProperty("season") val season: Int? = null,
            @JsonProperty("episode") val episode: Int? = null,
            @JsonProperty("type") val type: String? = null,
            @JsonProperty("aired") val aired: Boolean? = null,
            @JsonProperty("img") val img: String? = null,
            @JsonProperty("ids") val ids: SimklIds?,
    )

    data class SimklIds(
            @JsonProperty("simkl") val simkl: Int? = null,
            @JsonProperty("simkl_id") val simkl2: Int? = null,
            @JsonProperty("imdb") val imdb: String? = null,
            @JsonProperty("tmdb") val tmdb: Int? = null,
            @JsonProperty("mal") val mal: String? = null,
            @JsonProperty("anilist") val anilist: String? = null,
    )
}
