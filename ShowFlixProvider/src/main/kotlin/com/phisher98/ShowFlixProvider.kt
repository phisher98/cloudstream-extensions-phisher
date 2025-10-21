package com.phisher98

import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ShowFlixProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://showflix.store"
    override var name = "ShowFlix"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val installationID = "60f6b1a7-8860-4edf-b255-6bc465b6c704"


    data class TVAll(
        @JsonProperty("results") var results: List<TVResult> = listOf()
    )

    data class TVResult(
        @JsonProperty("objectId") var objectId: String,
        @JsonProperty("name") var name: String,
        @JsonProperty("posterURL") var posterURL: String?,
        @JsonProperty("releaseYear") var releaseYear: Int?,
        @JsonProperty("backdropURL") var backdropURL: String?,
        @JsonProperty("genres") var genres: List<String> = listOf(),
        @JsonProperty("storyline") var storyline: String?,
        @JsonProperty("rating") var rating: String?,
        @JsonProperty("tmdbId") var tmdbId: Int?,
        @JsonProperty("hdLink") var hdLink: String?,
        @JsonProperty("hubCloudLink") var hubCloudLink: String?,
        @JsonProperty("languages") var languages: List<String> = listOf(),
        @JsonProperty("createdAt") var createdAt: String?,
        @JsonProperty("updatedAt") var updatedAt: String?,
        @JsonProperty("seriesCategory") var seriesCategory: String?,

        )


    data class MovieAll(
        @JsonProperty("results") var results: List<MovieResults> = emptyList()
    )

    data class MovieResults(
        @JsonProperty("objectId") val objectId: String? = null,
        @JsonProperty("name") val name: String,
        @JsonProperty("posterURL") val posterURL: String? = null,
        @JsonProperty("releaseYear") val releaseYear: Int? = null,
        @JsonProperty("backdropURL") val backdropURL: String? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
        @JsonProperty("storyline") val storyline: String? = null,
        @JsonProperty("rating") val rating: String? = null,
        @JsonProperty("tmdbId") val tmdbId: Int? = null,
        @JsonProperty("embedLinks") val embedLinks: EmbedLinks? = null,
        @JsonProperty("hdLink") val hdLink: String? = null,
        @JsonProperty("hubCloudLink") val hubCloudLink: String? = null,
        @JsonProperty("languages") val languages: List<String>? = null,
        @JsonProperty("originalURL") val originalURL: String? = null,
        @JsonProperty("goFile") val goFile: String? = null,
        //Remove Category not Needed for Fix Recommendation
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("drive") val drive: String? = null,
        @JsonProperty("createdAt") val createdAt: String? = null,
        @JsonProperty("updatedAt") val updatedAt: String? = null
    )

    data class EmbedLinks(
        @JsonProperty("upnshare") val upnshare: String? = null,
        @JsonProperty("streamruby") val streamruby: String? = null,
        @JsonProperty("streamwish") val streamwish: String? = null,
        @JsonProperty("vihide") val vihide: String? = null
    )


    data class MovieLinks(
        @JsonProperty("streamruby") var streamRuby: String? = null,
        @JsonProperty("upnshare") var upnshare: String? = null,
        @JsonProperty("streamwish") var streamWish: String? = null,
        @JsonProperty("vihide") var vihide: String? = null,
        @JsonProperty("hdlink") var hdLink: String? = null,
        @JsonProperty("originalURL") var originalURL: String? = null,
        @JsonProperty("drive") var drive: String? = null,
        @JsonProperty("goFile") var goFile: String? = null,
        @JsonProperty("hubCloudLink") var hubCloudLink: String? = null
    )


    private val MovieapiUrl = "https://parse.showflix.sbs/parse/classes/moviesv2"
    private val TVapiUrl    = "https://parse.showflix.sbs/parse/classes/seriesv2"
    private val Api = "https://parse.showflix.sbs/parse/classes"

    private suspend fun queryMovieApi(query: String): NiceResponse {

        val req = (if (query.isBlank()) """{"where":{},"limit":20,"order":"-createdAt","count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""" else """{"where":{"languages":{"${"$"}in":["$query"]}},"limit":20,"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""").toRequestBody("text/plain".toMediaTypeOrNull())
        return app.post(
            MovieapiUrl,
            requestBody = req,
            referer = "$mainUrl/"
        )
    }

    private suspend fun queryTVApi(query: String): NiceResponse {
        val req = (if (query.isBlank()) """{"where":{},"limit":20,"order":"-createdAt","count":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""" else """{"where":{"languages":{"${"$"}in":["$query"]}},"limit":20,"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""").toRequestBody("text/plain".toMediaTypeOrNull())
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
        """Tamil""" to tamilMovies,
        """Tamil Dubbed""" to dubbedMovies,
        """English""" to englishMovies,
        """Telugu""" to teluguMovies,
        """Hindi""" to hindiMovies,
        """Malayalam""" to malayalamMovies,
        //TV Shows
        "" to trendingShows,
        """Tamil""" to tamilShows,
        """Tamil Dubbed""" to dubbedShows,
        """English""" to englishShows,
        """Telugu""" to teluguShows,
        """Hindi""" to hindiShows,
        """Malayalam""" to malayalamShows
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val elements = ArrayList<HomePageList>()
        val query = request.data.format(page)
        val Movielist = queryMovieApi(
            query
        ).parsed<MovieAll>().results

        val TVlist = queryTVApi(
            query
        ).parsed<TVAll>().results
        if (request.name.contains("Movies")) {
            val home =
                Movielist.map {
                    newMovieSearchResponse(
                        it.name,
                        "$mainUrl/movie/${it.objectId}",
                        TvType.Movie
                    ) {
                        this.posterUrl = it.posterURL
                        this.quality = SearchQuality.HD
                    }
                }
            elements.add(HomePageList(request.name, home))
        } else {
            val home =
                TVlist.map {
                    newTvSeriesSearchResponse(
                        it.name,
                        "$mainUrl/series/${it.objectId}",
                        TvType.TvSeries
                    ) {
                        this.posterUrl = it.posterURL
                        this.quality = SearchQuality.HD
                    }
                }
            elements.add(HomePageList(request.name, home))
        }
        return newHomePageResponse(elements, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val MovieSearchreq = """{"where":{"name":{"${"$"}regex":"$query","${"$"}options":"i"}},"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        val TVSearchreq = """{"where":{"name":{"${"$"}regex":"$query","${"$"}options":"i"}},"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val MovieResults =
            app.post(MovieapiUrl, requestBody = MovieSearchreq, referer = "$mainUrl/")
                .parsed<MovieAll>().results

        val TVResults = app.post(TVapiUrl, requestBody = TVSearchreq, referer = "$mainUrl/")
            .parsed<TVAll>().results

        val Movies = MovieResults.map {
            newMovieSearchResponse(
                it.name,
                "$mainUrl/movie/${it.objectId}",
                TvType.Movie
            ) {
                this.posterUrl = it.posterURL
                this.quality = SearchQuality.HD
            }
        }
        val TVSeries = TVResults.map {
            newTvSeriesSearchResponse(
                it.name,
                "$mainUrl/series/${it.objectId}",
                TvType.TvSeries
            ) {
                this.posterUrl = it.posterURL
                this.quality = SearchQuality.HD
            }
        }
        val merge = Movies + TVSeries
        return merge.sortedBy { -FuzzySearch.partialRatio(it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), query.lowercase()) }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.contains("movie")) {
            val MovieobjID = url.removePrefix("$mainUrl/movie/")
            val MovieLoadreq = """{"where":{"objectId":"$MovieobjID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"60f6b1a7-8860-4edf-b255-6bc465b6c704"}""".toRequestBody("text/plain".toMediaTypeOrNull())
            val data = app.post(MovieapiUrl, requestBody = MovieLoadreq, referer = "$mainUrl/").toString()

            val Movieresp = tryParseJson<MovieAll>(data)?.results
            val Movieit = Movieresp?.firstOrNull() ?: error("No movie found")

            val title = Movieit.name
            val yearRegex = Regex("""\((\d{4})\)""")
            val year = yearRegex.find(title)?.groupValues?.get(1)?.toIntOrNull()

            val poster = Movieit.posterURL
            val backdrop = Movieit.backdropURL
            val plot = Movieit.storyline
            val rating = Movieit.rating

            val recQuery = when {
                Movieit.category?.contains("Dubbed", ignoreCase = true) == true    -> "Tamil Dubbed"
                Movieit.category?.contains("Tamil", ignoreCase = true) == true     -> "Tamil"
                Movieit.category?.contains("English", ignoreCase = true) == true   -> "English"
                Movieit.category?.contains("Hindi", ignoreCase = true) == true     -> "Hindi"
                Movieit.category?.contains("Malayalam", ignoreCase = true) == true -> "Malayalam"
                else -> ""
            }
            val recommendations = queryMovieApi(
                recQuery
            ).parsed<MovieAll>().results.map{
                newMovieSearchResponse(
                    it.name,
                    "$mainUrl/movie/${it.objectId}",
                    TvType.Movie
                ) {
                    this.posterUrl = it.posterURL
                    this.quality = SearchQuality.HD
                }
            }

            return newMovieLoadResponse(
                title,
                "$mainUrl/movie/${Movieit.objectId}",
                TvType.Movie,
                MovieLinks(
                    Movieit.embedLinks?.streamruby,
                    Movieit.embedLinks?.upnshare,
                    Movieit.embedLinks?.streamwish,
                    Movieit.embedLinks?.vihide,
                    Movieit.hdLink,
                    Movieit.originalURL,
                    Movieit.drive,
                    Movieit.goFile,
                    Movieit.hubCloudLink,
                ).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.score = Score.from10(rating)
                this.backgroundPosterUrl = backdrop
                this.recommendations = recommendations
            }
        } else {
            val TVobjID =
                url.removePrefix("$mainUrl/series/")
            val TVLoadreq =
                """{"where":{"objectId":"$TVobjID"},"limit":1,"_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"$installationID"}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val TVresp = app.post(TVapiUrl, requestBody = TVLoadreq, referer = "$mainUrl/")
                .toString().removePrefix("""{"results":[""").removeSuffix("]}")
            val TVit = parseJson<TVResult>(TVresp)
            val title = TVit.name
            val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
            val year = yearRegex.find(title)?.value
                ?.toIntOrNull()
            val poster = TVit.posterURL
            val backdrop = TVit.backdropURL
            val plot = TVit.storyline
            val rating = TVit.rating
            val recQuery = when(TVit.seriesCategory != null) {
                TVit.seriesCategory.toString().contains("Dubbed")    -> """Tamil Dubbed"""
                TVit.seriesCategory.toString().contains("Tamil")     -> """Tamil"""
                TVit.seriesCategory.toString().contains("English")   -> """English"""
                TVit.seriesCategory.toString().contains("Hindi")     -> """Hindi"""
                TVit.seriesCategory.toString().contains("Malayalam") -> """Malayalam"""
                else -> ""
            }
            val recommendations = queryTVApi(
                recQuery
            ).parsed<TVAll>().results.map {
                newTvSeriesSearchResponse(
                    it.name,
                    "$mainUrl/series/${it.objectId}",
                    TvType.TvSeries
                ) {
                    this.posterUrl = it.posterURL
                    this.quality = SearchQuality.HD
                }
            }
            val seriesId = TVit.objectId
            val result = getSeasonsWithEpisodes(seriesId)
            val episodes = result.map { (seasonName, episodes) ->
                val seasonNum = Regex("\\d+").find(seasonName)?.value?.toInt()
                episodes.mapIndexed { _, data ->
                    val linksJson = MovieLinks(
                        data.embedLinks?.streamruby,
                        data.embedLinks?.upnshare,
                        data.embedLinks?.streamwish,
                        data.embedLinks?.vihide,
                    ).toJson()

                    newEpisode(linksJson) {
                        this.season = seasonNum
                        this.episode = data.episodeNumber
                        this.posterUrl = backdrop
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
                this.score = Score.from10(rating)
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
        val root: Loadlinks? = tryParseJson<Loadlinks>(data)
        val urls = root?.toEmbedUrls()
        urls?.amap { iframe ->
            if (iframe.contains(".mkv"))
            {
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        iframe,
                        INFER_TYPE
                    )
                    {
                        this.referer=url
                        this.quality= Qualities.P1080.value
                        this.headers=headers

                    }
                )
            }
            loadSourceNameExtractor(
                "Showflix ",
                iframe,
                "",
                subtitleCallback,
                callback
            )
        }
        return true
    }

    private suspend fun getSeasonsWithEpisodes(seriesId: String): List<Pair<String, List<EpisodeDetails>>> {
        val seasonRequest = """
        {
            "where": {"seriesId": "$seriesId"},
            "_method": "GET",
            "_ApplicationId": "SHOWFLIXAPPID",
            "_JavaScriptKey": "SHOWFLIXMASTERKEY",
            "_ClientVersion": "js3.4.1",
            "_InstallationId": "60f6b1a7-8860-4edf-b255-6bc465b6c704"
        }
    """.trimIndent().toRequestBody("text/plain".toMediaTypeOrNull())

        val seasonResponseText = app.post("$Api/seasonv2", requestBody = seasonRequest, referer = "https://showflix.store/").toString()
        val seasonResult = parseJson<SeasonResult>(seasonResponseText)

        val allSeasons = mutableListOf<Pair<String, List<EpisodeDetails>>>()

        for (season in seasonResult.results) {
            val episodeRequest = """
            {
                "where": {"seasonId": "${season.objectId}"},
                "_method": "GET",
                "_ApplicationId": "SHOWFLIXAPPID",
                "_JavaScriptKey": "SHOWFLIXMASTERKEY",
                "_ClientVersion": "js3.4.1",
                "_InstallationId": "60f6b1a7-8860-4edf-b255-6bc465b6c704"
            }
        """.trimIndent().toRequestBody("text/plain".toMediaTypeOrNull())

            val episodeResponseText = app.post("$Api/episodev2", requestBody = episodeRequest, referer = "https://showflix.store/").toString()
            val episodeResult = parseJson<EpisodeResult>(episodeResponseText)

            val episodes = episodeResult.results.map {
                EpisodeDetails(
                    objectId = it.objectId,
                    name = it.name,
                    seasonId = it.seasonId,
                    seasonNumber = it.seasonNumber,
                    episodeNumber = it.episodeNumber,
                    embedLinks = it.embedLinks,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
            allSeasons.add(season.name to episodes)
        }
        return allSeasons
    }

    private fun Loadlinks.toEmbedUrls(): List<String> {
        return listOfNotNull(
            streamwish.takeIf { it.isNotBlank() }?.let { "https://embedwish.com/e/$it" },
            streamruby.takeIf { it.isNotBlank() }?.let { "https://rubyvidhub.com/embed-$it.html" },
            upnshare.takeIf { it.isNotBlank() }?.let { "https://showflix.upns.one/#$it" },
            vihide.takeIf { it.isNotBlank() }?.let { "https://smoothpre.com/v/$it.html" },
            originalUrl,
            hdlink
        )
    }
}


suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "$source[${link.source}]",
                    "$source[${link.source}]",
                    link.url,
                ) {
                    this.quality = link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}