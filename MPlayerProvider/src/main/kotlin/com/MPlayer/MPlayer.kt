package com.MPlayer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
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
        return newMovieSearchResponse(title, LoadUrl(this.title, this.titleContentImageInfo,this.type,this.stream,this.description,this.shareUrl,null).toJson()) {
            posterUrl = portraitLargeImageUrl
        }
    }

    private fun Item.toSearchResult(): SearchResponse {
        val portraitLargeImageUrl = getPortraitLargeImageUrl(this)
        return newMovieSearchResponse(title, LoadUrl(this.title, this.titleContentImageInfo,this.type,null,this.description,this.shareUrl,null).toJson()) {
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
                    val streamUrl: String = endpointurl +item.stream?.hls?.high.toString()
                    result.add(newMovieSearchResponse(item.title, LoadUrl(item.title, item.titleContentImageInfo, item.type, null, item.description, item.shareUrl,streamUrl,portraitLargeImageUrl).toJson()) {
                        posterUrl = portraitLargeImageUrl
                    })
                } else {
                    val portraitLargeImageUrl = getBigPic(item)
                    result.add(newMovieSearchResponse(item.title, LoadUrl(item.title, item.titleContentImageInfo, item.type, null, item.description, item.shareUrl,null,portraitLargeImageUrl).toJson()) {
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
        val href = video.stream?.hls?.high ?: video.stream?.thirdParty?.hlsUrl ?: video.alternativestream
        return if (type == TvType.TvSeries) {
            val epposter = getMovieBigPic(url)
            val id = getdataid("$mainUrl${video.shareUrl}")
            val apiUrl = "$webApi/detail/tab/tvshowepisodes?type=season&id=$id"
            val episodes = mutableListOf<Episode>()
            val jsonResponse = app.get(apiUrl).toString()
            val episodesParser = try {
                gson.fromJson(jsonResponse, EpisodesParser::class.java)
            } catch (e: Exception) {
                Log.e("Error", "Failed to parse episodes JSON: ${e.message}")
                null
            }
            episodesParser?.items?.forEachIndexed { index, it ->
                val href1 = endpointurl + it.stream.hls.high
                val name = it.title ?: "Unknown Title"
                val image = imageUrl + it.imageInfo.map { img -> img.url }.firstOrNull()
                val episode = index + 1
                val season = 1
                episodes += Episode(data = href1, name = name, season = season, episode = episode, posterUrl = image)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = epposter ?: video.alternativeposter
                backgroundPosterUrl = epposter
                plot = video.description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, href) {
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
        if (data.startsWith("video"))
        {
            val href=endpointurl+data
            callback(
                ExtractorLink(
                    this.name,
                    name,
                    href,
                    "$mainUrl/",
                    Qualities.Unknown.value,
                    true
                )
            )
        }
        else
        callback(
            ExtractorLink(
                this.name,
                name,
                data,
                "$mainUrl/",
                Qualities.Unknown.value,
                true
            )
        )
        return true
    }}

private suspend fun getdataid(url: String): String {
    val data_id= app.get(url).document.select("div.hs__items-container > div").attr("data-id")
    return data_id
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
    val titleContentImageInfo: List<Any>? = emptyList(),  // Defaulting to an empty list
    val tvType: String,
    val stream: MovieStream? = null,
    val description: String,
    val shareUrl: String? = null,
    val alternativestream: String? = null,
    val alternativeposter: String? = null
)