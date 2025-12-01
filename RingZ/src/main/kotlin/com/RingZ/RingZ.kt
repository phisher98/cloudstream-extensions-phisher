package com.RingZ

import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class RingZ : MainAPI() {
    override var mainUrl = base64Decode("aHR0cHM6Ly9wcml2YXRlYXBpZGF0YS5wYWdlcy5kZXY=")
    override var name = "RingZ"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    companion object
    {
        val headers = mapOf(
            "cf-access-client-id" to base64Decode("ODMzMDQ5YjA4N2FjZjZlNzg3Y2VkZmQ4NWQxY2NkYjguYWNjZXNz"),
            "cf-access-client-secret" to base64Decode("MDJkYjI5NmE5NjFkNzUxM2MzMTAyZDc3ODVkZjQxMTNlZmYwMzZiMmQ1N2QwNjBmZmNjMmJhM2JhODIwYzZhYQ=="),
            "user-agent" to "Dart/3.8 (dart:io)"
        )

    }

    override val mainPage = runBlocking {
        val pages = RingzConfigLoader.fetchPages(
            base64Decode("aHR0cHM6Ly9tYWluLnJpbmd6YXBrLmlu"),
            mainUrl,
            "/t",
            settingsForProvider.enableAdult
        )

        mainPageOf(*pages.toTypedArray())
    }




    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data
        val responseString = app.get(url, headers).text
        val json = JSONObject(responseString)

        // Helper to get JSONArray safely
        fun getJsonArray(vararg keys: String): JSONArray {
            for (key in keys) {
                json.optJSONArray(key)?.let { return it }
            }
            return JSONArray()
        }

        // Helper to create LoadURL
        fun JSONObject.toLoadURL(type: String): LoadURL {
            return LoadURL(
                url = this.optString("l", url),
                title = this.optString("mn"),
                id = this.optString("id"),
                posterUrl = this.optString("IH"),
                trailer = this.optString("trailer").takeIf { it != "FALSE" },
                quality = this.optString("qlty").takeIf { it.isNotEmpty() },
                language = this.optString("lng"),
                type = type,
                category = this.optString("cg"),
                genre = this.optString("gn"),
                extra = this.toString()
            )
        }

        // Helper to create MovieSearchResponse list
        fun JSONArray.toSearchResponses(type: String, tvType: TvType, filterGenre: String? = null): List<MovieSearchResponse> {
            val list = mutableListOf<MovieSearchResponse>()
            for (i in 0 until this.length()) {
                val item = this.getJSONObject(i)
                if (filterGenre != null && !item.optString("gn").contains(filterGenre, ignoreCase = true)) continue

                val loadUrl = item.toLoadURL(type)
                list += newMovieSearchResponse(item.optString("mn"), loadUrl.toJson(), tvType) {
                    this.posterUrl = item.optString("IH")
                    if (type == "Movies") this.quality = getQualityFromString(item.optString("qlty").takeIf { it.isNotEmpty() })
                }
            }
            return list
        }

        return when {
            request.name.contains("Movies", ignoreCase = true) -> {
                val allMovies = getJsonArray("AllMovieDataList", "allMovieDataList")
                val searchResponses = allMovies.toSearchResponses("Movies", TvType.Movie)
                newHomePageResponse(
                    list = listOf(HomePageList(request.name.capitalize(), searchResponses, isHorizontalImages = true)),
                    hasNext = false
                )
            }

            request.name.contains("Anime", ignoreCase = true) -> {
                val animeList = getJsonArray("webSeriesDataList")
                val searchResponses = animeList.toSearchResponses("Anime", TvType.Anime, filterGenre = "Anime")
                newHomePageResponse(
                    list = listOf(HomePageList(request.name.capitalize(), searchResponses, isHorizontalImages = true)),
                    hasNext = false
                )
            }

            request.name.contains("desihub", ignoreCase = true) || request.name.contains("Webseries", ignoreCase = true)  -> {
                val webSeriesList = getJsonArray("webSeriesDataList")
                val searchResponses = webSeriesList.toSearchResponses("Series", TvType.TvSeries)
                newHomePageResponse(
                    list = listOf(HomePageList(request.name.capitalize(), searchResponses, isHorizontalImages = true)),
                    hasNext = false
                )
            }

            else -> {
                val allMovies = getJsonArray("AllMovieDataList", "allMovieDataList")
                val searchResponses = allMovies.toSearchResponses("Movies", TvType.Movie)
                newHomePageResponse(
                    list = listOf(HomePageList(request.name.capitalize(), searchResponses, isHorizontalImages = true)),
                    hasNext = false
                )
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val urls = RingzConfigLoader.fetchPages(
            "https://main.ringzapk.in",
            mainUrl,
            "/t",
            settingsForProvider.enableAdult
        )


        val results = mutableListOf<SearchResponse>()

        suspend fun fetchJson(url: String) = JSONObject(app.get(url, headers).text)

        // Helper to convert JSONObject to LoadURL
        fun JSONObject.toLoadURL(type: String, fallbackUrl: String): LoadURL {
            return LoadURL(
                url = this.optString("l", fallbackUrl),
                title = this.optString("mn"),
                id = this.optString("id"),
                posterUrl = this.optString("IH"),
                trailer = this.optString("trailer").takeIf { it != "FALSE" },
                quality = this.optString("qlty").takeIf { it.isNotEmpty() },
                language = this.optString("lng"),
                type = type,
                category = this.optString("cg"),
                genre = this.optString("gn"),
                extra = this.toString()
            )
        }

        fun JSONArray.toSearchResponses(type: String, tvType: TvType, filterGenre: String? = null,fallback: String? =null): List<SearchResponse> {
            val list = mutableListOf<SearchResponse>()
            for (i in 0 until this.length()) {
                val item = this.getJSONObject(i)

                val name = item.optString("mn")
                if (!name.contains(query, ignoreCase = true)) continue
                if (filterGenre != null && !item.optString("gn").contains(filterGenre, ignoreCase = true)) continue

                val loadUrl = item.toLoadURL(type, fallbackUrl = "$fallback")

                list += newMovieSearchResponse(name, loadUrl.toJson(), tvType) {
                    this.posterUrl = item.optString("IH")
                    if (tvType == TvType.Movie) this.quality = getQualityFromString(item.optString("qlty").takeIf { it.isNotEmpty() })
                }
            }
            return list
        }

        // Loop through all sources
        for ((url, type) in urls) {
            val json = fetchJson(url)
            val arrayKeys = listOf("AllMovieDataList", "allMovieDataList", "webSeriesDataList")
            val jsonArray = arrayKeys.firstNotNullOfOrNull { json.optJSONArray(it) } ?: JSONArray()

            val tvType = when {
                type.contains("Movies", ignoreCase = true) -> TvType.Movie
                type.contains("Anime", ignoreCase = true) -> TvType.Anime
                else -> TvType.TvSeries
            }

            results += jsonArray.toSearchResponses(type, tvType, filterGenre = if (type == "Anime") "Anime" else null,url)
        }

        return results
    }


    override suspend fun load(url: String): LoadResponse {
        val res = tryParseJson<LoadURL>(url) ?: throw ErrorLoadingException("Invalid URL JSON")
        val title = res.title ?: "Unknown Title"
        val href = res.url ?: throw ErrorLoadingException("URL missing")
        val poster = res.posterUrl
        val genre = res.genre?.split(",")?.map { it.trim() }

        val tvTag = when {
            res.type?.contains("Series", ignoreCase = true) == true -> TvType.TvSeries
            res.type?.contains("Anime", ignoreCase = true) == true -> TvType.Anime
            else -> TvType.Movie
        }

        // Helper to fetch JSON
        suspend fun fetchJson(fullUrl: String) = JSONObject(app.get(fullUrl, headers).text)

        // Helper to parse episodes from series/anime object
        fun parseEpisodes(seriesObj: JSONObject): List<Episode> {
            val episodeMap = mutableMapOf<String, MutableList<String>>()
            val keys = seriesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("eServer") || key == "eTape") {
                    val serverUrls = seriesObj.getJSONObject(key)
                    val epKeys = serverUrls.keys()
                    while (epKeys.hasNext()) {
                        val epNum = epKeys.next()
                        val epUrl = serverUrls.getString(epNum)
                        val urls = episodeMap.getOrPut(epNum) { mutableListOf() }
                        urls.add(epUrl)
                    }
                }
            }

            return episodeMap.entries.sortedBy { it.key.toInt() }.map { (epNum, urls) ->
                newEpisode(urls) {
                    name = "Episode $epNum"
                    episode = epNum.toInt()
                }
            }
        }

        return when (tvTag) {
            TvType.TvSeries, TvType.Anime -> {
                val seriesResText = fetchJson(if (href.contains(mainUrl)) href else "$mainUrl/$href")
                val webSeriesList = seriesResText.getJSONArray("webSeriesDataList")
                val seriesObj = (0 until webSeriesList.length())
                    .map { webSeriesList.getJSONObject(it) }
                    .firstOrNull { it.getString("id") == res.id }

                val allEpisodes = seriesObj?.let { parseEpisodes(it) } ?: emptyList()

                if (tvTag == TvType.TvSeries) {
                    newTvSeriesLoadResponse(title, url, TvType.TvSeries, allEpisodes) {
                        this.posterUrl = poster
                        this.tags = genre
                    }
                } else {
                    newAnimeLoadResponse(title, url, TvType.Anime) {
                        this.posterUrl = poster
                        this.tags = genre
                        addEpisodes(DubStatus.Subbed, allEpisodes)
                    }
                }
            }

            else -> {
                val movieResText = fetchJson("$mainUrl/$href")
                val allMovieDataList = movieResText.getJSONArray("AllMovieDataList")
                val movie = (0 until allMovieDataList.length())
                    .map { allMovieDataList.getJSONObject(it) }
                    .firstOrNull { it.optString("id") == res.id }

                if (movie != null) {
                    val links = buildList {
                        for (key in movie.keys()) {
                            val link = movie.optString(key)
                            if (link.startsWith("http") && key != "hf") add(link)
                        }
                    }
                    newMovieLoadResponse(movie.optString("mn"), url, TvType.Movie, links.toJson()) {
                        this.posterUrl = poster
                        this.tags = genre
                    }
                } else {
                    newMovieLoadResponse(title, url, TvType.Movie, url) {
                        this.posterUrl = poster
                        this.tags = genre
                    }
                }
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = JSONArray(data)
        for (i in 0 until urls.length()) {
            val url = urls.getString(i)
            val serverName = "Server ${i + 1}"
            callback.invoke(
                newExtractorLink(
                    serverName,
                    serverName,
                    url = url,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality= getQualityFromUrl(url)
                }
            )
        }

        return true
    }

    fun getQualityFromUrl(url: String): Int {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("2160") || lowerUrl.contains("4k") -> Qualities.P2160.value
            lowerUrl.contains("1080") -> Qualities.P1080.value
            lowerUrl.contains("720")  -> Qualities.P720.value
            lowerUrl.contains("480")  -> Qualities.P480.value
            lowerUrl.contains("360")  -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

}

