package com.Mxplayer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MxplayerProvider : MainAPI() {
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
    )
    override var lang = "hi"
    private var mxplayer: MXPlayer? = null
    override var mainUrl = "https://www.mxplayer.in"
    override var name = "Mxplayer"
    override val hasMainPage = true
    private var imageUrl="https://qqcdnpictest.mxplay.com/"
    private var userID: String? = null
    private val webApi = "https://api.mxplayer.in/v1/web"
    private val endParam
        get() = "&device-density=2&userid=$userID&platform=com.mxplay.desktop&content-languages=hi,en&kids-mode-enabled=false"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get(mainUrl)
        userID = res.okhttpResponse.headers.getCookies()["UserID"]
            ?: throw ErrorLoadingException("load fail, geo blocked")
        val drama = app.get(
                "$webApi/detail/browseItem?&pageNum=$page&pageSize=20&isCustomized=true&genreFilterIds=48efa872f6f17facebf6149dfc536ee1&type=2$endParam",
                referer = "$mainUrl/"
            ).parsedSafe<MXPlayer>()?.items?.map { item -> item.toSearchResult() } ?: throw ErrorLoadingException("Failed to load Drama data")
        val comedy = app.get(
            "$webApi/detail/browseItem?&pageNum=$page&pageSize=20&isCustomized=true&genreFilterIds=a24ddcadde26310ddfdb674e09e38eb5&type=2$endParam",
            referer = "$mainUrl/"
        ).parsedSafe<MXPlayer>()?.items?.map { item -> item.toSearchResult() } ?: throw ErrorLoadingException("Failed to load Comedy data")
        val hindimovieresponse = app.get(
            "$webApi/detail/browseItem?&pageNum=$page&pageSize=20&isCustomized=true&browseLangFilterIds=hi&type=1$endParam",
            referer = "$mainUrl/").toString()
        val movieRoot: MovieRoot = Gson().fromJson(hindimovieresponse, object : TypeToken<MovieRoot>() {}.type)
        val hindi_Movies = movieRoot.items.map { item ->
            item.toSearchResult()
        }

        val Dramashows = HomePageList("Drama Shows", drama)
        val Comedyshows = HomePageList("Comedy Shows", comedy)
        val HindiMovies = HomePageList("Hindi Movies", hindi_Movies)
        return newHomePageResponse(listOf(Dramashows,Comedyshows,HindiMovies),hasNext = true)
    }

    //Movie classes
    private fun MovieItem.toSearchResult(): SearchResponse {
        val portraitLargeImageUrl = getPortraitLargeImageUrl(this)
        return newMovieSearchResponse(title, LoadUrl(this.title, this.titleContentImageInfo,this.type,this.stream,this.description,this.shareUrl).toJson()) {
            posterUrl = portraitLargeImageUrl
        }
    }

    private fun Item.toSearchResult(): SearchResponse {
        val portraitLargeImageUrl = getPortraitLargeImageUrl(this)
        return newMovieSearchResponse(title, LoadUrl(this.title, this.titleContentImageInfo,this.type,null,this.description,this.shareUrl).toJson()) {
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
                    val streamUrl: String? = item.stream?.hls?.high
                    result.add(newMovieSearchResponse(item.title, LoadUrl(item.title, item.titleContentImageInfo, item.type, null, item.description, item.shareUrl).toJson()) {
                        posterUrl = portraitLargeImageUrl
                    })
                } else {
                    // For non-movie types, handle them without the stream (or other special handling)
                    result.add(newMovieSearchResponse(item.title, LoadUrl(item.title, item.titleContentImageInfo, item.type, null, item.description, item.shareUrl).toJson()) {
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
        val poster = getMovieBigPic(url) ?: video.titleContentImageInfo
        val type = if (video.tvType.contains("tvshow", true)) TvType.TvSeries else TvType.Movie
        val href = when (val stream = video.stream) {
            is MovieStream -> "https://d3sgzbosmwirao.cloudfront.net/" + stream.hls?.high  // Handle MovieStream type
            else -> null  // If stream is neither MovieStream nor String, return null
        }

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
                val href1 = "https://d3sgzbosmwirao.cloudfront.net/" + it.stream.hls.high
                val name = it.title ?: "Unknown Title"
                val image = imageUrl + it.imageInfo.map { img -> img.url }.firstOrNull()
                val episode = index + 1
                val season = 1
                episodes += Episode(data = href1, name = name, season = season, episode = episode, posterUrl = image)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = epposter
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

private suspend fun getdataid(url: String): String? {
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
    val tvType: String = "",  // Defaulting to empty string
    val stream: MovieStream? = null,  // Defaulting to null
    val description: String = "",  // Defaulting to empty string
    val shareUrl: String? = null  // Defaulting to null
)