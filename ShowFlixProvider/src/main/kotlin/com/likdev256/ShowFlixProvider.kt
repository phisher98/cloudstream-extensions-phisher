package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.random.Random

class ShowFlixProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://showflix.click"
    override var name = "ShowFlix"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val installationID = "1153d166-be0a-46ad-9197-cb77247722f9"

    data class Seasons(
        var Seasons: Map<String, List<String?>> = mapOf()
    )

    data class Streamhide(
        var Streamhide: Map<String, List<String?>> = mapOf()
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
        @JsonProperty("seriesLanguage"    ) var seriesLanguage    : String?,
        @JsonProperty("seriesTotalSeason" ) var seriesTotalSeason : String?,
        @JsonProperty("createdAt"         ) var createdAt         : String?,
        @JsonProperty("updatedAt"         ) var updatedAt         : String?,
        @JsonProperty("streamhide"        ) var streamhide        : Streamhide = Streamhide(),
        @JsonProperty("hdlink"            ) var hdlink            : String?
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
        @JsonProperty("streamlink" ) var streamlink : String,
        @JsonProperty("language"   ) var language   : String?,
        @JsonProperty("hdlink"     ) var hdlink     : String?,
        @JsonProperty("sharedisk"  ) var sharedisk  : String?,
        @JsonProperty("streamhide" ) var streamhide : String?,
        @JsonProperty("shrink"     ) var shrink     : Boolean?,
        @JsonProperty("createdAt"  ) var createdAt  : String?,
        @JsonProperty("updatedAt"  ) var updatedAt  : String?
    )

    data class MovieAll(
        @JsonProperty("results") var results: ArrayList<MovieResults> = arrayListOf()
    )

    data class MovieLinks(
        @JsonProperty("streamsb"  ) val streamsb  : String,
        @JsonProperty("streamhide") val streamhide: String?,
        @JsonProperty("sharedisk" ) val sharedisk : String?
    )

    data class GetShareDiskDl (
        @JsonProperty("uploaded_by"     ) var uploadedBy     : String?,
        @JsonProperty("type"            ) var type           : String?,
        @JsonProperty("video_url"       ) var videoUrl       : String?,
        @JsonProperty("title"           ) var title          : String?,
        @JsonProperty("online_playable" ) var onlinePlayable : Boolean?,
        @JsonProperty("date"            ) var date           : String?,
        @JsonProperty("size"            ) var size           : Long?   ,
        @JsonProperty("length"          ) var length         : Int?   ,
        @JsonProperty("download_data"   ) var downloadData   : String?,
        @JsonProperty("video_thumbnail" ) var videoThumbnail : String?,
        @JsonProperty("ad_type"         ) var adType         : String?,
        @JsonProperty("server_id"       ) var serverId       : String?
    )

    /*data class fullCount(
        @JsonProperty("results") var results: ArrayList<String> = arrayListOf(),
        @JsonProperty("count") var count: Int
    )*/

    private val MovieapiUrl = "https://parse.showflix.tk/parse/classes/movies"
    private val TVapiUrl    = "https://parse.showflix.tk/parse/classes/series"

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
        return HomePageResponse(elements, hasNext = true)
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
                    Movieit.sharedisk
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
            // Log.d("request", recQuery)
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

            val episodes = TVit.Seasons.Seasons.map { (seasonName, episodes) ->
                val seasonNum = Regex("\\d+").find(seasonName)?.value?.toInt()

                episodes.mapIndexed { epNum, data ->
                    //Log.d("Episodedata", data.toString())
                    //Log.d("EpisodeNum", epNum.toString())
                    Episode(
                        data = data.toString(),
                        season = seasonNum,
                        episode = epNum,
                        posterUrl = backdrop,
                    ) //Map<String, List<String>> = mapOf()
                }
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
                //addActors(actors)
                this.recommendations = recommendations
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

    private suspend fun loadStreamSBM3u8(
        url: String,
        main: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {

        val regexID =
            Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|/e/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
        val id = regexID.findAll(url).map {
            it.value.replace(Regex("(embed-|/e/)"), "")
        }.first()
        //val master = "$main/sources51/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
        val master = "$main/sources15/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
        val headers = mapOf(
            "watchsb" to "sbstream",
        )
        val mapped = app.get(
            master.lowercase(),
            headers = headers,
            referer = url,
        ).parsedSafe<Main>()
        // val urlmain = mapped.streamData.file.substringBefore("/hls/")

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    name + "-StreamSb_MultiAudio",
                    name + "-StreamSb_MultiAudio",
                    mapped?.streamData?.file.toString(),
                    url,
                    Qualities.Unknown.value,
                    true,
                    headers
                )
            )
        }
        mapped?.streamData?.subs?.map {sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    sub.label.toString(),
                    sub.file ?: return@map null,
                )
            )
        }
    }

    private suspend fun loadStreamHideM3u8(
        url: String,
        id: String,
        main: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {

        //val master = "$main/sources51/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
        val master = "$main/sources15/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
        val headers = mapOf(
            "watchsb" to "sbstream",
        )
        val mapped = app.get(
            master.lowercase(),
            headers = headers,
            referer = url,
        ).parsedSafe<Main>()
        // val urlmain = mapped.streamData.file.substringBefore("/hls/")
        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    name + "-StreamHide_MultiAudio",
                    name + "-StreamHide_MultiAudio",
                    mapped?.streamData?.file.toString(),
                    url,
                    Qualities.Unknown.value,
                    true,
                    headers
                )
            )
        }
        mapped?.streamData?.subs?.map {sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    sub.label.toString(),
                    sub.file ?: return@map null,
                )
            )
        }
    }

    private suspend fun loadShareDisk(
        id: String,
        main: String,
        callback: (ExtractorLink) -> Unit) {

        val link = parseJson<GetShareDiskDl>(
            app.get("$main$id").text
        ).videoUrl.toString()
        //Log.d("mybadlink", link)

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    name + "-ShareDisk",
                    name + "-ShareDisk",
                    link,
                    "",
                    Qualities.Unknown.value,
                    false
                )
            )
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        //Log.d("data", data)
            
        if (data.contains("sharedisk")) {
            val it = parseJson<MovieLinks>(data)
            //load streamsb links
            val streamsb = it.streamsb.replace(Regex("(/d/|embed-|/play/)"), "/e/")
            var main = "https://sbcloud1.com"
            loadStreamSBM3u8(streamsb, main, subtitleCallback, callback)

            if (it.sharedisk.isNullOrBlank().not()) {
                //load sharedisk links
                //Log.d("mybadsharedisk", it.sharedisk.toString())
                main = "https://us-central1-affiliate2apk.cloudfunctions.net/get_data?shortid="
                loadShareDisk(it.sharedisk.toString(), main, callback)
            }

            if (it.streamhide.isNullOrBlank().not()) {
                //load streamhide links
                main = "https://streamhide.to"
                val streamhide = "$main/e/${it.streamhide}.html"
                loadStreamHideM3u8(streamhide, it.streamhide.toString(), main, subtitleCallback, callback)
            }

        } else {
            val url = data.replace(Regex("(embed-|/play/)"), "/e/")
            //Log.d("url", url)
            val main = "https://sbcloud1.com"
            //Log.d("main", main)
            loadStreamSBM3u8(url, main, subtitleCallback, callback)
        }

        return true
    }
}
