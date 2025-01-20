package com.Mxplayer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
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
        return newMovieSearchResponse(title, this.toJson()) {
            posterUrl = portraitLargeImageUrl
        }
    }

    private fun Item.toSearchResult(): SearchResponse {
        val portraitLargeImageUrl = getPortraitLargeImageUrl(this)
        return newMovieSearchResponse(title, this.toJson()) {
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

    fun getBigPic(jsonString: String): String? {
        val item = tryParseJson<Item>(jsonString)
            ?: throw IllegalArgumentException("Invalid JSON or parsing failed")
        val bigPicUrl = item.titleContentImageInfo.firstOrNull { it.type == "banner_and_static_bg_desktop" }?.url

        return bigPicUrl?.let { "$imageUrl$it" }
    }

    fun getMovieBigPic(jsonString: String): String? {
        val gson = Gson()
        val item = gson.fromJson(jsonString, MovieItem::class.java)
        val bigPicUrl = item.titleContentImageInfo?.firstOrNull { it.type == "banner_and_static_bg_desktop" }?.url
        return bigPicUrl?.let { "$imageUrl$it" }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        //Not Working as of now search
        if (userID == null) {
            val res = app.get(mainUrl)
            userID = res.okhttpResponse.headers.getCookies()["UserID"]
        }
        val data = app.post(
            "$webApi/search/resultv2?query=$query$endParam",
            referer = "$mainUrl/",
            requestBody = "{}".toRequestBody("application/json".toMediaType())
        ).parsed<MXPlayer>()

        // Map the data items to SearchResponse using the toSearchResult extension function
        return data.items.map { entity ->
            Log.d("Phisher",entity.toString())
            entity.toSearchResult() // Ensure that `toSearchResult` maps the entity to the correct SearchResponse
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val gson = Gson()
        val video: Entity? = try {
            gson.fromJson(url, Entity::class.java)
        } catch (e: Exception) {
            Log.e("Error", "Failed to parse JSON into Entity")
            null
        }
        if (video == null) {
            Log.e("Error", "Failed to parse video from JSON")
            return null
        }
        val title = video.title
        val poster=getMovieBigPic(url)
        val type = if (video.type.contains("tvshow", true)) TvType.TvSeries else TvType.Movie
        val href="https://d3sgzbosmwirao.cloudfront.net/"+video.stream?.hls?.high
        Log.d("Phisher",href.toString())
        return if (type == TvType.TvSeries) {
            val epposter = getBigPic(url) ?: return null
            val id = getdataid("$mainUrl${video.shareUrl}")
            val apiUrl = "$webApi/detail/tab/tvshowepisodes?type=season&id=$id"
            val episodes = mutableListOf<Episode>()
            val jsonResponse = app.get(apiUrl).toString()
            val episodesParser = try {
                gson.fromJson(jsonResponse, EpisodesParser::class.java)
            } catch (e: Exception) {
                Log.e("Error", "Failed to parse episodes JSON")
                null
            }
            episodesParser?.items?.forEachIndexed { index, it ->
                val href = "https://d3sgzbosmwirao.cloudfront.net/" + it.stream.hls.high
                val name = it.title ?: "Unknown Title"
                val image = imageUrl + it.imageInfo.map { img -> img.url }.firstOrNull()
                val episode = index + 1
                val season = 1
                episodes += Episode(data = href, name = name, season = season, episode = episode, posterUrl = image)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = epposter
                backgroundPosterUrl = epposter
                plot = video.description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                posterUrl = poster
                backgroundPosterUrl = poster
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
/*
    private fun loadVideoLink(link: VideoLink?, callback: (ExtractorLink) -> Unit) {
        link ?: return
        link.base?.let {
            addVideoLink(callback, it, "base")
        }
        link.main?.let {
            addVideoLink(callback, it, "main")
        }
        link.high?.let {
            addVideoLink(callback, it, "high")
        }
        link.hlsUrl?.let {
            addVideoLink(callback, it, "thirdParty")
        }
    }

    private fun addVideoLink(callback: (ExtractorLink) -> Unit, url: String, name: String) {
        val newUrl = if (!url.startsWith("http")) {
            mxplayer!!.config.videoCdnBaseUrl + url
        } else {
            url
        }
        callback(
            ExtractorLink(
                this.name,
                name,
                newUrl,
                "$mainUrl/",
                Qualities.Unknown.value,
                true
            )
        )
    }

    private fun List<Image>.getUrl(type: String = "square"): String? {
        return firstOrNull { it.type == type }?.let {
            mxplayer!!.config.imageBaseUrl + "/" + it.url
        }
    }

    private suspend fun getMxplayer(url: String): Mxplayer {
        val res = app.get(url, referer = "$mainUrl/")
        return getMxplayer(res)
    }
}
 */

suspend fun getHlsLink(url: String): String? {
    val gson = Gson()

    // Parse the JSON into MovieRoot object
    val movieRoot: MovieRoot? = try {
        gson.fromJson(url, MovieRoot::class.java)
    } catch (e: Exception) {
        Log.e("Error", "Failed to parse JSON into MovieRoot: ${e.message}")
        return null
    }

    // If parsing fails or the movieRoot is null, log the error and return null
    if (movieRoot == null) {
        Log.e("Error", "Failed to parse movie data from JSON")
        return null
    }

    // Extract the first MovieItem (or iterate through items if you need more)
    val movieItem = movieRoot.items.firstOrNull() ?: run {
        Log.e("Error", "No movie items found in the response")
        return null
    }

    // Extract the HLS link from the MovieStream
    val hlsLink = movieItem.stream.hls?.high

    // Log the HLS link (for debugging purposes)
    Log.d("HLS Link", "Found HLS link: $hlsLink")

    // Handle missing or null HLS link
    if (hlsLink == null) {
        Log.e("Error", "No HLS link found for the movie")
        return null
    }

    // Return the HLS link
    return hlsLink
}

fun getHlsLinkFromJson(jsonInput: String): String? {
    val gson = Gson()
    return try {
        // Parse the JSON into the MovieStream data class
        val movieStream = gson.fromJson(jsonInput, MovieStream::class.java)
        movieStream.hls?.high
    } catch (e: Exception) {
        Log.e("Error", "Failed to parse JSON: ${e.message}")
        null
    }
}

private fun getMxplayer(res: NiceResponse): MXPlayer {
    val data = res.document.select("script")
        .firstOrNull { it.data().startsWith("window.__mxs__ = ") }
        ?.data() ?: throw ErrorLoadingException("Failed to load data from the main page")
    val jsonData = data.substringAfter("window.__mxs__ = ").trim()
    return tryParseJson(jsonData)
        ?: throw ErrorLoadingException("Failed to parse mxplayer data")
}


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


data class MoviesResponse(val movieList: List<Movie>)

data class Movie(val name: String, val movieId: String) {
    fun convertToDisplayMovie(): DisplayMovie {
        return DisplayMovie(name, movieId)
    }
}

data class DisplayMovie(val name: String, val movieId: String)
