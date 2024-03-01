package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.random.Random

class ShowFlixProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://showflix.lol"
    override var name = "ShowFlix"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val installationID = "845078d0-0602-48b5-be7f-9afd34248cc1"

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
        @JsonProperty("streamhide"        ) var streamhide        : Seasons = Seasons(),
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
        @JsonProperty("streamlink" ) val streamsb  : String?,
        @JsonProperty("streamhide" ) val streamhide: String?,
        @JsonProperty("sharedisk"  ) val sharedisk : String?,
        @JsonProperty("filelions"  ) val filelions : String?,
        @JsonProperty("streamwish" ) val streamwish : String?,
        @JsonProperty("streamruby" ) val streamruby : String?,
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

    private val MovieapiUrl = "https://parse.showflix.online/parse/classes/movies"
    private val TVapiUrl    = "https://parse.showflix.online/parse/classes/series"

    private suspend fun queryMovieApi(skip: Int, query: String): NiceResponse {
        val req =
            """{"where":{"category":{"${"$"}regex":"$query"}},"order":"-createdAt","limit":40,"skip":$skip,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        Log.d("JSON", req.toString())
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
        Log.d("JSON", req.toString())
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

        val check = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "$mainUrl/")
        Log.d("Mandikcheck", check.toString())

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
            Log.d("MandikLoadReq", "Expected Link: $MovieLoadreq")
            Log.d("MandikMovieID", "Expected Link: $MovieobjID")

            val Movieresp = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "$mainUrl/")
                .toString().removePrefix("""{"results":[""").removeSuffix("]}")
            Log.d("MandikMovieID", Movieresp)
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
            Log.d("Mandik", "Expected Link: $recQuery")
            Log.d("Mandik", "Expected Link: $Movieit")
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
            Log.d("Mandik", "Expected Link: $recommendations")

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
            Log.d("TVResult", recommendations.toString())

            val seasonDataList = listOf(TVit.Seasons.Seasons,
                TVit.streamwish.Seasons, TVit.filelions.Seasons, TVit.streamruby.Seasons, TVit.streamhide.Seasons)
            val combinedSeasons = mutableMapOf<String, MutableMap<Int, List<String?>>>()
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

    // Url Extractor Util
    fun splitUrl(url: String): Pair<String, String> {
        val urlRegex = Regex("(https?://)?([a-zA-Z0-9.-]+)(/.*)?")
        val match = urlRegex.find(url)
        return Pair(match?.groups?.get(2)?.value.toString(), match?.groups?.get(3)?.value.toString())
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

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private fun encodeId(id: String): String {
        val code = "${createHashTable()}||$id||${createHashTable()}||streamsb"
        return code.toCharArray().joinToString("") { char ->
            char.code.toString(16)
        }
    }

    private fun createHashTable(): String {
        return buildString {
            repeat(12) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    private suspend fun loadStreamSB(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {

        val (main, path) = splitUrl(url)
        val regexID =
            Regex("(embed-[a-zA-Z\\d]{0,8}[a-zA-Z\\d_-]+|/e/[a-zA-Z\\d]{0,8}[a-zA-Z\\d_-]+)")
        val id = regexID.findAll(url).map {
            it.value.replace(Regex("(embed-|/e/)"), "")
        }.first()
        val master = "$main/375664356a494546326c4b797c7c6e756577776778623171737/${encodeId(id)}"
        val headers = mapOf(
            "watchsb" to "sbstream",
        )
        Log.d("Mandik", "Expected Link: $id")
        Log.d("Mandik", "Expected Link: $master")
        Log.d("Mandik", "Expected Link: $headers")
        val mapped = app.get(
            master.lowercase(),
            headers = headers,
            referer = url,
        ).parsedSafe<Main>()
        // val urlmain = mapped.streamData.file.substringBefore("/hls/")

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    name + "-StreamSb",
                    name + "-StreamSb",
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

    private suspend fun loadStreamHide(
        main: String,
        id: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {

        val url = "https://$main$id.html"
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to main,
        )
        val response = app.get(url, referer = url).document
        response.select("body > script[type=text/javascript]").map { script ->
            if (script.data().contains(Regex("eval\\(function\\(p,a,c,k,e,[rd]"))) {
                val unpackedscript = getAndUnpack(script.data())
                val m3u8Regex = Regex("file.\"(.*?m3u8.*?)\"")
                val m3u8 = m3u8Regex.find(unpackedscript)?.destructured?.component1() ?: ""
                val cleanMain = main.replace(Regex("/.*"), "")
                if (m3u8.isNotEmpty()) {
                    callback.invoke(
                        ExtractorLink(
                            "$name-$cleanMain",
                            "$name-$cleanMain",
                            m3u8,
                            "https://$cleanMain",
                            Qualities.Unknown.value,
                            true,
                            headers
                        )
                    )
                }
            }
        }
    }

    private suspend fun loadShareDisk(
        id: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {

        val main = "https://us-central1-affiliate2apk.cloudfunctions.net/get_data?shortid="
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

    private suspend fun loadStreamWish(
        main: String,
        id: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {

        val url = "https://$main/e/$id"
        val doc = app.get(url).text

        val linkRegex = Regex("sources:.\\[\\{file:\"(.*?)\"")
        val link = linkRegex.find(doc)?.groups?.get(1)?.value.toString()
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to main,

        )
        Log.d("Mandikurl", "Expected Link: $url")
        Log.d("Mandikdoc", "Expected Link: $doc")
        Log.d("MandiklinkRegex", "Expected Link: $linkRegex")
        Log.d("Mandik", "Expected Link: $link")
        Log.d("Mandik", "Expected Link: $headers")

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    "$name-$main",
                    "$name-$main",
                    link,
                    "https://$main/",
                    Qualities.Unknown.value,
                    true,
                    headers
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
        if (data.contains("sharedisk")) {
            val m = parseJson<MovieLinks>(data)
            //load links in a forEach so it doesn't fail iof one of the links are empty
            m.streamsb?.let   { it1 -> loadStreamSB(it1, subtitleCallback, callback) }
            m.streamwish?.let { it1 -> loadStreamWish("streamwish.to", it1, subtitleCallback, callback) }
            m.streamruby?.let { it1 -> loadStreamWish("streamruby.com", it1, subtitleCallback, callback) }
            m.streamhide?.let { it1 -> loadStreamHide("streamhide.com/e/", it1,  subtitleCallback, callback) }
            m.filelions?.let  { it1 -> loadStreamHide("filelions.to/v/", it1, subtitleCallback, callback) }
            m.sharedisk?.let  { it1 -> loadShareDisk(it1, subtitleCallback, callback) }

        } else {
            val (main, id) = splitUrl(data)
            if (data.contains("filelions")) {
                loadStreamHide("$main/v/", id, subtitleCallback, callback)
            } else {
                loadStreamWish(main, id, subtitleCallback, callback)
            }
        }

        return true
    }
}
