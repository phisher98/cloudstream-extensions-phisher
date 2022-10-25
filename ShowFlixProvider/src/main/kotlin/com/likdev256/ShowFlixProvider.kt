package com.likdev256

import android.util.Log
import com.lagradost.cloudstream3.*
import com.fasterxml.jackson.annotation.JsonProperty
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
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    //private const val TAG = "ShowFlix"

    override val mainPage = mainPageOf(
        "" to "Trending Movies",
        """\\QTamil\\E""" to "Tamil Movies",
        """\\QTamil Dubbed\\E""" to "Dubbed Movies",
        """\\QEnglish\\E""" to "English Movies",
        """\\QTelugu\\E""" to "Telugu Movies",
        """\\QHindi\\E""" to "Hindi Movies",
        """\\QMalayalam\\E""" to "Malayalam Movies",
        "$mainUrl/lan/series/Tamil Dubbed" to "Dubbed TV Series"
    )

    //Log.d("request", request.toString())
    //Log.d("Check", request.data)
    //Log.d("Page", page.toString())

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

    private suspend fun queryApi(apiUrl: String, query: String): NiceResponse {
        //val res = app.post(
            //"https://parse.showflix.tk/parse/classes/movies",
            //requestBody = """{"where":{"category":{"${"$"}regex":"\\QTamil Dubbed\\E"}},"limit":50,"order":"-updatedAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull()),
            //referer = "https://showflix.in/"
        //).parsed<All>()
        val req = """{"where":{"category":{"${"$"}regex":"$query"}},"limit":50,"order":"-updatedAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        //Log.d("JSON", res.toString())
        return app.post(apiUrl, requestBody = req, referer = "https://showflix.in/")
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        /*val req = mapOf(
            "_ApplicationId" to "SHOWFLIXAPPID",
            "_ClientVersion" to "js3.4.1",
            "_InstallationId" to "2573d2b7-ef8f-48f0-9e91-8e5c2beb220d",
            "_method" to "GET",
            "limit" to "50",
            "order" to "-updatedAt",
            "where" to mapOf("category" to mapOf("$/regex" to "\\QTamil Dubbed\\E"))
        )*/

        val list = queryApi("https://parse.showflix.tk/parse/classes/movies", "").parsed<All>().results.map {
            newMovieSearchResponse(
                it.movieName,
                url = "$mainUrl/movie/${it.objectId}",
                TvType.Movie
            ) {
                this.posterUrl = it.poster
                //this.quality = quality
            }
        }
        return if (request.name.contains("(H)")) HomePageResponse(
            arrayListOf(
                HomePageList(
                    request.name.replace(" (H)", ""),
                    list,
                    request.name.contains("(H)")
                )
            ), hasNext = true)
        else newHomePageResponse(request.name, list)
    }
}