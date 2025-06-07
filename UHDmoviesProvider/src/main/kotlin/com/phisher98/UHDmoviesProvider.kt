package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.net.URI

class UHDmoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://uhdmovies.tips"
    override var name = "UHDmovies"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    companion object {
        private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        private var cachedDomains: DomainsParser? = null

        suspend fun getDomains(forceRefresh: Boolean = false): DomainsParser? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<DomainsParser>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }
    }

    override val mainPage = mainPageOf(
        "" to "Home",
        "movies/" to "Movies",
        "tv-series/" to "TV Series",
        "tv-shows/" to "TV Shows",
        "movies/dual-audio-movies/" to "Dual Audio Movies",
        "movies/collection-movies/" to "Hollywood",
        "tv-shows/netflix/" to "Netflix",
        "web-series/" to "Web Series",
        "amazon-prime/" to "Amazon Prime",
    )

    private suspend fun cfKiller(url: String): NiceResponse {
        var doc = app.get(url)
        if (doc.document.select("title").text() == "Just a moment...") {
            doc = app.get(url, interceptor = CloudflareKiller())
        }
        return doc
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val uhdmoviesAPI = getDomains()?.uhdmovies
        val document = if (page == 1) {
            cfKiller("$uhdmoviesAPI/${request.data}").document
        } else {
            cfKiller("$uhdmoviesAPI/${request.data}" + "/page/$page/").document
        }

        //Log.d("Document", document.toString())
        val home = document.select("article.gridlove-post").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val titleRaw = this.select("h1.sanket").text().trim().removePrefix("Download ")
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groups?.get(1)?.value ?: titleRaw
        val href = fixUrl(this.select("div.entry-image > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.entry-image > a > img").attr("src"))
        val quality = getQualityFromString(title)
        return if (titleRaw.contains("season|S0", true) || titleRaw.contains("episode", true) || titleRaw.contains("S0", true)) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val uhdmoviesAPI = getDomains()?.uhdmovies
        val document = cfKiller("$uhdmoviesAPI?s=$query ").document

        return document.select("article.gridlove-post").mapNotNull {
            it.toSearchResult()
        }
    }

    data class UHDLinks(
        @JsonProperty("sourceName") val sourceName: String,
        @JsonProperty("sourceLink") val sourceLink: String
    )

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val titleRaw = doc.select("div.gridlove-content div.entry-header h1.entry-title").text().trim().removePrefix("Download ")
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groups?.get(1)?.value ?: titleRaw
        val poster = fixUrlNull(doc.selectFirst("div.entry-content  p  img")?.attr("src"))
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value?.toIntOrNull()
        val tags = doc.select("div.entry-category > a.gridlove-cat").map { it.text() }
        val tvTags = doc.selectFirst("h1.entry-title")?.text() ?:""
        val type = if (tvTags.contains("Season") || tvTags.contains("S0")) TvType.TvSeries else TvType.Movie
        val trailer=doc.select("p iframe").attr("src")
        return if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            var pTags = doc.select("p:has(a:contains(Episode))")
            if (pTags.isEmpty())
            {
                pTags = doc.select("div:has(a:contains(Episode))")
            }
            val seasonList = mutableListOf<Pair<String, Int>>()
            var season = 1
            pTags.mapNotNull { pTag ->
                val prevPtag = pTag.previousElementSibling()
                val details = prevPtag ?. text() ?: ""
                val realSeason = Regex("""(?:Season |S0)(\d+)""").find(details) ?. groupValues
                    ?. get(1) ?: ""
                val qualityRegex = """(1080p|720p|480p|2160p|4K|[0-9]*0p)""".toRegex(RegexOption.IGNORE_CASE)
                val quality = qualityRegex.find(details) ?. groupValues ?. get(1) ?: ""
                if(realSeason.isNotEmpty() && quality.isNotEmpty()) {
                    val sizeRegex = Regex("""\d+(?:\.\d+)?\s*(?:MB|GB)\b""")
                    val size = sizeRegex.find(details) ?. value ?: ""
                    seasonList.add("S$realSeason $quality $size" to season)
                }
                else {
                    seasonList.add(details to season)
                }
                val aTags = pTag.select("a:contains(Episode)")
                aTags.mapNotNull { aTag ->
                    val aTagText = aTag.text()
                    val link = aTag.attr("href")
                    episodes.add(
                        newEpisode(link)
                        {
                            this.name=aTagText
                            this.season=season
                            this.episode=aTags.indexOf(aTag) + 1
                        }
                    )
                }
                season++
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster ?. trim()
                this.year = year
                this.tags = tags
                this.seasonNames = seasonList.map {(name, int) -> SeasonData(int, name)}
                addTrailer(trailer)
            }
        } else {
            val iframeRegex = Regex("""\[.*]""")
            val iframe = doc.select("""div.entry-content > p""").amap { it }.filter {
                iframeRegex.find(it.toString()) != null
            }
            val data = iframe.amap {
                UHDLinks(
                    it.text().substringBefore("Download"),
                    it.nextElementSibling()?.select("a.maxbutton-1")?.attr("href") ?: ""
                )
            }
            newMovieLoadResponse(title, url, TvType.Movie, data) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.tags = tags
                addTrailer(trailer)
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    fun fixUrl(url: String, domain: String): String {
        if (url.startsWith("http")) {
            return url
        }
        if (url.isEmpty()) {
            return ""
        }

        val startsWithNoHttp = url.startsWith("//")
        if (startsWithNoHttp) {
            return "https:$url"
        } else {
            if (url.startsWith('/')) {
                return domain + url
            }
            return "$domain/$url"
        }
    }

    fun Document.getMirrorLink(): String? {
        return this.select("div.mb-4 a").randomOrNull()
            ?.attr("href")
    }

    fun Document.getMirrorServer(server: Int): String {
        return this.select("div.text-center a:contains(Server $server)").attr("href")
    }

    suspend fun extractMirrorUHD(url: String, ref: String): String? {
        var baseDoc = app.get(fixUrl(url, ref)).document
        var downLink = baseDoc.getMirrorLink()
        run lit@{
            (1..2).forEach {
                if (downLink != null) return@lit
                val server = baseDoc.getMirrorServer(it.plus(1))
                baseDoc = app.get(fixUrl(server, ref)).document
                downLink = baseDoc.getMirrorLink()
            }
        }
        return if (downLink?.contains("workers.dev") == true) downLink else base64Decode(
            downLink?.substringAfter(
                "download?url="
            ) ?: return null
        )
    }


    suspend fun extractBackupUHD(url: String): String? {
        val resumeDoc = app.get(url)

        val script = resumeDoc.document.selectFirst("script:containsData(FormData.)")?.data()

        val ssid = resumeDoc.cookies["PHPSESSID"]
        val baseIframe = getBaseUrl(url)
        val fetchLink =
            script?.substringAfter("fetch('")?.substringBefore("',")?.let { fixUrl(it, baseIframe) }
        val token = script?.substringAfter("'token', '")?.substringBefore("');")

        val body = FormBody.Builder()
            .addEncoded("token", "$token")
            .build()
        val cookies = mapOf("PHPSESSID" to "$ssid")

        val result = app.post(
            fetchLink ?: return null,
            requestBody = body,
            headers = mapOf(
                "Accept" to "*/*",
                "Origin" to baseIframe,
                "Sec-Fetch-Site" to "same-origin"
            ),
            cookies = cookies,
            referer = url
        ).text
        return tryParseJson<UHDBackupUrl>(result)?.url
    }

    data class UHDBackupUrl(
        @JsonProperty("url") val url: String? = null,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        //Log.d("Phisher",data)
        //val href=bypassHrefli(data)
        if (data.startsWith("https://")) {
            data.let { me ->
                val driveLink = bypassHrefli(me) ?: ""
                loadExtractor(driveLink, subtitleCallback, callback)

            }
        } else {
            val sources = parseJson<ArrayList<UHDLinks>>(data)
            sources.amap { me ->
                val link = me.sourceLink
                val driveLink = bypassHrefli(link) ?: ""
                loadExtractor(driveLink, subtitleCallback, callback)
            }
        }
        return true
    }
}