package com.phisher98

import com.fasterxml.jackson.annotation.*
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.random.Random

class ShowFlixProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://showflix.site"
    override var name = "ShowFlix"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val installationID = "951253bd-11ac-473f-b0b0-346e2d3d542f"

    data class Seasons(
        var Seasons: Map<String, List<String?>> = mapOf()
    )

    data class TVResults(
        @JsonProperty("objectId"          ) var objectId          : String?,
        @JsonProperty("Seasons"           ) var Seasons           : Seasons = Seasons(),
        @JsonProperty("seriesName"        ) var seriesName        : String,
        @JsonProperty("seriesRating"      ) var seriesRating      : String?,
        @JsonProperty("seriesStoryline"   ) var seriesStoryline   : String?,
        @JsonProperty("seriesPoster"      ) var seriesPoster      : String?,
        @JsonProperty("seriesBackdrop"    ) var seriesBackdrop    : String?,
        @JsonProperty("seriesCategory"    ) var seriesCategory    : String?,
        @JsonProperty("seriesTotalSeason" ) var seriesTotalSeason : String?,
        @JsonProperty("seriesLanguage"    ) var seriesLanguage    : String?,
        @JsonProperty("createdAt"         ) var createdAt         : String?,
        @JsonProperty("updatedAt"         ) var updatedAt         : String?,
        @JsonProperty("Note"              ) var Note              : String?,
        @JsonProperty("streamhide"        ) var streamhide        : Seasons? = Seasons(),
        @JsonProperty("streamwish"        ) var streamwish        : Seasons = Seasons(),
        @JsonProperty("hdlink"            ) var hdlink            : String?,
        @JsonProperty("streamruby"        ) var streamruby        : Seasons = Seasons(),
        @JsonProperty("filelions"         ) var filelions         : Seasons = Seasons()
    )

    data class TVAll(
        @JsonProperty("results") var results: ArrayList<TVResults> = arrayListOf()
    )

    data class MovieResults(
        @JsonProperty("objectId"   ) var objectId   : String?,
        @JsonProperty("movieName"  ) var movieName  : String,
        @JsonProperty("rating"     ) var rating     : String?,
        @JsonProperty("storyline"  ) var storyline  : String?,
        @JsonProperty("poster"     ) var poster     : String?,
        @JsonProperty("backdrop"   ) var backdrop   : String?,
        @JsonProperty("category"   ) var category   : String?,
        @JsonProperty("streamlink" ) var streamlink : String?,
        @JsonProperty("language"   ) var language   : String?,
        @JsonProperty("hdlink"     ) var hdlink     : String?,
        @JsonProperty("sharedisk"  ) var sharedisk  : String?,
        @JsonProperty("streamhide" ) var streamhide : String?,
        @JsonProperty("streamwish" ) var streamwish : String?,
        @JsonProperty("filelions"  ) var filelions  : String?,
        @JsonProperty("streamruby" ) var streamruby : String?,
        @JsonProperty("uploadever" ) var uploadever : String?,
        @JsonProperty("shrink"     ) var shrink     : Boolean?,
        @JsonProperty("createdAt"  ) var createdAt  : String?,
        @JsonProperty("updatedAt"  ) var updatedAt  : String?
    )

    data class MovieAll(
        @JsonProperty("results") var results: ArrayList<MovieResults> = arrayListOf()
    )

    data class MovieLinks(
        @JsonProperty("streamlink" ) var streamsb  : String?,
        @JsonProperty("streamhide" ) val streamhide: String?,
        @JsonProperty("sharedisk"  ) val sharedisk : String?,
        @JsonProperty("filelions"  ) val filelions : String?,
        @JsonProperty("streamwish" ) val streamwish : String?,
        @JsonProperty("streamruby" ) val streamruby : String?,
    )

    /*data class fullCount(
        @JsonProperty("results") var results: ArrayList<String> = arrayListOf(),
        @JsonProperty("count") var count: Int
    )*/

    private val MovieapiUrl = "https://parse.showflix.shop/parse/classes/movies"
    private val TVapiUrl    = "https://parse.showflix.shop/parse/classes/series"

    private suspend fun queryMovieApi(skip: Int, query: String): NiceResponse {
        val req =
            """{"where":{"category":{"${"$"}regex":"$query"}},"order":"-createdAt","limit":40,"skip":$skip,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        //Log.d("JSON", res.toString())
        return app.post(
            MovieapiUrl,
            requestBody = req,
            referer = "$mainUrl/"
        )
    }

    private suspend fun queryTVApi(skip: Int, query: String): NiceResponse {
        val req =
            """{"where":{"seriesCategory":{"${"$"}regex":"$query"}},"order":"-createdAt","limit":10,"skip":$skip,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        //Log.d("JSON", res.toString())
        return app.post(
            TVapiUrl,
            requestBody = req,
            referer = "$mainUrl/"
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
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val TVreq =
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val fullMovies = app.post(MovieapiUrl, requestBody = Moviereq, referer = "$mainUrl/").parsed<fullCount>().count
        val fullTV = app.post(TVapiUrl, requestBody = TVreq, referer = "$mainUrl/").parsed<fullCount>().count*/
        val elements = ArrayList<HomePageList>()
        //val home = ArrayList<SearchResponse>()
        val query = request.data.format(page)
        val Movielist = queryMovieApi(
            if(page == 1) 0 else page * 40,
            query
        ).parsed<MovieAll>().results

        val TVlist = queryTVApi(
            if(page == 1) 0 else page * 10,
            query
        ).parsed<TVAll>().results
        if (request.name.contains("Movies")) {
            val home =
                Movielist.map {
                    newMovieSearchResponse(
                        it.movieName,
                        "$mainUrl/movie/${it.objectId}",
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
                        "$mainUrl/series/${it.objectId}",
                        TvType.TvSeries
                    ) {
                        this.posterUrl = it.seriesPoster
                        this.quality = SearchQuality.HD
                    }
                }
            elements.add(HomePageList(request.name, home))
        }
        return newHomePageResponse(elements, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val MovieSearchreq =
            """{"where":{"movieName":{"${"$"}regex":"$query","${"$"}options":"i"}},"order":"-updatedAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val TVSearchreq =
            """{"where":{"seriesName":{"${"$"}regex":"$query","${"$"}options":"i"}},"order":"-updatedAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )

        val MovieResults =
            app.post(MovieapiUrl, requestBody = MovieSearchreq, referer = "$mainUrl/")
                .parsed<MovieAll>().results   //Log.d("JSON", res.toString())

        // val check = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "$mainUrl/")
        // Log.d("check", check.toString())

        val TVResults = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "$mainUrl/")
            .parsed<TVAll>().results

        val Movies = MovieResults.map {
            newMovieSearchResponse(
                it.movieName,
                "$mainUrl/movie/${it.objectId}",
                TvType.Movie
            ) {
                this.posterUrl = it.poster
                this.quality = SearchQuality.HD
            }
        }
        val TVSeries = TVResults.map {
            newTvSeriesSearchResponse(
                it.seriesName,
                "$mainUrl/series/${it.objectId}",
                TvType.TvSeries
            ) {
                this.posterUrl = it.seriesPoster
                this.quality = SearchQuality.HD
            }
        }
        val merge = Movies + TVSeries
        // merge.map {
            // Log.d("myname", it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase())
            // Log.d("myquery", query)
        // }
        return merge.sortedBy { -FuzzySearch.partialRatio(it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), query.lowercase()) }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.contains("movie")) {
            val MovieobjID = url.removePrefix("$mainUrl/movie/")
            val MovieLoadreq =
                """{"where":{"objectId":"$MovieobjID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val Movieresp = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "$mainUrl/")
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
            val rating = Movieit.rating.toRatingInt()
            val recQuery = when(Movieit.category != null) {
                Movieit.category.toString().contains("Dubbed")    -> """\\QTamil Dubbed\\E"""
                Movieit.category.toString().contains("Tamil")     -> """\\QTamil\\E"""
                Movieit.category.toString().contains("English")   -> """\\QEnglish\\E"""
                Movieit.category.toString().contains("Hindi")     -> """\\QHindi\\E"""
                Movieit.category.toString().contains("Malayalam") -> """\\QMalayalam\\E"""
                else -> ""
            }
            val recommendations = queryMovieApi(
                Random.nextInt(0, 2000),
                recQuery
            ).parsed<MovieAll>().results.map{
                newMovieSearchResponse(
                    it.movieName,
                    "$mainUrl/movie/${it.objectId}",
                    TvType.Movie
                ) {
                    this.posterUrl = it.poster
                    this.quality = SearchQuality.HD
                }
            }

            return newMovieLoadResponse(
                title,
                "$mainUrl/movie/${Movieit.objectId}",
                TvType.Movie,
                MovieLinks(
                    Movieit.streamlink,
                    Movieit.streamhide,
                    Movieit.sharedisk,
                    Movieit.filelions,
                    Movieit.streamwish,
                    Movieit.streamruby,
                ).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                //this.tags = tags
                this.rating = rating
                this.backgroundPosterUrl = backdrop
                //addActors(actors)
                this.recommendations = recommendations
                //addTrailer(trailer)
            }
        } else {
            val TVobjID =
                url.removePrefix("$mainUrl/series/")//TVRegexurl.replace(url, "")
            val TVLoadreq =
                """{"where":{"objectId":"$TVobjID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val TVresp = app.post(TVapiUrl, requestBody = TVLoadreq, referer = "$mainUrl/")
                .toString().removePrefix("""{"results":[""").removeSuffix("]}")
            val TVit = parseJson<TVResults>(TVresp)
            val title = TVit.seriesName
            val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
            val year = yearRegex.find(title)?.value
                ?.toIntOrNull()
            val poster = TVit.seriesPoster
            val backdrop = TVit.seriesBackdrop
            val plot = TVit.seriesStoryline
            val rating = TVit.seriesRating.toRatingInt()
            //val seasonCount = TVit.seriesTotalSeason.toInt()
            val recQuery = when(TVit.seriesCategory != null) {
                TVit.seriesCategory.toString().contains("Dubbed")    -> """\\QTamil Dubbed\\E"""
                TVit.seriesCategory.toString().contains("Tamil")     -> """\\QTamil\\E"""
                TVit.seriesCategory.toString().contains("English")   -> """\\QEnglish\\E"""
                TVit.seriesCategory.toString().contains("Hindi")     -> """\\QHindi\\E"""
                TVit.seriesCategory.toString().contains("Malayalam") -> """\\QMalayalam\\E"""
                else -> ""
            }
            Log.d("Phisher query", recQuery)
            val recommendations = queryTVApi(
                Random.nextInt(0, 100),
                recQuery
            ).parsed<TVAll>().results.map {
                newTvSeriesSearchResponse(
                    it.seriesName,
                    "$mainUrl/series/${it.objectId}",
                    TvType.TvSeries
                ) {
                    this.posterUrl = it.seriesPoster
                    this.quality = SearchQuality.HD
                }
            }
            // Log.d("TVResult", recommendations.toString())

            val seasonDataList = listOf(TVit.Seasons.Seasons,
                TVit.streamwish.Seasons, TVit.filelions.Seasons, TVit.streamruby.Seasons, TVit.streamhide?.Seasons)
            val combinedSeasons = mutableMapOf<String, MutableMap<Int, List<String?>>>()
            val episodes = TVit.Seasons.Seasons.map { (seasonName, episodes) ->
                val seasonNum = Regex("\\d+").find(seasonName)?.value?.toInt()
                episodes.mapIndexed { epNum, data ->
                    newEpisode(data.toString())
                    {
                        this.season=seasonNum
                        this.episode=epNum
                        this.posterUrl=backdrop
                    }
                }.filter { it.episode != 0 }
            }.flatten()
            return newTvSeriesLoadResponse(
                title,
                "$mainUrl/series/${TVit.objectId}",
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                //this.tags = tags
                this.rating = rating
                this.backgroundPosterUrl = backdrop
                this.recommendations = recommendations
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.contains("sharedisk")) {
            val sources = mutableListOf<String>()
            val m = parseJson<MovieLinks>(data)
            val filelions = "https://filelions.to/v/" + m.filelions
            val streamwish = "https://streamwish.to/e/" + m.streamwish
            val streamruby = "https://streamruby.com/" + m.streamruby
            sources.add(filelions)
            sources.add(streamwish)
            sources.add(streamruby)
            sources.forEach { url->
             loadExtractor(url,subtitleCallback,callback)
            }
        }
        else
        {
            if (data.contains("streamwish"))
            {
                val href=data.replace("streamwish","embedwish")
                loadExtractor(href,subtitleCallback, callback)
            }
            else
            {
                loadExtractor(data,subtitleCallback, callback)
            }
        }
        return true
    }
}
