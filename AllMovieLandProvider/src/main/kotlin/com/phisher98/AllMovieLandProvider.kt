package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import org.jsoup.nodes.Element
import java.net.URI

class AllMovieLandProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://allmovieland.you"
    override var name = "AllMovieLand"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon
    )

    private var sessionCookies: Map<String, String>? = null

    override val mainPage = mainPageOf(
        "$mainUrl/films/" to "Movies",
        "$mainUrl/bollywood/" to "Bollywood Movies",
        "$mainUrl/hollywood/" to "Hollywood Movies",
        "$mainUrl/series/" to "TV Shows",
        "$mainUrl/cartoon/" to "Cartoons"
    )

    private suspend fun querySearchApi(query: String): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("do", "search")
            .addEncoded("subaction", "search")
            .addEncoded("search_start", "0")
            .addEncoded("full_search", "0")
            .addEncoded("result_from", "1")
            .addEncoded("story", query)
            .build()

        return app.post(
            "$mainUrl/index.php?do=opensearch", //$mainUrl/engine/ajax/controller.php?mod=search
            requestBody = body,
            referer = "$mainUrl/",
            cookies = ensureSession()
        )
    }

    private suspend fun ensureSession(forceRefresh: Boolean = false): Map<String, String> {
        if (sessionCookies == null || forceRefresh) {
            val sessionId = app.get("$mainUrl/").cookies["PHPSESSID"].orEmpty()
            sessionCookies = if (sessionId.isNotBlank()) {
                mapOf("PHPSESSID" to sessionId)
            } else {
                emptyMap()
            }
        }
        return sessionCookies.orEmpty()
    }

    private suspend fun getDocument(url: String, referer: String? = null) =
        app.get(url, referer = referer, cookies = ensureSession()).document

    private fun extractJsonObject(script: String): String? {
        val start = script.indexOf('{')
        val end = script.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return script.substring(start, end + 1)
    }

    private suspend fun getDlPayload(link: String, refererUrl: String, playerDomain: String): StreamPayload {
        val baseurl = getBaseUrl(link)
        val doc = app.get(link, referer = refererUrl, cookies = ensureSession()).document
        val jsonString = extractJsonObject(doc.select("body > script:last-child").html()) ?: return StreamPayload(
            playerDomain = playerDomain,
            tokenKey = "",
            items = emptyList()
        )
        val json = parseJson<Getfile>(jsonString)
        val tokenKey = json.key.orEmpty()
        val jsonfile = if (json.file.startsWith("http")) json.file else baseurl + json.file
        val m3u8Langs = app.post(
            jsonfile,
            referer = link,
            headers = mapOf(
                "X-CSRF-TOKEN" to tokenKey,
            ),
        ).text.replace(Regex("(,)\\s*\\[]"), "")
        return StreamPayload(
            playerDomain = playerDomain,
            tokenKey = tokenKey,
            items = parseJson<List<Extract>>(m3u8Langs)
        )
    }

    private suspend fun getM3u8(playerDomain: String, tokenKey: String, file: String?): String {
        return app.post(
            "$playerDomain/playlist/$file.txt",
            headers = mapOf(
                "X-CSRF-TOKEN" to tokenKey,
            ),
            referer = "$mainUrl/"
        ).text
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val cookies = ensureSession()
        val document = if (page == 1) {
            app.get(request.data, cookies = cookies).document
        } else {
            app.get(request.data + "/page/$page/", cookies = cookies).document
        }

        val home = document.select("article.short-mid").mapNotNull {
                    it.toSearchResult(cookies)
                }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(cookies: Map<String, String>): SearchResponse? {
        val title = this.selectFirst("a > h3")?.text()?.trim() ?: return null
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(mainUrl + this.select("div.new-short__poster > a.new-short__poster--link > img").attr("data-src"))
        val checkType = this.select("span.new-short__cats").text()
        val type = if (checkType.contains("films", true)) TvType.Movie
        else if (checkType.contains("series", true)) TvType.TvSeries
        else TvType.Cartoon
        return when (type) {
            TvType.Movie -> {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    posterHeaders = cookies
                }
            }
            TvType.TvSeries -> {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    posterHeaders = cookies
                }
            }
            else -> {
                newMovieSearchResponse(title, href, TvType.Cartoon) {
                    this.posterUrl = posterUrl
                    posterHeaders = cookies
                }
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cookies = ensureSession()
        val searchList = querySearchApi(
            query
        ).document

        return searchList.select("article.short-mid").mapNotNull {
            it.toSearchResult(cookies)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val cookies = ensureSession()
        val doc = getDocument(url)
        val title = doc.selectFirst("h1.fs__title")?.text()?.trim()
            ?: return null
        val poster = fixUrlNull(mainUrl + doc.selectFirst("img.fs__poster-img")?.attr("src"))
        val tags = doc.select("div.xfs__item--value[itemprop=genre] > a").map { it.text() }
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value
            ?.toIntOrNull()
        val description = doc.select("div.fs__descr--text > p").joinToString {
            it.text().trim()
        }
        val isMovie    = tags.filter { it.contains("films", true) }.joinToString()
        val isTvSeries = tags.filter { it.contains("series", true) }.joinToString()
        val type = if (isMovie.contains("films", true)) TvType.Movie
        else if (isTvSeries.contains("series", true)) TvType.TvSeries
        else TvType.Cartoon
        val trailerLink = doc.select("#player > div").map {
            it.select("iframe").attr("src")
        }.filter { it.contains("youtube") }.joinToString()
        val trailer = fixUrlNull(trailerLink)
        val rating = doc.select("b.imdb__value").text().replace(",", ".")
        val duration =
            doc.select("li.xfs__item_op:nth-child(3) > b").text().removeSuffix(" min.").trim()
                .toIntOrNull()
        val actors =
            doc.select("div.xfs__item_op > b[itemprop=actors]").text().split(", ").map {
                ActorData(
                    Actor(it)
                )
            }
        val recommendations = doc.select("li.short-mid").mapNotNull { it.toSearchResult(cookies) }
        val idRegex = Regex("(src:.')+(\\D.*\\d)")
        val id = idRegex.find(doc.select("div.tabs__content script").toString())?.groups?.get(2)?.value
        val playerScript = doc.select("script:containsData(AwsIndStreamDomain)").toString()

        val domainRegex = Regex("const AwsIndStreamDomain.*'(.*)';")
        val playerDomain = domainRegex.find(playerScript)?.groups?.get(1)?.value ?: return null
        val embedLink = "$playerDomain/play/$id"
        val streamPayload = getDlPayload(embedLink, url, playerDomain)
        var episodes: List<Episode> = listOf()
        var data = ""
        if (type == TvType.TvSeries) {
            val folderJson = streamPayload.raw
            if (folderJson.contains("folder", true)) {
                episodes = parseJson<ArrayList<Seasons>>(folderJson).flatMap { season ->
                    val sNum = season.id.toIntOrNull()
                    season.folder.map { ep ->
                        val eNum = ep.episode.toIntOrNull()
                        newEpisode(
                            EpisodePayload(
                                playerDomain = streamPayload.playerDomain,
                                tokenKey = streamPayload.tokenKey,
                                links = ep.folder.map { file ->
                                    Extract(title = file.title, id = file.id, file = file.file)
                                }
                            ).toJson()
                        ) {
                            this.name = ep.title
                            this.episode = eNum
                            this.season = sNum
                            this.posterUrl = poster
                        }
                    }
                }
            } else {
                episodes = streamPayload.items.map {
                    newEpisode(streamPayload.toJson()) {
                        this.name = "1 episode"
                        this.season = 1
                        this.episode = 1
                        this.posterUrl = poster
                    }
                }
            }
        } else {
            data = streamPayload.toJson()
        }

        return when (type) {
            TvType.Movie -> {
                newMovieLoadResponse(title, url, TvType.Movie, data) {
                    this.posterUrl = poster?.trim()
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from100(rating)
                    this.duration = duration
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer)
                }
            }
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster?.trim()
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from100(rating)
                    this.duration = duration
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer)
                }
            }
            else -> {
                newMovieLoadResponse(title, url, TvType.Movie, data) {
                    this.posterUrl = poster?.trim()
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from100(rating)
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer)
                }
            }
        }
    }

    data class Getfile (
        @JsonProperty("file"       ) var file       : String,
        @JsonProperty("hls"        ) var hls        : Int?   ,
        @JsonProperty("id"         ) var id         : String?,
        @JsonProperty("cuid"       ) var cuid       : String?,
        @JsonProperty("key"        ) var key        : String?,
        @JsonProperty("movie"      ) var movie      : String?,
        @JsonProperty("host"       ) var host       : String?,
        @JsonProperty("masterId"   ) var masterId   : String?,
        @JsonProperty("masterHash" ) var masterHash : String?,
        @JsonProperty("userIp"     ) var userIp     : String?,
        @JsonProperty("poster"     ) var poster     : String?,
        @JsonProperty("href"       ) var href       : String,
        @JsonProperty("p2p"        ) var p2p        : Boolean?,
        @JsonProperty("rek"        ) var rek        : Any?,
        @JsonProperty("autoplay"   ) var autoplay   : Int?   ,
        @JsonProperty("domain"     ) var domain     : Any?,
        @JsonProperty("kp"         ) var kp         : String?,
    )

    data class Extract (
        @JsonProperty("title" ) var title : String?,
        @JsonProperty("id"    ) var id    : String?,
        @JsonProperty("file"  ) var file  : String?
    )

    data class Seasons (
        @JsonProperty("title"  ) var title  : String?,
        @JsonProperty("id"     ) var id     : String,
        @JsonProperty("folder" ) var folder : List<Episodes> = listOf()
    )

    data class Episodes (
        @JsonProperty("episode" ) var episode : String,
        @JsonProperty("title"   ) var title   : String?,
        @JsonProperty("id"      ) var id      : String?,
        @JsonProperty("folder"  ) var folder  : List<Files> = listOf()
    )

    data class Files (
        @JsonProperty("file"    ) var file   : String,
        @JsonProperty("end_tag" ) var endTag : String?,
        @JsonProperty("title"   ) var title  : String?,
        @JsonProperty("id"      ) var id     : String?
    )

    data class StreamPayload(
        @JsonProperty("playerDomain") val playerDomain: String,
        @JsonProperty("tokenKey") val tokenKey: String,
        @JsonProperty("links") val items: List<Extract>,
        @JsonProperty("raw") val raw: String = items.toJson(),
    )

    data class EpisodePayload(
        @JsonProperty("playerDomain") val playerDomain: String,
        @JsonProperty("tokenKey") val tokenKey: String,
        @JsonProperty("links") val links: List<Extract>,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val payload = runCatching { parseJson<StreamPayload>(data) }.getOrNull()
            ?: runCatching { parseJson<EpisodePayload>(data) }.getOrNull()?.let {
                StreamPayload(it.playerDomain, it.tokenKey, it.links)
            }
            ?: return false
        val m3u8Links = payload.items
        m3u8Links.forEach {
            safeApiCall {
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                    "Referer" to payload.playerDomain,
                    "Origin" to payload.playerDomain
                )

                M3u8Helper.generateM3u8(
                    "AllMovieLand-$lang",
                    getM3u8(payload.playerDomain, payload.tokenKey, it.file),
                    payload.playerDomain,
                    headers = headers
                ).forEach(callback)
            }
        }
        return true
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}
