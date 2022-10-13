package com.likdev256

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.net.URI
import java.net.URLDecoder
import com.fasterxml.jackson.annotation.JsonProperty

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
        "$mainUrl/lan/movie/Tamil" to "Tamil Movies",
        "$mainUrl/lan/movie/Tamil Dubbed" to "Tamil Dubbed Movies",
        "$mainUrl/lan/series/Tamil" to "Tamil TV Series",
        "$mainUrl/lan/series/Tamil Dubbed" to "Tamil Dubbed TV Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        //Log.d("request", request.toString())
        Log.d("Check", request.data)
        //Log.d("Page", page.toString())

        val document = app.get(request.data).document //if (page == 1) {
            //app.get(request.data).document
        //} else {
            //app.get(request.data + "page/" + page).document
        //}

        val reqMain = mapOf(
            "_ApplicationId" to "SHOWFLIXAPPID",
            "_ClientVersion" to "js3.4.1",
            "_InstallationId" to "2573d2b7-ef8f-48f0-9e91-8e5c2beb220d",
            "_method" to "GET",
            "limit" to "50",
            "order" to "-updatedAt",
            "where" to "{}"
        )

        val res = app.post(
            "https://parse.showflix.tk/parse/classes/movies",
            data = reqMain,
            referer = "https://showflix.in/"
        ).text

        Log.d("JSON", res)


        private data class MovieResults (
            @JsonProperty("objectId") var objectId : String?,
            @JsonProperty("movieName") var movieName : String?,
            @JsonProperty("rating") var rating : String?,
            @JsonProperty("storyline") var storyline : String?,
            @JsonProperty("poster") var poster : String?,
            @JsonProperty("backdrop") var backdrop : String?,
            @JsonProperty("category") var category : String?,
            @JsonProperty("streamlink") var streamlink : String?,
            @JsonProperty("language") var language : String?,
            @JsonProperty("createdAt") var createdAt : String?,
            @JsonProperty("updatedAt") var updatedAt : String?,
            @JsonProperty("hdlink") var hdlink : String?

        )
/*
        private data class Data(
            @JsonProperty("shows") val shows: Shows
        )

        private data class Shows(
            @JsonProperty("pageInfo") val pageInfo: PageInfo,
            @JsonProperty("edges") val edges: List<Edges>,
            @JsonProperty("__typename") val _typename: String
        )

        private data class Edges(
            @JsonProperty("_id") val Id: String?,
            @JsonProperty("name") val name: String,
            @JsonProperty("englishName") val englishName: String?,
            @JsonProperty("nativeName") val nativeName: String?,
            @JsonProperty("thumbnail") val thumbnail: String?,
            @JsonProperty("type") val type: String?,
            @JsonProperty("season") val season: Season?,
            @JsonProperty("score") val score: Double?,
            @JsonProperty("airedStart") val airedStart: AiredStart?,
            @JsonProperty("availableEpisodes") val availableEpisodes: AvailableEpisodes?,
            @JsonProperty("availableEpisodesDetail") val availableEpisodesDetail: AvailableEpisodesDetail?,
            @JsonProperty("studios") val studios: List<String>?,
            @JsonProperty("description") val description: String?,
            @JsonProperty("status") val status: String?,
        )

        private data class AvailableEpisodes(
            @JsonProperty("sub") val sub: Int,
            @JsonProperty("dub") val dub: Int,
            @JsonProperty("raw") val raw: Int
        )

        private data class AiredStart(
            @JsonProperty("year") val year: Int,
            @JsonProperty("month") val month: Int,
            @JsonProperty("date") val date: Int
        )

        private data class Season(
            @JsonProperty("quarter") val quarter: String,
            @JsonProperty("year") val year: Int
        )

        private data class PageInfo(
            @JsonProperty("total") val total: Int,
            @JsonProperty("__typename") val _typename: String
        )

        private data class AllAnimeQuery(
            @JsonProperty("data") val data: Data
        )

        data class RandomMain(
            @JsonProperty("data") var data: DataRan? = DataRan()
        )

        data class DataRan(
            @JsonProperty("queryRandomRecommendation") var queryRandomRecommendation: ArrayList<QueryRandomRecommendation> = arrayListOf()
        )

        data class QueryRandomRecommendation(
            @JsonProperty("_id") val Id: String? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("englishName") val englishName: String? = null,
            @JsonProperty("nativeName") val nativeName: String? = null,
            @JsonProperty("thumbnail") val thumbnail: String? = null,
            @JsonProperty("airedStart") val airedStart: String? = null,
            @JsonProperty("availableChapters") val availableChapters: String? = null,
            @JsonProperty("availableEpisodes") val availableEpisodes: String? = null,
            @JsonProperty("__typename") val _typename: String? = null
        )
*/
        override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
            val items = ArrayList<HomePageList>()
        /*    val urls = listOf(
//            Pair(
//                "Top Anime",
//                """$mainUrl/graphql?variables={"type":"anime","size":30,"dateRange":30}&extensions={"persistedQuery":{"version":1,"sha256Hash":"276d52ba09ca48ce2b8beb3affb26d9d673b22f9d1fd4892aaa39524128bc745"}}"""
//            ),
                // "countryOrigin":"JP" for Japanese only
                Pair(
                    "Recently updated",
                    """$mainUrl/graphql?variables={"search":{"allowAdult":false,"allowUnknown":false},"limit":30,"page":1,"translationType":"dub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"d2670e3e27ee109630991152c8484fce5ff5e280c523378001f9a23dc1839068"}}"""
                ),
            ) */

            val MovieQuery =
                """$mainUrl/graphql?variables={"format":"anime"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"21ac672633498a3698e8f6a93ce6c2b3722b29a216dcca93363bf012c360cd54"}}"""
            val getMovies = app.get(MovieQuery).text
            val jsonran = parseJson<MovieResults>(ranlink)
            val ranhome = jsonran.data?.queryRandomRecommendation?.map {
                newAnimeSearchResponse(it.name!!, "$mainUrl/anime/${it.Id}", fix = false) {
                    this.posterUrl = it.thumbnail
                    this.otherName = it.nativeName
                }
            }

            items.add(HomePageList("Random", ranhome!!))

            urls.apmap { (HomeName, url) ->
                val test = app.get(url).text
                val json = parseJson<AllAnimeQuery>(test)
                val home = ArrayList<SearchResponse>()
                val results = json.data.shows.edges.filter {
                    // filtering in case there is an anime with 0 episodes available on the site.
                    !(it.availableEpisodes?.raw == 0 && it.availableEpisodes.sub == 0 && it.availableEpisodes.dub == 0)
                }
                results.map {
                    home.add(
                        newAnimeSearchResponse(it.name, "$mainUrl/anime/${it.Id}", fix = false) {
                            this.posterUrl = it.thumbnail
                            this.year = it.airedStart?.year
                            this.otherName = it.englishName
                            addDub(it.availableEpisodes?.dub)
                            addSub(it.availableEpisodes?.sub)
                        })
                }
                items.add(HomePageList(HomeName, home))
            }

            if (items.size <= 0) throw ErrorLoadingException()
            return HomePageResponse(items)
        }

        override suspend fun search(query: String): List<SearchResponse> {
            val link =
                """$mainUrl/allanimeapi?variables={"search":{"allowAdult":false,"allowUnknown":false,"query":"$query"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"9c7a8bc1e095a34f2972699e8105f7aaf9082c6e1ccd56eab99c2f1a971152c6"}}"""
            var res = app.get(link).text
            if (res.contains("PERSISTED_QUERY_NOT_FOUND")) {
                res = app.get(link).text
                if (res.contains("PERSISTED_QUERY_NOT_FOUND")) return emptyList()
            }
            val response = parseJson<AllAnimeQuery>(res)

            val results = response.data.shows.edges.filter {
                // filtering in case there is an anime with 0 episodes available on the site.
                !(it.availableEpisodes?.raw == 0 && it.availableEpisodes.sub == 0 && it.availableEpisodes.dub == 0)
            }

            return results.map {
                newAnimeSearchResponse(it.name, "$mainUrl/anime/${it.Id}", fix = false) {
                    this.posterUrl = it.thumbnail
                    this.year = it.airedStart?.year
                    this.otherName = it.englishName
                    addDub(it.availableEpisodes?.dub)
                    addSub(it.availableEpisodes?.sub)
                }
            }
        }

        private data class AvailableEpisodesDetail(
            @JsonProperty("sub") val sub: List<String>,
            @JsonProperty("dub") val dub: List<String>,
            @JsonProperty("raw") val raw: List<String>
        )


        override suspend fun load(url: String): LoadResponse? {
            val rhino = Context.enter()
            rhino.initSafeStandardObjects()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()

            val html = app.get(url).text
            val soup = Jsoup.parse(html)

            val script = soup.select("script").firstOrNull {
                it.html().contains("window.__NUXT__")
            } ?: return null

            val js = """
            const window = {}
            ${script.html()}
            const returnValue = JSON.stringify(window.__NUXT__.fetch[0].show)
        """.trimIndent()

            rhino.evaluateString(scope, js, "JavaScript", 1, null)
            val jsEval = scope.get("returnValue", scope) ?: return null
            val showData = parseJson<Edges>(jsEval as String)

            val title = showData.name
            val description = showData.description
            val poster = showData.thumbnail

            val episodes = showData.availableEpisodes.let {
                if (it == null) return@let Pair(null, null)
                Pair(if (it.sub != 0) ((1..it.sub).map { epNum ->
                    Episode(
                        "$mainUrl/anime/${showData.Id}/episodes/sub/$epNum", episode = epNum
                    )
                }) else null, if (it.dub != 0) ((1..it.dub).map { epNum ->
                    Episode(
                        "$mainUrl/anime/${showData.Id}/episodes/dub/$epNum", episode = epNum
                    )
                }) else null)
            }

            val characters = soup.select("div.character > div.card-character-box").mapNotNull {
                val img = it?.selectFirst("img")?.attr("src") ?: return@mapNotNull null
                val name = it.selectFirst("div > a")?.ownText() ?: return@mapNotNull null
                val role = when (it.selectFirst("div > .text-secondary")?.text()?.trim()) {
                    "Main" -> ActorRole.Main
                    "Supporting" -> ActorRole.Supporting
                    "Background" -> ActorRole.Background
                    else -> null
                }
                Pair(Actor(name, img), role)
            }

            // bruh, they use graphql
            //val recommendations = soup.select("#suggesction > div > div.p > .swipercard")?.mapNotNull {
            //    val recTitle = it?.selectFirst(".showname > a") ?: return@mapNotNull null
            //    val recName = recTitle.text() ?: return@mapNotNull null
            //    val href = fixUrlNull(recTitle.attr("href")) ?: return@mapNotNull null
            //    val img = it.selectFirst(".image > img").attr("src") ?: return@mapNotNull null
            //    AnimeSearchResponse(recName, href, this.name, TvType.Anime, img)
            //}

            return newAnimeLoadResponse(title, url, TvType.Anime) {
                posterUrl = poster
                year = showData.airedStart?.year

                addEpisodes(DubStatus.Subbed, episodes.first)
                addEpisodes(DubStatus.Dubbed, episodes.second)
                addActors(characters)
                //this.recommendations = recommendations

                showStatus = getStatus(showData.status.toString())

                plot = description?.replace(Regex("""<(.*?)>"""), "")
            }
        }

        private val embedBlackList = listOf(
            "https://mp4upload.com/",
            "https://streamsb.net/",
            "https://dood.to/",
            "https://videobin.co/",
            "https://ok.ru",
            "https://streamlare.com",
        )

        private fun embedIsBlacklisted(url: String): Boolean {
            embedBlackList.forEach {
                if (it.javaClass.name == "kotlin.text.Regex") {
                    if ((it as Regex).matches(url)) {
                        return true
                    }
                } else {
                    if (url.contains(it)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun String.sanitize(): String {
            var out = this
            listOf(Pair("\\u002F", "/")).forEach {
                out = out.replace(it.first, it.second)
            }
            return out
        }

        private data class Links(
            @JsonProperty("link") val link: String,
            @JsonProperty("hls") val hls: Boolean?,
            @JsonProperty("resolutionStr") val resolutionStr: String,
            @JsonProperty("src") val src: String?
        )

        private data class AllAnimeVideoApiResponse(
            @JsonProperty("links") val links: List<Links>
        )

        private data class ApiEndPoint(
            @JsonProperty("episodeIframeHead") val episodeIframeHead: String
        )

        private suspend fun getM3u8Qualities(
            m3u8Link: String,
            referer: String,
            qualityName: String,
        ): List<ExtractorLink> {
            return M3u8Helper.generateM3u8(
                this.name,
                m3u8Link,
                referer,
                name = "${this.name} - $qualityName"
            )
        }

        override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            var apiEndPoint =
                parseJson<ApiEndPoint>(app.get("$mainUrl/getVersion").text).episodeIframeHead
            if (apiEndPoint.endsWith("/")) apiEndPoint =
                apiEndPoint.slice(0 until apiEndPoint.length - 1)

            val html = app.get(data).text

            val sources = Regex("""sourceUrl[:=]"(.+?)"""").findAll(html).toList()
                .map { URLDecoder.decode(it.destructured.component1().sanitize(), "UTF-8") }
            sources.apmap {
                safeApiCall {
                    var link = it.replace(" ", "%20")
                    if (URI(link).isAbsolute || link.startsWith("//")) {
                        if (link.startsWith("//")) link = "https:$it"

                        if (Regex("""streaming\.php\?""").matches(link)) {
                            // for now ignore
                        } else if (!embedIsBlacklisted(link)) {
                            if (URI(link).path.contains(".m3u")) {
                                getM3u8Qualities(link, data, URI(link).host).forEach(callback)
                            } else {
                                callback(
                                    ExtractorLink(
                                        "AllAnime - " + URI(link).host,
                                        "",
                                        link,
                                        data,
                                        Qualities.P1080.value,
                                        false
                                    )
                                )
                            }
                        }
                    } else {
                        link = apiEndPoint + URI(link).path + ".json?" + URI(link).query
                        val response = app.get(link)

                        if (response.code < 400) {
                            val links = parseJson<AllAnimeVideoApiResponse>(response.text).links
                            links.forEach { server ->
                                if (server.hls != null && server.hls) {
                                    getM3u8Qualities(
                                        server.link,
                                        "$apiEndPoint/player?uri=" + (if (URI(server.link).host.isNotEmpty()) server.link else apiEndPoint + URI(
                                            server.link
                                        ).path),
                                        server.resolutionStr
                                    ).forEach(callback)
                                } else {
                                    callback(
                                        ExtractorLink(
                                            "AllAnime - " + URI(server.link).host,
                                            server.resolutionStr,
                                            server.link,
                                            "$apiEndPoint/player?uri=" + (if (URI(server.link).host.isNotEmpty()) server.link else apiEndPoint + URI(
                                                server.link
                                            ).path),
                                            Qualities.P1080.value,
                                            false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return true
        }

    }