package com.likdev256

import android.util.Log
import com.lagradost.cloudstream3.*
//import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlin.math.roundToInt

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

    data class Results(
        @JsonProperty("objectId") var objectId: String,
        @JsonProperty("movieName") var movieName: String,
        @JsonProperty("rating") var rating: String,
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

    private val MovieapiUrl = "https://parse.showflix.tk/parse/classes/movies"
    private val TVapiUrl = "https://parse.showflix.tk/parse/classes/series"

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
                        it.objectId,
                        TvType.Movie
                    ) {
                        this.posterUrl = it.poster
                        this.quality = getQualityFromString("hd")
                    })
            }
            elements.add(HomePageList(Name, home))
        }
        return HomePageResponse(elements, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val MovieSearchreq =
            """{"where":{"movieName":{"${"$"}regex":"$query","${"$"}options":"i"}},"order":"-updatedAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val TVSearchreq =
            """{"where":{"seriesName":{"${"$"}regex":"$query","${"$"}options":"i"}},"order":"-updatedAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        //Log.d("JSON", res.toString())
        return app.post(MovieapiUrl, requestBody = MovieSearchreq, referer = "https://showflix.in/").parsed<All>().results.map {
            newMovieSearchResponse(it.movieName, it.objectId, TvType.Movie) {
                this.posterUrl = it.poster
                this.quality = getQualityFromString("hd")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val Regexurl = Regex("(https:\\/\\/showflix\\.in/)")
        val objID = Regexurl.replace(url, "")
        val MovieLoadreq =
            """{"where":{"objectId":"$objID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        Log.d("rq", objID.toString())
        val resp = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "https://showflix.in/").toString()
        val Regexlist1 = Regex("(\\{\"results\":\\[)")
        val Regexlist2 = Regex("(?<=\\})(?:(.)*)*")
        val clean1 = Regexlist1.replace(resp, "")
        Log.d("res", resp)
        Log.d("test1", clean1)
        val clean2 = Regexlist2.replace(clean1, "")
        Log.d("test", clean2)
        val it = parseJson<Results>(clean2)
        val title = it.movieName
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value
            ?.toIntOrNull()
        val poster = it.poster
        val backdrop = it.backdrop
        val plot = it.storyline
        val rating = it.rating.toDouble().roundToInt()
        //val recommendations = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "https://showflix.in/").parsed<All>().results
        return newMovieLoadResponse(title, "$mainUrl${"/"}movie${"/"}${it.objectId}", TvType.Movie, "$mainUrl${"/"}movie${"/"}${it.objectId}") {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                //this.tags = tags
                this.rating = rating
                this.backgroundPosterUrl = backdrop
                //addActors(actors)
                //this.recommendations = recommendations
                //addTrailer(trailer)
            }
    }
}