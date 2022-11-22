package com.likdev256

//import com.fasterxml.jackson.annotation.JsonProperty
import android.util.Log
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
        @JsonProperty("streamlink") var streamlink: String,
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
            referer = "$mainUrl/"
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
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val TVreq =
            """{"where":{},"limit":0,"count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val fullMovies = app.post(MovieapiUrl, requestBody = Moviereq, referer = "$mainUrl/").parsed<fullCount>().count
        val fullTV = app.post(TVapiUrl, requestBody = TVreq, referer = "$mainUrl/").parsed<fullCount>().count*/
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

        val MovieResults =
            app.post(MovieapiUrl, requestBody = MovieSearchreq, referer = "$mainUrl/")
                .parsed<MovieAll>().results   //Log.d("JSON", res.toString())

        val check = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "$mainUrl/")
        Log.d("check", check.toString())

        val TVResults = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "$mainUrl/")
            .parsed<TVAll>().results

        val Movies = MovieResults.map {
            newMovieSearchResponse(
                it.movieName,
                "$mainUrl${"/"}movie${"/"}${it.objectId}",
                TvType.Movie
            ) {
                this.posterUrl = it.poster
                this.quality = SearchQuality.HD
            }
        }
        val TVSeries = TVResults.map {
            newTvSeriesSearchResponse(
                it.seriesName,
                "$mainUrl${"/"}series${"/"}${it.objectId}",
                TvType.TvSeries
            ) {
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
            val rating = Movieit.rating.toDouble().roundToInt() * 1000

            return newMovieLoadResponse(
                title,
                "$mainUrl${"/"}movie${"/"}${Movieit.objectId}",
                TvType.Movie,
                "movie,${Movieit.streamlink}"
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
        } else {
            val TVobjID =
                url.removePrefix("https://showflix.in/series/")//TVRegexurl.replace(url, "")
            val TVLoadreq =
                """{"where":{"objectId":"$TVobjID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"1c380d0e-7415-433c-b0ab-675872a0e782"}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val TVresp = app.post(TVapiUrl, requestBody = TVLoadreq, referer = "$mainUrl/")
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
            //val seasonCount = TVit.seriesTotalSeason.toInt()
            //val recommendations = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "$mainUrl/").parsed<MovieAll>().results

            //val episodes = mutableListOf<Episode>()
            val episodes = TVit.Seasons.Seasons.map { (seasonName, episodes) ->
                val seasonNum = Regex("\\d+").find(seasonName)?.value?.toInt()

                episodes.mapIndexed { epNum, data ->
                    //Log.d("Episodedata", data.toString())
                    //Log.d("EpisodeNum", epNum.toString())
                    Episode(
                        data = data.toString(),
                        season = seasonNum,
                        episode = epNum
                    ) //Map<String, List<String>> = mapOf()
                }
            }.flatten()

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

    data class Subs (
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class StreamData (
        @JsonProperty("file") val file: String,
        @JsonProperty("cdn_img") val cdnImg: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("subs") val subs: ArrayList<Subs>? = arrayListOf(),
        @JsonProperty("length") val length: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("backup") val backup: String,
    )

    data class Main (
        @JsonProperty("stream_data") val streamData: StreamData,
        @JsonProperty("status_code") val statusCode: Int,
    )

    private val hexArray = "0123456789ABCDEF".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        //Log.d("data", data)
        if (data.contains("movie")) {
            val url = data.substringAfter(",").replace(Regex("(embed-|/play/)"), "/e/")
            //Log.d("url", url)
            val main =
                if (url.contains("sbcloud")) "https://sbcloud1.com" else "https://embedsb.com"
            //Log.d("main", main)

            val regexID =
                Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|/e/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
            val id = regexID.findAll(url).map {
                it.value.replace(Regex("(embed-|/e/)"), "")
            }.first()
//        val master = "$main/sources48/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
            val master = "$main/sources48/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
            val headers = mapOf(
                "watchsb" to "sbstream",
            )
            val mapped = app.get(
                master.lowercase(),
                headers = headers,
                referer = url,
            ).parsedSafe<Main>()
            // val urlmain = mapped.streamData.file.substringBefore("/hls/")
            M3u8Helper.generateM3u8(
                name,
                mapped?.streamData?.file.toString(),
                url,
                headers = headers
            ).forEach(callback)

        } else {
            val url = data.replace(Regex("(embed-|/play/)"), "/e/")
            //Log.d("url", url)
            val main =
                if (url.contains("sbcloud")) "https://sbcloud1.com" else "https://embedsb.com"
            //Log.d("main", main)

            val regexID =
                Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|/e/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
            val id = regexID.findAll(url).map {
                it.value.replace(Regex("(embed-|/e/)"), "")
            }.first()
//        val master = "$main/sources48/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
            val master = "$main/sources48/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
            val headers = mapOf(
                "watchsb" to "sbstream",
            )
            val mapped = app.get(
                master.lowercase(),
                headers = headers,
                referer = url,
            ).parsedSafe<Main>()
            // val urlmain = mapped.streamData.file.substringBefore("/hls/")
            M3u8Helper.generateM3u8(
                name,
                mapped?.streamData?.file.toString(),
                url,
                headers = headers
            ).forEach(callback)
        }

        return true
    }
}

class Sbcloud : StreamSB() {
    override var mainUrl = "https://sbcloud1.com"
}

class StreamSB6 : StreamSB() {
    override var mainUrl = "https://embedsb.com"
}