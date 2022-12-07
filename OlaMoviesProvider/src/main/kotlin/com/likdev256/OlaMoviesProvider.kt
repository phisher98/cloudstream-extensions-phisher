package com.likdev256

//import com.fasterxml.jackson.annotation.JsonProperty
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.nicehttp.RequestBodyTypes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URI

class OlaMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://olamovies.cyou"
    override var name = "OlaMovies"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    data class Links(
        @JsonProperty("link") val link: String,
        @JsonProperty("quality") val quality: String
    )

    data class MovieLinks(
        @JsonProperty("url") val url: String,
        @JsonProperty("list") val list: List<Links> = arrayListOf()
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/movies/bollywood/" to "Bollywood Movies",
        "$mainUrl/movies/hollywood/" to "Hollywood Movies",
        "$mainUrl/movies/south-indian/" to "South Indian Movies",
        "$mainUrl/movies/anime-movies/" to "Anime Movies",
        "$mainUrl/tv-series/" to "TV Series",
        "$mainUrl/tv-series/anime-tv-series-tv-series/" to "Anime TV Series",
        "$mainUrl/tv-series/english-tv-series/" to "English TV Series",
        "$mainUrl/tv-series/hindi-tv-series/" to "Hindi TV Series",
        "$mainUrl/tv-shows/" to "TV Shows",
        "$mainUrl/tv-shows/cartoon-tvs/" to "Cartoon TV Shows",
        "$mainUrl/tv-shows/documentary/" to "Documentary TV Shows",
        "$mainUrl/tv-shows/hindi-tv-shows/" to "Hindi TV Shows"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        //Log.d("Document", request.data)
        val home = document.select("div.layout-simple").mapNotNull {
            it.toSearchResult()
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val titleS = this.selectFirst("article > div.entry-overlay h2.entry-title > a")?.text().toString()
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleS)?.groups?.get(1)?.value.toString()
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("article > div.entry-overlay h2.entry-title > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.select("article > div.entry-image > a > img").attr("data-lazy-src").trim())
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN).post-152185 > div:nth-child(2) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > a:nth-child(1)
        val quality = getQualityFromString(if (titleS.contains("2160p")) "4k" else "hd")
        val type = ArrayList<String>()
        this.select("article div.entry-category a").forEach { type.add(it.ownText()) }
        //Log.d("mygodtype", type.toString())
        return if (type.contains("Movies")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("div.layout-simple").mapNotNull {
            val titleS = it.selectFirst("article > div.entry-overlay h2.entry-title > a")?.text().toString()
            val titleRegex = Regex("(^.*\\)\\d*)")
            val title = titleRegex.find(titleS)?.groups?.get(1)?.value.toString()
            //Log.d("title", title)
            val href = fixUrl(it.selectFirst("article > div.entry-overlay h2.entry-title > a")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.select("article > div.entry-image > a > img").attr("src").trim())
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN).post-152185 > div:nth-child(2) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > a:nth-child(1)
            val quality = getQualityFromString(if (titleS.contains("2160p")) "4k" else "hd")
            val type = ArrayList<String>()
            it.select("article div.entry-category a").forEach { type.add(it.ownText()) }
            //Log.d("mygodtype", type.toString())
            if (type.contains("Movies")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }

    private fun String.containsAnyOfIgnoreCase(keywords: List<String>): Boolean {
        for (keyword in keywords) {
            if (this.contains(keyword, true)) return true
        }
        return false
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val titleS = doc.select("h1.entry-title").text().toString().trim()
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleS)?.groups?.get(1)?.value.toString()
        //Log.d("title", title)
        val href = doc.select("div.is-layout-flex > div.wp-block-button").map {
            Links(
                link = "$mainUrl${it.select("a[target=_blank]").attr("href")}",
                quality = it.select("a[target=_blank]").text()
            )
        }
        //Log.d("href", href)
        val poster = fixUrlNull(doc.select("span.gridlove-cover > a").attr("href"))
        //Log.d("poster", poster.toString())
        val bloat = listOf("720p","1080p","2160p","Bluray","x264","x265","60FPS","120fps","144FPS","WEB-DL")
        val tags = doc.select("div.entry-tags > a").map { if (it.text().containsAnyOfIgnoreCase(bloat)) "" else it.text() }.filter { !it.isNullOrBlank() }
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value?.toIntOrNull()
        val trailer = fixUrlNull(doc.select("div.perfmatters-lazy-youtube").attr("data-src"))
        val plot = doc.select("div.entry-content > p").text()
        //Log.d("year", year.toString())
        val type = ArrayList<String>()
        doc.select("article div.entry-category a").forEach { type.add(it.ownText()) }
        val recommendations = doc.select("div.col-lg-6").mapNotNull {
            it.toSearchResult()
        }

        val titRegex = Regex("\\d+")
        val seasonRegex = Regex("(.eason)+(.\\d)")
        val episodes = ArrayList<Episode>()

        if (doc.select("div.w3-margin-bottom").toString() != "") {
            doc.select("div.entry-content > div.w3-margin-bottom").forEach { me ->
                doc.select("div.is-layout-flex").forEach { he ->
                    if (he.toString().contains("episode", true)) {
                        // Log.d("myme", he.toString())
                        val seasonNum = me.select("button.w3-button").text()
                        // Log.d("myseason", seasonRegex.find(seasonNum)?.groups?.get(2)?.value.toString())
                        he.select("div.wp-block-button").forEach {
                            episodes.add(
                                Episode(
                                    data = mainUrl + it.select("a[target=_blank]").attr("href").toString(),
                                    name = it.select("a[target=_blank]").text(),//.replaceFirst(epName.first().toString(), ""),
                                    season = seasonRegex.find(seasonNum)?.groups?.get(2)?.value?.trim()?.toIntOrNull(),
                                    episode = titRegex.find(it.select("a[target=_blank]").text().toString())?.value?.toInt()
                                )
                            )
                        }
                    }
                }
            }
        } else {
            doc.select("div.is-layout-flex").forEach { me ->
                if (me.toString().contains("episode", true)) {
                    // Log.d("myme", me.toString())
                    me.select("div.wp-block-button").forEach {
                        episodes.add(
                            Episode(
                                data = mainUrl + it.select("a[target=_blank]").attr("href").toString(),
                                name = it.select("a[target=_blank]").text(),//.replaceFirst(epName.first().toString(), ""),
                                season = 1,
                                episode = titRegex.find(it.select("a[target=_blank]").text().toString())?.value?.toInt()
                            )
                        )
                    }
                }
            }
        }

        return if (type.contains("Movies")) {
            newMovieLoadResponse(title, url, TvType.Movie, MovieLinks(url, href)) {
                this.posterUrl = poster
                this.year = year
                this.tags = tags
                this.plot = plot
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.tags = tags
                this.plot = plot
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    data class Response(
        @JsonProperty("credits") var credits: String,
        @JsonProperty("from_db") var fromDb: Boolean,
        @JsonProperty("success") var success: Boolean,
        @JsonProperty("type") var type: String,
        @JsonProperty("url") var url: String
    )

    private suspend fun bypassAdLinks(link: String): String? {
        val apiUrl = "https://api.emilyx.in/api/bypass"
        val apiSupportedLinks = listOf("dulink", "ez4short")

        val type = if (link.contains("rocklinks")) "rocklinks"
        else if (link.contains("dulink")) "dulink"
        else if (link.contains("ez4short")) "ez4short"
        else ""
        //Log.d("mytype", type)
        val values = mapOf("type" to type, "url" to link)
        //Log.d("mytype", values.toString())
        val json = mapper.writeValueAsString(values)
            .toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        // Log.d("mytype", json.toString())
        //Log.d("mytype", JsonAsString(json).toString())
        val domain = "https://ser3.crazyblog.in"
        println(link.substringAfterLast("/"))
        val html = app.get("$domain/${link.substringAfterLast("/")}")
        val cookies = html.headers.filter { it.first.contains("set-cookie") }.toString()
        println(cookies)
        val (AppSession, csrfToken, app_visitor) = cookies.split("),").map {
            println("it: $it")
            it.substringAfter("=").substringBefore("; path")
        }
        val document = html.document
        val data = document.select("#go-link input")
            .mapNotNull { it.attr("name").toString() to it.attr("value").toString() }.toMap()

        return if (link.containsAnyOfIgnoreCase(apiSupportedLinks)) {
            app.post(
                url = apiUrl,
                requestBody = json
            ).parsedSafe<Response>()?.url
        } else if (link.contains("ser2.crazyblog")) {
            app.post(
                url = "$domain/links/go",
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "Accept-Language" to "en-US,en;q=0.5",
                ),
                cookies = mapOf(
                    "app_visitor" to app_visitor,
                    "AppSession" to AppSession,
                    "csrfToken" to csrfToken,

                    ),
                data =  data,
                referer = "$domain/${link.substringAfterLast("/")}"
            ).toString()
        } else {
            ""
        }
    }

    private suspend fun bypassOlaRedirect(link: String, referer: String): String {
        //Log.d("myfirstlink", link)
        val key = link.substringAfter("?key=").substringBefore("&id=").replace("%2B","+").replace("%3D","=").replace("%2F","/")
        //Log.d("mykey", key)
        val id = link.substringAfter("&id=")
        //Log.d("myid", id)
        val param = mapOf(key to id)
        val doc = app.get(link, referer = referer, params = param).document
        //Log.d("mydoctest", doc.toString())
        //Log.d("mydoc", doc.select("#download > a").attr("href"))
        return doc.select("#download > a").attr("href").trim()
    }

    val gdbot = "https://gdbot.xyz"

    suspend fun extractGdbot(url: String): String? {
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        )
        val res = app.get(
            "$gdbot/", headers = headers
        )
        val token = res.document.selectFirst("input[name=_token]")?.attr("value")
        val cookiesSet = res.headers.filter { it.first == "set-cookie" }
        val xsrf =
            cookiesSet.find { it.second.contains("XSRF-TOKEN") }?.second?.substringAfter("XSRF-TOKEN=")
                ?.substringBefore(";")
        val session =
            cookiesSet.find { it.second.contains("gdtot_proxy_session") }?.second?.substringAfter("gdtot_proxy_session=")
                ?.substringBefore(";")

        val cookies = mapOf(
            "gdtot_proxy_session" to "$session",
            "XSRF-TOKEN" to "$xsrf"
        )
        val requestFile = app.post(
            "$gdbot/file", data = mapOf(
                "link" to url,
                "_token" to "$token"
            ), headers = headers, referer = "$gdbot/", cookies = cookies
        ).document

        return requestFile.selectFirst("div.mt-8 a.float-right")?.attr("href")
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    data class DriveBotLink(
        @JsonProperty("url") val url: String? = null,
    )

    suspend fun extractDrivebot(url: String): String? {
        val iframeGdbot =
            app.get(url).document.selectFirst("li.flex.flex-col.py-6 a:contains(Drivebot)")
                ?.attr("href")
        val driveDoc = app.get(iframeGdbot ?: return null)

        val ssid = driveDoc.cookies["PHPSESSID"]
        val script = driveDoc.document.selectFirst("script:containsData(var formData)")?.data()

        val baseUrl = getBaseUrl(iframeGdbot)
        val token = script?.substringAfter("'token', '")?.substringBefore("');")
        val link =
            script?.substringAfter("fetch('")?.substringBefore("',").let { "$baseUrl$it" }

        val body = FormBody.Builder()
            .addEncoded("token", "$token")
            .build()
        val cookies = mapOf("PHPSESSID" to "$ssid")

        val result = app.post(
            link,
            requestBody = body,
            headers = mapOf(
                "Accept" to "*/*",
                "Origin" to baseUrl,
                "Sec-Fetch-Site" to "same-origin"
            ),
            cookies = cookies,
            referer = iframeGdbot
        ).text
        return AppUtils.tryParseJson<DriveBotLink>(result)?.url
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Log.d("mydata", data)
        val me = parseJson<MovieLinks>(data)
        //Log.d("mydata", me.list.toString())
        me.list.forEach {
            if (it.quality.isNotEmpty()) {
                //Log.d("mydata", it.toString())
                var count = 0
                Log.d("mybypassolalinks1", bypassOlaRedirect(it.link, me.url))
                Log.d("mydata", "got-here")
                val shortLink = bypassOlaRedirect(it.link, me.url)
                if (bypassOlaRedirect(it.link, me.url).isNotEmpty()) {
                    while (count <= 10) {
                        Log.d("mylist", "got")
                        Log.d("myfinallinks1", extractDrivebot(extractGdbot(bypassAdLinks(shortLink).toString()).toString()).toString())
                        count = count + 1
                        safeApiCall {
                            callback.invoke(
                                ExtractorLink(
                                    it.quality,
                                    it.quality,
                                    extractDrivebot(extractGdbot(bypassAdLinks(shortLink).toString()).toString()).toString(),
                                    "$mainUrl/",
                                    getQualityFromName(
                                        Regex("(?i)((DVDRip)|(HD)|(HQ)|(HDRip))").find(it.quality)?.value.toString()
                                            .lowercase()
                                    ),
                                    false
                                )
                            )
                        }
                    }
                }

            }/* else {
                Log.d("mybypassolalinks1", bypassOlaRedirect(it.link, me.url))
                Log.d("mybypasslinks2", bypassAdLinks(bypassOlaRedirect(it.link, me.url)).toString())
                safeApiCall {
                    callback.invoke(
                        ExtractorLink(
                            "it.quality",
                            "it.quality",
                            data,
                            "$mainUrl/",
                            Qualities.Unknown.value,
                            false
                        )
                    )
                }
            }*/
        }
        return true
    }
}
