package com.MPlayer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MPlayer : MainAPI() {
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
    )
    override var lang = "hi"
    override var mainUrl = "https://www.mxplayer.in"
    override var name = "M Player"
    override val hasMainPage = true
    private var imageUrl="https://qqcdnpictest.mxplay.com/"
    private var userID: String? = null
    private val webApi = "https://api.mxplayer.in/v1/web"
    private val endpointurl="https://d3sgzbosmwirao.cloudfront.net/"
    private val endParam
        get() = "&device-density=2&userid=$userID&platform=com.mxplay.desktop&content-languages=hi,en&kids-mode-enabled=false"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get(mainUrl)
        userID = res.okhttpResponse.headers.getCookies()["UserID"]
            ?: throw ErrorLoadingException("load fail, geo blocked")

        val dramaResponse = app.get(
            "$webApi/detail/browseItem?&pageNum=$page&pageSize=20&isCustomized=true&genreFilterIds=48efa872f6f17facebf6149dfc536ee1&type=2$endParam",
            referer = "$mainUrl/"
        ).toString()
        val dramaRoot: MXPlayer = Gson().fromJson(dramaResponse, object : TypeToken<MXPlayer>() {}.type)
        val dramashows = dramaRoot.items.map { item ->
            item.toSearchResult()
        }

        val crimeResponse = app.get(
            "$webApi/detail/browseItem?&pageNum=1&pageSize=20&isCustomized=true&genreFilterIds=b413dff55bdad743c577a8bea3b65044&type=2$endParam",
            referer = "$mainUrl/"
        ).toString()
        val crimeRoot: MXPlayer = Gson().fromJson(crimeResponse, object : TypeToken<MXPlayer>() {}.type)
        val crime_shows = crimeRoot.items.map { item ->
            item.toSearchResult()
        }

        val thrillerResponse = app.get(
            "$webApi/detail/browseItem?&pageNum=1&pageSize=20&isCustomized=true&genreFilterIds=2dd5daf25be5619543524f360c73c3d8&type=2$endParam",
            referer = "$mainUrl/"
        ).toString()
        val thrillerRoot: MXPlayer = Gson().fromJson(thrillerResponse, object : TypeToken<MXPlayer>() {}.type)
        val thriller_shows = thrillerRoot.items.map { item ->
            item.toSearchResult()
        }

        val hindimovieresponse = app.get(
            "$webApi/detail/browseItem?&pageNum=$page&pageSize=20&isCustomized=true&browseLangFilterIds=hi&type=1$endParam",
            referer = "$mainUrl/").toString()
        val movieRoot: MovieRoot = Gson().fromJson(hindimovieresponse, object : TypeToken<MovieRoot>() {}.type)
        val hindi_Movies = movieRoot.items.map { item ->
            item.toSearchResult()
        }

        val telgumovieresponse = app.get(
            "$webApi/detail/browseItem?&pageNum=$page&pageSize=20&isCustomized=true&browseLangFilterIds=te&type=1$endParam",
            referer = "$mainUrl/").toString()
        val movieRootte: MovieRoot = Gson().fromJson(telgumovieresponse, object : TypeToken<MovieRoot>() {}.type)
        val telgu_Movies = movieRootte.items.map { item ->
            item.toSearchResult()
        }
        val Dramashows = HomePageList("Drama Shows", dramashows)
        val HindiMovies = HomePageList("Hindi Movies", hindi_Movies)
        val telguMovies = HomePageList("Telgu Movies", telgu_Movies)
        val crimeshows = HomePageList("Crime Shows", crime_shows)
        val thrillershows = HomePageList("Thriller Shows", thriller_shows)

        return newHomePageResponse(listOf(crimeshows,Dramashows,thrillershows,HindiMovies,telguMovies))
    }

    //Movie classes
    private fun MovieItem.toSearchResult(): SearchResponse {
        val portraitLargeImageUrl = getPortraitLargeImageUrl(this)
        val bigpic=getMBigPic(this)
        return newMovieSearchResponse(title, LoadUrl(this.title, this.titleContentImageInfo,bigpic,this.type,this.stream,this.description,this.shareUrl,null).toJson()) {
            posterUrl = portraitLargeImageUrl
        }
    }

    private fun Item.toSearchResult(): SearchResponse {
        val portraitLargeImageUrl = getPortraitLargeImageUrl(this)
        return newMovieSearchResponse(title, LoadUrl(this.title, this.titleContentImageInfo,null,this.type,null,this.description,this.shareUrl,null).toJson()) {
            posterUrl = portraitLargeImageUrl
        }
    }

    fun getPortraitLargeImageUrl(item: MovieItem): String? {
        return item.imageInfo
            .firstOrNull { it.type == "portrait_large" }
            ?.url?.let { imageUrl + it }
    }


    fun getPortraitLargeImageUrl(item: Item): String? {
        return item.imageInfo
            .firstOrNull { it.type == "portrait_large" }
            ?.url?.let { imageUrl + it }
    }

    fun getMovieBigPic(jsonString: String): String? {
        val gson = Gson()
        val item = gson.fromJson(jsonString, MovieItem::class.java)
        val bigPicUrl = item.titleContentImageInfo?.firstOrNull { it.type == "banner_and_static_bg_desktop" }?.url
        return bigPicUrl?.let { "$imageUrl$it" }
    }

    fun getMBigPic(item: MovieItem): String? {
        return item.imageInfo
            .firstOrNull { it.type == "bigpic" }
            ?.url?.let { imageUrl + it }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val gson = Gson()
        val response = app.post(
            "$webApi/search/resultv2?query=$query$endParam",
            referer = "$mainUrl/",
            requestBody = "{}".toRequestBody("application/json".toMediaType())
        ).body.string()
        val searchResult = gson.fromJson(response, SearchResult::class.java)
        val result = mutableListOf<SearchResponse>()
        searchResult.sections.forEach { section ->
            section.items.forEach { item ->
                if (item.type.contains("movie", ignoreCase = true)) {
                    val portraitLargeImageUrl = getBigPic(item)
                    val streamUrl: String = item.stream?.hls?.high?.let { endpointurl + it }
                        ?: item.stream?.thirdParty?.hlsUrl
                        ?: item.stream?.thirdParty?.dashUrl
                        ?: ""
                    result.add(newMovieSearchResponse(item.title, LoadUrl(item.title, item.titleContentImageInfo,null, item.type, null, item.description, item.shareUrl,streamUrl,portraitLargeImageUrl).toJson()) {
                        posterUrl = portraitLargeImageUrl
                    })
                } else {
                    val portraitLargeImageUrl = getBigPic(item)
                    result.add(newMovieSearchResponse(item.title, LoadUrl(item.title, item.titleContentImageInfo,null, item.type, null, item.description, item.shareUrl,null,portraitLargeImageUrl).toJson()) {
                        posterUrl = portraitLargeImageUrl
                    })
                }
            }
        }

        return result
    }

    fun getBigPic(item: Item): String? {
        return item.imageInfo
            .firstOrNull { it.type == "bigpic" }
            ?.url?.let { imageUrl + it }
    }



    @Suppress("LABEL_NAME_CLASH")
    override suspend fun load(url: String): LoadResponse? {
        val gson = Gson()
        val video: LoadUrl? = try {
            gson.fromJson(url, LoadUrl::class.java)
        } catch (e: Exception) {
            Log.e("Error", "Failed to parse JSON into Entity: ${e.message}")
            null
        }
        if (video == null) {
            Log.e("Error", "Failed to parse video from JSON")
            return null
        }
        val title = video.title
        val poster = getMovieBigPic(url) ?: video.titleContentImageInfo ?: video.alternativeposter
        val type = if (video.tvType.contains("tvshow", true)) TvType.TvSeries else TvType.Movie
        Log.d("Phisher Video",video.stream.toString())
        val hrefList = listOfNotNull(
            video.stream?.hls?.high,
            video.stream?.hls?.base,
            video.stream?.hls?.main,
            video.stream?.dash?.high,
            video.stream?.dash?.base,
            video.stream?.dash?.main,

            video.stream?.mxplay?.hls?.high,
            video.stream?.mxplay?.hls?.base,
            video.stream?.mxplay?.hls?.main,
            video.stream?.mxplay?.dash?.high,
            video.stream?.mxplay?.dash?.base,
            video.stream?.mxplay?.dash?.main,

            // alternative or fallback
            video.stream?.thirdParty?.hlsUrl,
            video.stream?.thirdParty?.dashUrl,
            video.alternativestream
        ).map { it }.distinct()
        return if (type == TvType.TvSeries) {
            val epposter = getMovieBigPic(url)
            val seasonData = getSeasonData("$mainUrl${video.shareUrl}")
            val episodes = mutableListOf<Episode>()

            seasonData.forEach { (season, seasonId) ->
                var episodeNumber = 1
                var page = 1
                var nextQuery: String? = null

                do {
                    val apiUrl = if (nextQuery == null) {
                        "$webApi/detail/tab/tvshowepisodes?type=season&id=$seasonId"
                    } else {
                        "$webApi/detail/tab/tvshowepisodes?type=season&$nextQuery&id=$seasonId&sortOrder=0&device-density=2&userid=debug-user-id&platform=com.mxplay.desktop&content-languages=hi,en&kids-mode-enabled=false"
                    }
                    val jsonResponse = app.get(apiUrl).text

                    val episodesParser = try {
                        gson.fromJson(jsonResponse, EpisodesParser::class.java)
                    } catch (e: Exception) {
                        Log.e("Error M Player:", "Failed to parse JSON on page $page: ${e.message}")
                        break
                    }
                    episodesParser?.items?.forEach { it ->

                        val streamUrls = listOfNotNull(
                            it.stream.hls.high,
                            it.stream.hls.base,
                            it.stream.hls.main,
                            it.stream.dash.high,
                            it.stream.dash.base,
                            it.stream.dash.main,
                            it.stream.mxplay.hls.high,
                            it.stream.mxplay.hls.base,
                            it.stream.mxplay.hls.main,
                            it.stream.mxplay.dash.high,
                            it.stream.mxplay.dash.base,
                            it.stream.mxplay.dash.main
                        ).distinct()
                        if (streamUrls.isEmpty()) return@forEach
                        val name = it.title ?: "Unknown Title"
                        val image = imageUrl + it.imageInfo.firstOrNull()?.url
                        episodes += newEpisode(streamUrls) {
                            this.name = name
                            this.season = season
                            this.episode = episodeNumber
                            this.posterUrl = image
                        }
                        episodeNumber++
                    }

                    nextQuery = episodesParser?.next
                    page++
                } while (nextQuery != null)
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = epposter ?: video.alternativeposter
                backgroundPosterUrl = epposter
                plot = video.description
            }
        }
        else {
            newMovieLoadResponse(title, url, TvType.Movie, hrefList) {
                posterUrl = poster.toString()
                backgroundPosterUrl = poster.toString()
                plot = video.description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = if (data.trim().startsWith("[")) {
            val gson=Gson()
            try {
                gson.fromJson(data, Array<String>::class.java)?.toList().orEmpty()
            } catch (e: Exception) {
                Log.e("Error M Player:", "Failed to parse stream URL list: ${e.message}")
                listOf(data) // fallback to single link
            }
        } else listOf(data)
        urls.forEach { url ->
            val label = if (url.contains(".m3u8")) "HLS"
            else if (url.contains(".mpd")) "DASH (Doesn't Work in CS)"
            else ""
            val fullUrl = if (url.startsWith("video")) endpointurl + url else url
            callback.invoke(
                newExtractorLink(
                    label,
                    "${this.name} $label",
                    url = fullUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        return true
    }

private suspend fun getSeasonData(url: String): List<Pair<Int, String>> {
    val document = app.get(url).document
    return document.select("div.hs__items-container > div").mapNotNull { element ->
        val tab = element.attr("data-tab").toIntOrNull()
        val id = element.attr("data-id")
        if (tab != null && id.isNotBlank()) {
            tab to id
        } else {
            null
        }
    }
}


private fun Headers.getCookies(cookieKey: String = "set-cookie"): Map<String, String> {
    // Get a list of cookie strings
    // set-cookie: name=value; -----> name=value
    val cookieList =
        this.filter { it.first.equals(cookieKey, ignoreCase = true) }.map {
            it.second.split(";").firstOrNull()
        }.filterNotNull()

    // [name=value, name2=value2] -----> mapOf(name to value, name2 to value2)
    return cookieList.associate {
        val split = it.split("=", limit = 2)
        (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
    }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
}

data class LoadUrl(
    val title: String,
    val titleContentImageInfo: List<Any>? = emptyList(),
    val bigpic:String? = null,
    val tvType: String,
    val stream: MovieStream? = null,
    val description: String,
    val shareUrl: String? = null,
    val alternativestream: String? = null,
    val alternativeposter: String? = null
)
}