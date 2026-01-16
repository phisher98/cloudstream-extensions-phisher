package com.phisher98


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import org.jsoup.nodes.Element
import java.net.URI
import java.text.Normalizer

open class Movierulzhd : MainAPI() {

    override var mainUrl: String = runBlocking {
        MovierulzhdPlugin.getDomains()?.movierulzhd ?: "https://1movierulzhd.pro"
    }
    var directUrl = ""
    override var name = "Movierulzhd"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "movies" to "Movies",
        "tvshows" to "TV Shows",
        "genre/netflix" to "Netflix",
        "genre/amazon-prime" to "Amazon Prime",
        "genre/Zee5" to "Zee5",
        "genre/sony-liv" to "Sony Liv",
        "genre/hotstar" to "Hotstar",
        "genre/jio-cinema" to "Jio Cinema",
        "seasons" to "Season",
        "episodes" to "Episode",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if(page == 1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url).document
        val home =
            document.select("div.items.normal article, div#archive-content article, div.items.full article").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episodes/") -> {
                var title = uri.substringAfter("$mainUrl/episodes/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }

            uri.contains("/seasons/") -> {
                var title = uri.substringAfter("$mainUrl/seasons/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }

            else -> {
                uri
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 > a")?.text() ?: return null
        val href = getProperLink(fixUrl(this.selectFirst("h3 > a")!!.attr("href")))
        var posterUrl = this.select("div.poster img").last()?.getImageAttr()
       
        if (posterUrl != null) {
            if (posterUrl.contains(".gif")) {
                posterUrl = fixUrlNull(this.select("div.poster img").attr("data-wpfc-original-src"))
            }
        }
        val quality = getSearchQuality(this.select("span.quality").text())
        val score = this.select("div.rating").text()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
            this.score = Score.from10(score)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").documentLarge
        return document.select("div.result-item").map {
            val title =
                it.selectFirst("div.title > a")!!.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
            val href = getProperLink(it.selectFirst("div.title > a")!!.attr("href"))
            val posterUrl = it.selectFirst("img")!!.attr("src")
            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val document = request.documentLarge
        directUrl = getBaseUrl(request.url)
        val title =
            document.selectFirst("div.data > h1")?.text()?.trim().toString()
        val background = fixUrlNull(document.selectFirst(".playbox img.cover")?.getImageAttr())
        val posterUrl = fixUrlNull(document.selectFirst("div.poster img")?.getImageAttr())
        /*if (backgroud.isNullOrEmpty()) {
            if (background.contains("movierulzhd")) {
                background = fixUrlNull(document.select("div.poster img").attr("src"))
            }
        }*/
        val tags = document.select("div.sgeneros > a").map { it.text() }
        val year = Regex(",\\s?(\\d+)").find(
            document.select("span.date").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.select("ul#section > li:nth-child(1)").text()
                .contains("Episodes") || document.select("ul#playeroptionsul li span.title")
                .text().contains(
                    Regex("Episode\\s+\\d+|EP\\d+|PE\\d+|S\\d{2}|E\\d{2}")
                )
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.wp-content > p").text().trim()
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val rating = document.selectFirst("span.dt_rating_vgs")?.text()
        val actors = document.select("div.persons > div[itemprop=actor]").map {
            Actor(
                it.select("meta[itemprop=name]").attr("content"),
                it.select("img:last-child").attr("src")
            )
        }

        val recommendations = document.select("div.owl-item").map {
            val recName = it.selectFirst("a")!!.attr("href").removeSuffix("/").split("/").last()
            val recHref = it.selectFirst("a")!!.attr("href")
            val recPosterUrl = it.selectFirst("img")?.getImageAttr()
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = if (document.select("ul.episodios > li").isNotEmpty()) {
                document.select("ul.episodios > li").map {
                    val href = it.select("a").attr("href")
                    val name = fixTitle(it.select("div.episodiotitle > a").text().trim())
                    val image = it.selectFirst("div.imagen > img")?.getImageAttr()
                    val episode =
                        it.select("div.numerando").text().replace(" ", "").split("-").last()
                            .toIntOrNull()
                    val season =
                        it.select("div.numerando").text().replace(" ", "").split("-").first()
                            .toIntOrNull()
                    newEpisode(href)
                    {
                        this.name=name
                        this.episode=episode
                        this.season=season
                        this.posterUrl=image
                    }
                }
            } else {
            val check = document.select("ul#playeroptionsul > li").toString().contains("Super")
				if (check) {
				    document.select("ul#playeroptionsul > li").drop(1).map {
				        val name = it.selectFirst("span.title")?.text()
				        val type = it.attr("data-type")
				        val post = it.attr("data-post")
				        val nume = it.attr("data-nume")
                        newEpisode(LinkData(name, type, post, nume).toJson())
                        {
                            this.name=name
                        }
				    }
				} else {
				    document.select("ul#playeroptionsul > li").map {
				        val name = it.selectFirst("span.title")?.text()
				        val type = it.attr("data-type")
				        val post = it.attr("data-post")
				        val nume = it.attr("data-nume")
                        newEpisode(LinkData(name, type, post, nume).toJson())
                        {
                            this.name=name
                        }
				    }
				}
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl= background
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.year = year
                this.backgroundPosterUrl= background
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            if (data.startsWith("{")) {
                val loadData = AppUtils.tryParseJson<LinkData>(data)
                if (loadData != null) {
                    try {
                        val source = app.post(
                            url = "$directUrl/wp-admin/admin-ajax.php",
                            data = mapOf(
                                "action" to "doo_player_ajax",
                                "post" to "${loadData.post}",
                                "nume" to "${loadData.nume}",
                                "type" to "${loadData.type}"
                            ),
                            referer = data,
                            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                        ).parsed<ResponseHash>().embed_url

                        if (!source.contains("youtube")) {
                            loadCustomExtractor(name,source, "$directUrl/", subtitleCallback, callback)
                        }
                    } catch (e: Exception) {
                        println("Error loading direct source: ${e.message}")
                    }
                }
            } else {
                try {
                    val document = app.get(data).documentLarge
                    val items = document.select("ul#playeroptionsul > li").map {
                        Triple(
                            it.attr("data-post"),
                            it.attr("data-nume"),
                            it.attr("data-type")
                        )
                    }

                    items.amap { (post, nume, type) ->
                        try {
                            val source = app.post(
                                url = "$directUrl/wp-admin/admin-ajax.php",
                                data = mapOf(
                                    "action" to "doo_player_ajax",
                                    "post" to post,
                                    "nume" to nume,
                                    "type" to type
                                ),
                                referer = data,
                                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                            ).parsed<ResponseHash>().embed_url
                            if (!source.contains("youtube")) {
                                if (source.contains("/#"))
                                {
                                    VidStack().getUrl(source,"",subtitleCallback,callback)
                                }
                                else
                                loadExtractor(source, subtitleCallback, callback)
                            }
                        } catch (e: Exception) {
                            println("Error loading item: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    println("Error processing HTML document: ${e.message}")
                }
            }
            return true
        } catch (e: Exception) {
            println("General error in loadLinks: ${e.message}")
            return false
        }
    }


    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private suspend fun loadCustomExtractor(
        name: String? = null,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        name ?: link.source,
                        name ?: link.name,
                        link.url,
                    ) {
                        this.quality = when {
                            link.name == "VidSrc" -> Qualities.P1080.value
                            link.type == ExtractorLinkType.M3U8 -> link.quality
                            else -> quality ?: link.quality
                        }
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }


    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val originalUrl = request.url.toString()
            val modifiedRequest = if (originalUrl.startsWith("https://exxample.com/")) {
                val encodedPart = originalUrl.removePrefix("https://exxample.com/")
                val decodedUrl = try {
                    base64Decode(encodedPart)
                } catch (e: IllegalArgumentException) {
                    println("Failed to decode Base64: ${e.message}")
                    null
                }
                if (decodedUrl != null) {
                    request.newBuilder()
                        .url(decodedUrl)
                        .build()
                } else {
                    request
                }
            } else {
                request
            }

            val finalRequest = if (modifiedRequest.url.host.contains("sukumsanghas.com")) {
                modifiedRequest.newBuilder()
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .header("DNT", "1")
                    .header("Origin", "https://molop.art")
                    .header("Pragma", "no-cache")
                    .header("Referer", "https://molop.art/")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-GPC", "1")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0"
                    )
                    .build()
            } else {
                modifiedRequest
            }
            chain.proceed(finalRequest)
        }
    }




    data class LinkData(
        val tag: String? = null,
        val type: String? = null,
        val post: String? = null,
        val nume: String? = null,
    )

    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     *
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    fun getSearchQuality(check: String?): SearchQuality? {
        val s = check ?: return null
        val u = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val patterns = listOf(
            Regex("\\b(4k|ds4k|uhd|2160p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.FourK,

            // CAM / THEATRE SOURCES FIRST
            Regex("\\b(hdts|hdcam|hdtc)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HdCam,
            Regex("\\b(camrip|cam[- ]?rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip,
            Regex("\\b(cam)\\b", RegexOption.IGNORE_CASE) to SearchQuality.Cam,

            // WEB / RIP
            Regex("\\b(web[- ]?dl|webrip|webdl)\\b", RegexOption.IGNORE_CASE) to SearchQuality.WebRip,

            // BLURAY
            Regex("\\b(bluray|bdrip|blu[- ]?ray)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,

            // RESOLUTIONS
            Regex("\\b(1440p|qhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,
            Regex("\\b(1080p|fullhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
            Regex("\\b(720p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.SD,

            // GENERIC HD LAST
            Regex("\\b(hdrip|hdtv|HD)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,

            Regex("\\b(dvd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.DVD,
            Regex("\\b(hq)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HQ,
            Regex("\\b(rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip
        )


        for ((regex, quality) in patterns) if (regex.containsMatchIn(u)) return quality
        return null
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )
}
