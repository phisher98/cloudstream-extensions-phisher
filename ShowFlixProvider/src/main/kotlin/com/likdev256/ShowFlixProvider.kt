package com.likdev256

//import android.util.Log
import com.lagradost.cloudstream3.*
//import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.annotation.*

class ShowFlixProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://showflix.in"
    override var name = "ShowFlix"
    override val hasMainPage = true
    override var lang = "in"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    data class Results(
        @JsonProperty("objectId") var objectId: String,
        @JsonProperty("movieName") var movieName: String,
        @JsonProperty("rating") var rating: String?,
        @JsonProperty("storyline") var storyline: String?,
        @JsonProperty("poster") var poster: String?,
        @JsonProperty("backdrop") var backdrop: String?,
        @JsonProperty("category") var category: String?,
        @JsonProperty("streamlink") var streamlink: String?,
        @JsonProperty("language") var language: String?,
        @JsonProperty("createdAt") var createdAt: String?,
        @JsonProperty("updatedAt") var updatedAt: String?,
        @JsonProperty("shrink") var shrink: Boolean?,
        @JsonProperty("hdlink") var hdlink: String?
    )

    data class All(
        @JsonProperty("results") var results: ArrayList<Results> = arrayListOf()
    )

    data class fullCount(
        @JsonProperty("results") var results : ArrayList<String> = arrayListOf(),
        @JsonProperty("count") var count : Int
    )

    /* val elements = listOf(
            Pair("Trending Now", "$mainUrl/movie"),
            Pair("Tamil Movies", "$mainUrl/lan/movie/Tamil"),
            Pair("Tamil Dubbed Movies", "$mainUrl/lan/series/Tamil Dubbed"),
            Pair("English Movies", "$mainUrl/lan/movie/English"),
            Pair("Tamil Movies", "$mainUrl/lan/movie/Tamil"),
            Pair("Telugu Movies", "$mainUrl/lan/movie/Telugu"),
            Pair("Hindi Movies", "$mainUrl/lan/movie/Hindi"),
            Pair("Malayalam Movies", "$mainUrl/lan/movie/Malayalam"),
            Pair("Tamil Dubbed TV Series", "$mainUrl/lan/series/Tamil Dubbed")
        )*/

    val MovieapiUrl = "https://parse.showflix.tk/parse/classes/movies"
    val TVapiUrl = "https://parse.showflix.tk/parse/classes/series"

    private suspend fun queryMovieApi(skip: Int, query: String): NiceResponse {
        val req =
            """{"where":{"category":{"${"$"}regex":"$query"}},"limit":20,"skip":$skip,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
            RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
            //Log.d("JSON", res.toString())
        return app.post(
            MovieapiUrl,
            requestBody = req,
            referer = "https://showflix.in/"
        )
    }

    private suspend fun queryTVApi(skip: Int, query: String): NiceResponse {
        val req =
            """{"where":{"category":{"${"$"}regex":"$query"}},"limit":20,"skip":$skip,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        //Log.d("JSON", res.toString())
        return app.post(
            TVapiUrl,
            requestBody = req,
            referer = "https://showflix.in/"
        )
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val Moviereq =
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val TVreq =
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val fullMovies = app.post(MovieapiUrl, requestBody = Moviereq, referer = "https://showflix.in/").parsed<fullCount>().count
        val fullTV = app.post(TVapiUrl, requestBody = TVreq, referer = "https://showflix.in/").parsed<fullCount>().count
        val mainpage = listOf(
            Pair("", "Trending Movies"),
            Pair("""\\QTamil\\E""", "Tamil Movies"),
            Pair("""\\QTamil Dubbed\\E""", "Dubbed Movies"),
            Pair("""\\QEnglish\\E""", "English Movies"),
            Pair("""\\QTelugu\\E""", "Telugu Movies"),
            Pair("""\\QHindi\\E""", "Hindi Movies"),
            Pair("""\\QMalayalam\\E""", "Malayalam Movies")
        )
        val elements = ArrayList<HomePageList>()

        mainpage.apmap { (query, Name) ->
            val home = ArrayList<SearchResponse>()
            val list = queryMovieApi(
                page*10,
                query
            ).parsed<All>().results
            list.map {
                home.add(
                    newMovieSearchResponse(
                        it.movieName,
                        url = "$mainUrl/movie/${it.objectId}",
                        TvType.Movie
                    ) {
                        this.posterUrl = it.poster
                        //this.quality = quality
                    })
            }
            elements.add(HomePageList(Name, home))
        }
        return HomePageResponse(elements, hasNext = true)
    }
}