package com.IStreamFlare

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class IStreamFlare  : MainAPI() {
    override var mainUrl              = base64Decode("aHR0cHM6Ly9hZG1pbi5oaXBwaXR1bmVzLnBybw==")
    override var name                 = "IStreamFlare"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.TvSeries,TvType.Anime)

    companion object
    {
        val headers = mapOf(
            "User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 13; Subsystem for Android(TM) Build/TQ3A.230901.001)",
            "x-api-key" to base64Decode("a0M3VjFmOFFSYVp5dlluaA==")
        )

        private const val cinemeta_url = "https://v3-cinemeta.strem.io/meta"

    }

    override val mainPage = mainPageOf(
        "android/getTrending" to "Trending",
        "android/getMostWatched/Movies/page" to "Most Watched Movies",
        "android/getMostWatched/WebSeries/page" to "Most Watched Webseries",
        "android/getRecentContentList/Movies" to "Recently Added Movies",
        "android/getRecentContentList/WebSeries" to "Recently Added Webseries",
        "android/getRandWebSeries" to "Webseries",
        "android/getRandMovies" to "Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response=if (request.data.endsWith("page"))
        {
            val offset = (page - 1) * 10
            app.get("${mainUrl}/${request.data}".substringBefore("page") + offset, headers = headers).text
        }
        else
        {
            app.get("$mainUrl/${request.data}", headers = headers).text
        }

        val gson = Gson()

        val type = object : TypeToken<List<HomeRes>>() {}.type
        val homeList = try {
            gson.fromJson<List<HomeRes>>(response, type)
        } catch (e: Exception) {
            emptyList()
        }

        val searchResults = homeList.map { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = searchResults,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun HomeRes.toSearchResult(): SearchResponse {
        val title= this.name
        val posterUrl = this.poster
        val quality = this.customTag?.customTagsName ?: ""
        val loadData = LoadDataObject(
            id = this.id,
            tmdbId = this.tmdbId,
            contentType = this.contentType
        )
        return newMovieSearchResponse(title, loadData.toJson(), TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/android/searchContent/$query/1", headers).text
        val gson = Gson()

        val type = object : TypeToken<List<HomeRes>>() {}.type
        val homeList = try {
            gson.fromJson<List<HomeRes>>(response, type)
        } catch (e: Exception) {
            emptyList()
        }
        val results = homeList.map { it.toSearchResult() }
        return results
    }

    @Suppress("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val res = tryParseJson<LoadDataObject>(url)
            ?: throw ErrorLoadingException("Invalid URL JSON")

        val gson = Gson()
        val isMovie = res.contentType?.toIntOrNull() == 1
        val type = if (isMovie) "movie" else "series"
        val endpoint = if (isMovie) "getMovieDetails" else "getWebSeriesDetails"

        val rawJson = app.get("$mainUrl/android/$endpoint/${res.id}", headers).text
        val parsedObject = JsonParser.parseString(rawJson).asJsonObject
        val dataJson = parsedObject["data"] ?: parsedObject
        val href="$mainUrl/android/getMoviePlayLinks/${res.id}/0"

        val resJson = gson.fromJson(dataJson, HomeRes::class.java)
            ?: throw ErrorLoadingException("Failed to parse HomeRes")

        val imdbId = app.get(
            "https://thingproxy.freeboard.io/fetch/https://api.themoviedb.org/3/movie/${res.tmdbId}/external_ids?api_key=1865f43a0549ca50d341dd9ab8b29f49"
        ).parsedSafe<IMDB>()?.imdbId

        val responseData = imdbId
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?.let {
                val jsonResponse = app.get("$cinemeta_url/$type/$it.json").text
                if (jsonResponse.startsWith("{")) gson.fromJson(jsonResponse, ResponseData::class.java) else null
            }

        val meta = responseData?.meta
        val poster = meta?.background ?: resJson.poster
        val description = meta?.description ?: resJson.description
        val cast = meta?.cast ?: emptyList()
        val year = meta?.year?.toIntOrNull()

        if (!isMovie) {
            val episodesList = mutableListOf<Episode>()

            val seasonsJson = app.get("$mainUrl/android/getSeasons/${res.id}", headers).text
            val seasonListType = object : TypeToken<List<SeasonRes>>() {}.type
            val seasons: List<SeasonRes> = gson.fromJson(seasonsJson, seasonListType) ?: emptyList()

            seasons.forEach { season ->
                val seasonNumber = season.seasonOrder.toIntOrNull() ?: return@forEach

                val episodesJson = app.get("$mainUrl/android/getEpisodes/${season.id}/0", headers).text
                val episodeListType = object : TypeToken<List<EpisodesRes>>() {}.type
                val episodes: List<EpisodesRes> = gson.fromJson(episodesJson, episodeListType) ?: emptyList()

                episodes.forEach { episode ->
                    val episodeNumber = episode.episoadeOrder.toIntOrNull() ?: return@forEach

                    episodesList.add(
                        newEpisode(episode.url) {
                            name = episode.episoadeName.ifBlank { "Episode $episodeNumber" }
                            this.season = seasonNumber
                            this.episode = episodeNumber
                            this.posterUrl = episode.episoadeImage
                            this.description = episode.episoadeDescription
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(resJson.name, url, TvType.TvSeries, episodesList) {
                backgroundPosterUrl = poster
                posterUrl = poster
                plot = description
                this.year = year
                addTMDbId(resJson.tmdbId)
                addActors(cast)
            }
        }
        else
        return newMovieLoadResponse(resJson.name, url, TvType.Movie, href) {
            this.posterUrl = poster
            this.plot = description
            addTMDbId(resJson.tmdbId)
            addActors(cast)
            this.year = year
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.contains("getMoviePlayLinks"))
        {
            val responseText = app.get(data, headers).text

            val streamLinks: List<StreamLinks> = Gson().fromJson(
                responseText,
                object : TypeToken<List<StreamLinks>>() {}.type
            )

            streamLinks.forEach { link ->
                val type = when {
                    link.type.contains("M3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                    else -> INFER_TYPE
                }

                callback.invoke(
                    newExtractorLink(
                        name = link.name,
                        source = name,
                        url = link.url,
                        type = type
                    ) {
                        this.referer = ""
                        this.quality = getQualityFromName(link.quality)
                    }
                )
            }
        }
        else
        {
            callback.invoke(
                newExtractorLink(
                    name = name,
                    source = name,
                    url = data,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = getQualityFromName("")
                }
            )
        }
        return true
    }
}
