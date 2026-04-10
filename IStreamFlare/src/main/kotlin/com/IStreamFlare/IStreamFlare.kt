package com.IStreamFlare

import com.IStreamFlare.IStreamFlare.Companion.headers
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.BuildConfig
import org.json.JSONObject

class IStreamFlare  : MainAPI() {
    override var mainUrl = BuildConfig.iStreamFlare
    override var name = "IStreamFlare"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object {
        val headers = mapOf(
            "User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 13; Subsystem for Android(TM) Build/TQ3A.230901.001)",
            "x-api-key" to "kC7V1f8QRaZyvYnh"
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
        "android/getAllLiveTV" to "TV Channels"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val isLiveTv = request.data.contains("android/getAllLiveTV", ignoreCase = true)

        val url = if (request.data.endsWith("page")) {
            val offset = (page - 1) * 10
            "${mainUrl}/${request.data.removeSuffix("page")}$offset"
        } else {
            "$mainUrl/${request.data}"
        }

        val root = app.get(url, headers = headers).parsedSafe<Response>() ?: return newHomePageResponse(emptyList())

        val jsonData = if (root.encrypted) {
            decryptPayload(root.data)
        } else {
            root.data
        }

        val gson = Gson()
        val type = object : TypeToken<List<HomeRes>>() {}.type

        val homeList: List<HomeRes> = try {
            gson.fromJson(jsonData, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val searchResults = homeList.map { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = searchResults,
                isHorizontalImages = isLiveTv
            ),
            hasNext = searchResults.isNotEmpty()
        )
    }


    private fun HomeRes.toSearchResult(): SearchResponse {
        val title = this.name
        val posterUrl = this.poster ?: this.banner
        val quality = this.customTag?.customTagsName?.substringBefore("+") ?: ""
        val loadData = LoadDataObject(
            id = this.id,
            tmdbId = this.tmdbId,
            contentType = this.contentType,
            url = this.url
        )
        return newMovieSearchResponse(title, loadData.toJson(), TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/android/searchContent/$query/1"
        val raw = app.get(url, headers).text
        val outer = JSONObject(raw)
        val jsonString = if (outer.optBoolean("encrypted")) {
            getDecodedJson(url)
        } else {
            raw
        }

        val gson = Gson()
        val type = object : TypeToken<List<HomeRes>>() {}.type

        val homeList: List<HomeRes> = try {
            gson.fromJson(jsonString, type)
        } catch (_: Exception) {
            emptyList()
        }
        return homeList.map { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = tryParseJson<LoadDataObject>(url)
            ?: throw ErrorLoadingException("Invalid URL JSON")

        val gson = Gson()
        fun parseJsonSafe(input: String?): JsonElement? {
            if (input.isNullOrBlank()) return null
            val clean = input.trim()
            if (!clean.startsWith("{") && !clean.startsWith("[")) return null
            return runCatching { JsonParser.parseString(clean) }.getOrNull()
        }
        if (res.contentType?.toIntOrNull() == 3 && !res.url.isNullOrBlank()) {
            return newLiveStreamLoadResponse(
                name = "Live TV",
                url = res.url,
                res.url
            ) {
                this.posterUrl = null
            }
        }
        val isMovie = res.contentType?.toIntOrNull() == 1
        val type = if (isMovie) "movie" else "series"
        val endpoint = if (isMovie) "getMovieDetails" else "getWebSeriesDetails"

        // ---------------- MAIN DETAILS ----------------

        val rawJson = getDecodedJson("$mainUrl/android/$endpoint/${res.id}")
        val parsedElement = parseJsonSafe(rawJson)
            ?: throw ErrorLoadingException("Invalid JSON response")

        val dataElement = if (
            parsedElement.isJsonObject &&
            parsedElement.asJsonObject.has("data")
        ) parsedElement.asJsonObject["data"]
        else parsedElement

        val resJson = runCatching {
            gson.fromJson(dataElement, HomeRes::class.java)
        }.getOrNull() ?: throw ErrorLoadingException("Failed to parse HomeRes")


        val imdbId = runCatching {
            val response = app.get(
                "https://api.themoviedb.org/3/movie/${res.tmdbId}/external_ids?api_key=1865f43a0549ca50d341dd9ab8b29f49"
            ).text

            val jsonObject = JSONObject(response)
            jsonObject.optString("imdb_id","").takeIf { it.isNotBlank() && it.startsWith("tt") }
        }.getOrNull()


        val responseData = imdbId
            ?.takeIf { it.isNotBlank()}
            ?.let {
                runCatching {
                    val json = app.get("$cinemeta_url/$type/$it.json").text
                    parseJsonSafe(json)?.let {
                        gson.fromJson(it, ResponseData::class.java)
                    }
                }.getOrNull()
            }

        val meta = responseData?.meta
        val poster = meta?.background ?: resJson.poster
        val description = meta?.description ?: resJson.description
        val cast = meta?.cast ?: emptyList()
        val year = meta?.year?.toIntOrNull()


        if (!isMovie) {

            val episodesList = mutableListOf<Episode>()

            val seasonsRaw = getDecodedJson("$mainUrl/android/getSeasons/${res.id}")
            val seasonsElement = parseJsonSafe(seasonsRaw)

            val seasons: List<SeasonRes> =
                if (seasonsElement?.isJsonArray == true)
                    gson.fromJson(
                        seasonsElement,
                        object : TypeToken<List<SeasonRes>>() {}.type
                    )
                else emptyList()

            seasons.forEach { season ->
                if (season.status.toIntOrNull() != 0) {

                    val seasonNumber =
                        Regex("""(\d+)""")
                            .find(season.sessionName)
                            ?.value
                            ?.toIntOrNull() ?: 1

                    val episodesRaw =
                        getDecodedJson("$mainUrl/android/getEpisodes/${season.id}/0")

                    val episodesElement = parseJsonSafe(episodesRaw)

                    val episodes: List<EpisodesRes> =
                        if (episodesElement?.isJsonArray == true)
                            gson.fromJson(
                                episodesElement,
                                object : TypeToken<List<EpisodesRes>>() {}.type
                            )
                        else emptyList()

                    episodes.forEach { episode ->
                        val epNumber = episode.episoadeOrder.toIntOrNull() ?: return@forEach
                        if (episode.url.isNotBlank()) {
                            episodesList.add(
                                newEpisode(episode.url) {
                                    name = episode.episoadeName.ifBlank {
                                        "Episode $epNumber"
                                    }
                                    this.season = seasonNumber
                                    this.episode = epNumber
                                    this.posterUrl = episode.episoadeImage
                                    this.description = episode.episoadeDescription
                                }
                            )
                        }
                    }
                }
            }

            return newTvSeriesLoadResponse(
                resJson.name,
                url,
                TvType.TvSeries,
                episodesList
            ) {
                backgroundPosterUrl = poster
                posterUrl = poster
                plot = description
                this.year = year
                addTMDbId(resJson.tmdbId)
                addActors(cast)
            }
        }

        val href = "$mainUrl/android/getMoviePlayLinks/${res.id}/0"

        return newMovieLoadResponse(
            resJson.name,
            url,
            TvType.Movie,
            href
        ) {
            posterUrl = poster
            plot = description
            this.year = year
            addTMDbId(resJson.tmdbId)
            addActors(cast)
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return when {
            data.contains("getMoviePlayLinks", ignoreCase = true) -> {
                val decodedJson = getDecodedJson(data)
                val element = JsonParser.parseString(decodedJson)

                val links: List<StreamLinks> =
                    if (element.isJsonArray)
                        Gson().fromJson(
                            element,
                            object : TypeToken<List<StreamLinks>>() {}.type
                        )
                    else emptyList()

                links.forEach { link ->
                    when {
                        link.url.contains(".php?id=", ignoreCase = true) -> {
                            loadExtractor(link.url, link.quality, subtitleCallback, callback)
                        }

                        else -> {
                            val type = if (link.url.contains(".m3u8", true)) {
                                ExtractorLinkType.M3U8
                            } else {
                                INFER_TYPE
                            }

                            callback.invoke(
                                newExtractorLink(
                                    name = link.name,
                                    source = this.name,
                                    url = link.url,
                                    type = type
                                ) {
                                    referer = ""
                                    quality = getQualityFromName(link.quality)
                                }
                            )
                        }
                    }
                }
                true
            }

            // =========================
            // DIRECT LINKS / EXTRACTORS
            // =========================
            else -> {
                when {

                    data.contains("x7flix", ignoreCase = true) -> {
                        callback.invoke(
                            newExtractorLink(
                                name = name,
                                source = this.name,
                                url = data,
                                type = ExtractorLinkType.M3U8
                            ) {
                                referer = data
                                quality = getQualityFromName("")
                            }
                        )
                    }

                    data.contains(".php?id=", ignoreCase = true) -> {
                        loadExtractor(data, subtitleCallback, callback)
                    }

                    else -> {
                        val type = if (data.contains(".m3u8", true)) {
                            ExtractorLinkType.M3U8
                        } else {
                            INFER_TYPE
                        }

                        callback.invoke(
                            newExtractorLink(
                                name = name,
                                source = this.name,
                                url = data,
                                type = type
                            ) {
                                referer = ""
                                quality = getQualityFromName("")
                            }
                        )
                    }
                }
                true
            }
        }
    }
}


    private suspend fun getDecodedJson(url: String): String {
    val response = app.get(url, headers).text

    return try {
        val root = JsonParser.parseString(response).asJsonObject
        if (root.has("encrypted") && root["encrypted"].asBoolean && root.has("data")) {
            decryptPayload(root["data"].asString)
        } else {
            response
        }
    } catch (_: Exception) {
        response
    }
}