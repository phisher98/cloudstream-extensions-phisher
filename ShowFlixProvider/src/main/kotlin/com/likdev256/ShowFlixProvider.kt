package com.likdev256

import android.app.appsearch.SearchResult
import android.util.Log
import com.lagradost.cloudstream3.*
//import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.R.attr.data
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlin.math.roundToInt
import me.xdrop.fuzzywuzzy.FuzzySearch

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

    data class Seasons(
        var Seasons: Map<String, List<String?>> = mapOf()
    )

    data class TVResults(
        @JsonProperty("objectId") var objectId: String,
        @JsonProperty("Seasons") var Seasons: Seasons = Seasons(),
        @JsonProperty("seriesName") var seriesName: String,
        @JsonProperty("seriesRating") var seriesRating: String,
        @JsonProperty("seriesStoryline") var seriesStoryline: String?,
        @JsonProperty("seriesPoster") var seriesPoster: String?,
        @JsonProperty("seriesBackdrop") var seriesBackdrop: String?,
        @JsonProperty("seriesCategory") var seriesCategory: String?,
        @JsonProperty("seriesTotalSeason") var seriesTotalSeason: String,
        @JsonProperty("seriesLanguage") var seriesLanguage: String?,
        @JsonProperty("createdAt") var createdAt: String?,
        @JsonProperty("updatedAt") var updatedAt: String?,
        @JsonProperty("hdlink") var hdlink: String?
    )

    data class TVAll(
        @JsonProperty("results") var results: ArrayList<TVResults> = arrayListOf()
    )

    data class MovieResults(
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

    data class MovieAll(
        @JsonProperty("results") var results: ArrayList<MovieResults> = arrayListOf()
    )

    data class fullCount(
        @JsonProperty("results") var results: ArrayList<String> = arrayListOf(),
        @JsonProperty("count") var count: Int
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
            """{"where":{"category":{"${"$"}regex":"$query"}},"limit":40,"skip":$skip,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
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
            """{"where":{"seriesCategory":{"${"$"}regex":"$query"}},"limit":10,"skip":$skip,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        //Log.d("JSON", res.toString())
        return app.post(
            TVapiUrl,
            requestBody = req,
            referer = "https://showflix.in/"
        )
    }

    private val trendingMovies = "Trending Movies"
    private val tamilMovies = "Tamil Movies"
    private val dubbedMovies = "Dubbed Movies"
    private val englishMovies = "English Movies"
    private val teluguMovies = "Telugu Movies"
    private val hindiMovies = "Hindi Movies"
    private val malayalamMovies = "Malayalam Movies"

    private val trendingShows = "Trending Shows"
    private val tamilShows = "Tamil Shows"
    private val dubbedShows = "Dubbed Shows"
    private val englishShows = "English Shows"
    private val teluguShows = "Telugu Shows"
    private val hindiShows = "Hindi Shows"
    private val malayalamShows = "Malayalam Shows"

    override val mainPage = mainPageOf(
        "" to trendingMovies,
        """\\QTamil\\E""" to tamilMovies,
        """\\QTamil Dubbed\\E""" to dubbedMovies,
        """\\QEnglish\\E""" to englishMovies,
        """\\QTelugu\\E""" to teluguMovies,
        """\\QHindi\\E""" to hindiMovies,
        """\\QMalayalam\\E""" to malayalamMovies,
        //TV Shows
        "" to trendingShows,
        """\\QTamil\\E""" to tamilShows,
        """\\QTamil Dubbed\\E""" to dubbedShows,
        """\\QEnglish\\E""" to englishShows,
        """\\QTelugu\\E""" to teluguShows,
        """\\QHindi\\E""" to hindiShows,
        """\\QMalayalam\\E""" to malayalamShows
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        /*val Moviereq =
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val TVreq =
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val fullMovies = app.post(MovieapiUrl, requestBody = Moviereq, referer = "https://showflix.in/").parsed<fullCount>().count
        val fullTV = app.post(TVapiUrl, requestBody = TVreq, referer = "https://showflix.in/").parsed<fullCount>().count*/
        val elements = ArrayList<HomePageList>()
        //val home = ArrayList<SearchResponse>()
        val query = request.data.format(page)
        val Movielist = queryMovieApi(
            page * 40,
            query
        ).parsed<MovieAll>().results

        val TVlist = queryTVApi(
            page * 10,
            query
        ).parsed<TVAll>().results
        if (request.name.contains("Movies")) {
            val home =
                Movielist.map {
                    newMovieSearchResponse(
                        it.movieName,
                        "$mainUrl${"/"}movie${"/"}${it.objectId}",
                        TvType.Movie
                    ) {
                        this.posterUrl = it.poster
                        this.quality = SearchQuality.HD
                    }
                }
            elements.add(HomePageList(request.name, home))
        } else {
            val home =
                TVlist.map {
                    newTvSeriesSearchResponse(
                        it.seriesName,
                        "$mainUrl${"/"}series${"/"}${it.objectId}",
                        TvType.TvSeries
                    ) {
                        this.posterUrl = it.seriesPoster
                        this.quality = SearchQuality.HD
                    }
                }
            elements.add(HomePageList(request.name, home))
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

        val MovieResults = app.post(MovieapiUrl, requestBody = MovieSearchreq, referer = "https://showflix.in/").parsed<MovieAll>().results   //Log.d("JSON", res.toString())

        val check = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "https://showflix.in/")
        Log.d("check", check.toString())

        val TVResults = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "https://showflix.in/").parsed<TVAll>().results

        val Movies = MovieResults.map {
                newMovieSearchResponse(it.movieName, "$mainUrl${"/"}movie${"/"}${it.objectId}", TvType.Movie) {
                    this.posterUrl = it.poster
                    this.quality = SearchQuality.HD
                }
            }
        val TVSeries = TVResults.map {
                newTvSeriesSearchResponse(it.seriesName, "$mainUrl${"/"}series${"/"}${it.objectId}", TvType.TvSeries) {
                    this.posterUrl = it.seriesPoster
                    this.quality = SearchQuality.HD
                }
            }
        val merge = Movies + TVSeries
        return merge.sortedBy { -FuzzySearch.partialRatio(it.name.lowercase(), query.lowercase()) }
}

    override suspend fun load(url: String): LoadResponse? {
        if (url.contains("movie")) {
            val MovieobjID = url.removePrefix("https://showflix.in/movie/")
            val MovieLoadreq =
                """{"where":{"objectId":"$MovieobjID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val Movieresp = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "https://showflix.in/")
                .toString().removePrefix("""{"results":[""").removeSuffix("]}")
            //Log.d("res", Movieresp)
            val Movieit = parseJson<MovieResults>(Movieresp)
            val title = Movieit.movieName
            val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
            val year = yearRegex.find(title)?.value
                ?.toIntOrNull()
            val poster = Movieit.poster
            val backdrop = Movieit.backdrop
            val plot = Movieit.storyline
            val rating = Movieit.rating.toDouble().roundToInt() * 1000

            return newMovieLoadResponse(
                title,
                "$mainUrl${"/"}movie${"/"}${Movieit.objectId}",
                TvType.Movie,
                "$mainUrl${"/"}movie${"/"}${Movieit.objectId}"
            ) {
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
        else {
            val TVobjID = url.removePrefix("https://showflix.in/series/")//TVRegexurl.replace(url, "")
            val TVLoadreq =
                """{"where":{"objectId":"$TVobjID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val TVresp = app.post(TVapiUrl, requestBody = TVLoadreq, referer = "https://showflix.in/")
                .toString().removePrefix("""{"results":[""").removeSuffix("]}")
            //val TVclean1 = Regexlist1.replace(TVresp, "")
            //Log.d("res", TVresp)
            //Log.d("test1", TVclean1)
            //val TVclean2 = TVclean1.removeSuffix("]}")//Regexlist2.replace(TVclean1, "")
            //Log.d("test", TVclean2)
            val TVit = parseJson<TVResults>(TVresp)
            val title = TVit.seriesName
            val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
            val year = yearRegex.find(title)?.value
                ?.toIntOrNull()
            val poster = TVit.seriesPoster
            val backdrop = TVit.seriesBackdrop
            val plot = TVit.seriesStoryline
            val rating = TVit.seriesRating.toDouble().roundToInt() * 1000
            val seasonCount = TVit.seriesTotalSeason.toInt()
            //val recommendations = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "https://showflix.in/").parsed<MovieAll>().results

            /*{
                "0": {
                "objectId": "6HTSAD5BWu",
                "Seasons": {
                "Season 1": [
                null,
                "https://embedsb.com/play/61ivwwqc8aci.html",
                "https://embedsb.com/play/ol14scunl1lk.html",
                "https://embedsb.com/play/yt44a3v3brej.html",
                "https://embedsb.com/play/m88ogvsfmsd0.html",
                "https://embedsb.com/play/l7eifocs58yt.html",
                "https://embedsb.com/play/9ualmpfnviqy.html"
                ]
            },
                "seriesName": "Bloody Brothers",
                "seriesRating": "8",
                "seriesStoryline": "Driving home late one night, two brothers, Jaggi and Daljeet run over an old man. They put the body back in his house, but when people suspect, the brothers' lives fall apart. They realise they can trust no one. Not even each other.",
                "seriesPoster": "https://www.themoviedb.org/t/p/original/7l4NR6lipccf43Wtihi9xfMn60a.jpg",
                "seriesBackdrop": "https://www.themoviedb.org/t/p/original/3KIQGLMKetNJ9xAXx0mrcItLIxS.jpg",
                "seriesCategory": "Tamil Telugu Hindi",
                "seriesTotalSeason": "1",
                "seriesLanguage": "hi",
                "createdAt": "2022-05-18T13:11:17.073Z",
                "updatedAt": "2022-05-18T13:11:17.073Z"
            }
            }*/

            val episodes = mutableListOf<Episode>()
            TVit.Seasons.Seasons.forEach {
                episodes.add(
                    Episode(
                        it.value.toString()
                    ) //Map<String, List<String>> = mapOf()
                )
            }

            return newTvSeriesLoadResponse(
                title,
                "$mainUrl${"/"}series${"/"}${TVit.objectId}",
                TvType.TvSeries,
                episodes
            ) {
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
}
