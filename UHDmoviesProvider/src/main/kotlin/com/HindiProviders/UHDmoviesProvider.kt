package com.HindiProviders

//import android.util.Log
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.net.URI

class UHDmoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://uhdmovies.foo"
    override var name = "UHDmovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Home",
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tv-series/" to "TV Series",
        "$mainUrl/tv-shows/" to "TV Shows",
        "$mainUrl/movies/dual-audio-movies/" to "Dual Audio Movies",
        "$mainUrl/movies/collection-movies/" to "Hollywood",
        "$mainUrl/tv-shows/netflix/" to "Netflix",
        "$mainUrl/web-series/" to "Web Series",
        "$mainUrl/amazon-prime/" to "Amazon Prime",
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
        val document = if (page == 1) {
            cfKiller(request.data).document
        } else {
            cfKiller(request.data + "/page/$page/").document
        }

        //Log.d("Document", document.toString())
        val home = document.select("article.gridlove-post").mapNotNull {
                it.toSearchResult()
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val titleRaw = this.select("h1.sanket").text().trim().removePrefix("Download ")
        //Log.d("titleRaw",titleRaw)
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groups?.get(1)?.value ?: titleRaw
        val href = fixUrl(this.select("div.entry-image > a").attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.select("div.entry-image > a > img").attr("src"))
        val quality = getQualityFromString(title)
        //Log.d("Quality", quality.toString())
        return if (titleRaw.contains("season", true) || titleRaw.contains("episode", true)) {
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
        val document = cfKiller("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

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
        //Log.d("Doc", doc.toString())
        val titleRaw = doc.select("div.gridlove-content div.entry-header h1.entry-title").text().trim().removePrefix("Download ")
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groups?.get(1)?.value ?: titleRaw
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("div.entry-content > p > img")?.attr("src"))
        //Log.d("poster", poster.toString())
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value?.toIntOrNull()
        val tags = doc.select("div.entry-category > a.gridlove-cat").map { it.text() }
        val tvTags = doc.selectFirst("h1.entry-title")?.text() ?:""
        val type = if (tvTags.contains("Season")) TvType.TvSeries else TvType.Movie
        val iframeRegex = Regex("""\[.*]""")
        val iframe = doc.select("""div.entry-content > p""").mapNotNull{ it }.filter{
            iframeRegex.find(it.toString()) != null
        }
        //Log.d("iframe", iframe.toString())
        val data = iframe.map {
            UHDLinks(
                it.text(),
                doc.select("div.entry-content > p > span > span > a").attr("href")
            )
        }
        val episodes = mutableListOf<Episode>()
        doc.select("p a.maxbutton").forEach { me ->
            //Log.d("Phsher Next ",me.nextElementSibling().toString())
            val episode=me.select("span").text().substringAfter("Episode").toIntOrNull()
            //Log.d("Phisher Next ",tedata)
            episodes.add(
                Episode(
                    data = me.attr("href"),
                    name = me.select("span").text(),
                    posterUrl = poster,
                    episode = episode
                )
            )
        }

        return if (type == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, data) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.tags = tags
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
        Log.d("Phisher Test 2", data)
        if (data.startsWith("https://"))
        {
            data.let { me ->
                val link = me
                val driveLink = bypassHrefli(link) ?: ""
                val base = getBaseUrl(driveLink)
                val driveReq = app.get(driveLink)
                val driveRes = driveReq.document
                val bitLink = driveRes.select("a.btn.btn-warning").attr("href")
                val insLink =
                    driveRes.select("a.btn.btn-danger:contains(Instant Download)")
                        .attr("href")
                val downloadLink = when {
                    insLink.isNotEmpty() -> extractInstantUHD(insLink)
                    driveRes.select("button.btn.btn-success").text()
                        .contains("Direct Download", true) -> extractDirectUHD(
                        driveLink,
                        driveReq
                    )

                    bitLink.isNullOrEmpty() -> {
                        val backupIframe =
                            driveRes.select("a.btn.btn-outline-warning").attr("href")
                        extractBackupUHD(backupIframe ?: "")
                    }

                    else -> {
                        extractMirrorUHD(bitLink, base)
                    }
                }
                Log.d("Phisher bitLink",bitLink)
                Log.d("Phisher downloadLink",downloadLink.toString())
                val resume = extractResumeUHD(bitLink)
                val pixeldrain = extractPixeldrainUHD(bitLink)
                val serverslist = listOf(downloadLink, resume, pixeldrain)
                serverslist.forEach {
                    if (it != null) {
                        if (it.contains("https://video-leech.xyz")) {
                            loadExtractor(it,subtitleCallback,callback)
                        } else
                            callback.invoke(
                                ExtractorLink(
                                    "UHDMovies", "UHDMovies", it
                                        ?: "", "", getQualityFromName("")
                                )
                            )
                    }
                }
            }
        }
        else {
            val sources = parseJson<ArrayList<UHDLinks>>(data)
            Log.d("Phisher sources",sources.toString())
            sources.apmap { me ->
                val link = me.sourceLink
                val driveLink = bypassHrefli(link) ?: ""
                val base = getBaseUrl(driveLink)
                val driveReq = app.get(driveLink)
                val driveRes = driveReq.document
                val bitLink = driveRes.select("a.btn.btn-warning").attr("href")
                val insLink =
                    driveRes.select("a.btn.btn-danger:contains(Instant Download)")
                        .attr("href")
                val downloadLink = when {
                    insLink.isNotEmpty() -> extractInstantUHD(insLink)
                    driveRes.select("button.btn.btn-success").text()
                        .contains("Direct Download", true) -> extractDirectUHD(
                        driveLink,
                        driveReq
                    )

                    bitLink.isNullOrEmpty() -> {
                        val backupIframe =
                            driveRes.select("a.btn.btn-outline-warning").attr("href")
                        extractBackupUHD(backupIframe ?: "")
                    }

                    else -> {
                        extractMirrorUHD(bitLink, base)
                    }
                }
                val rawtag=sources.apmap { it.sourceName }.toString()
                val tag = "(\\d{3,4}p\\s)(.*)".toRegex().find(rawtag)?.groupValues?.get(2)?.substringBefore(",")
                val quality = "(\\d{3,4}p)".toRegex().find(rawtag)?.groupValues?.get(1)
                val resume = extractResumeUHD(bitLink)
                val pixeldrain = extractPixeldrainUHD(bitLink)
                val serverslist = listOf(downloadLink, resume, pixeldrain,insLink)
                serverslist.forEach {
                    if (it != null) {
                        if (it.contains("https://video-leech.xyz")) {
                                loadExtractor(it,subtitleCallback,callback)
                        } else
                            callback.invoke(
                                ExtractorLink(
                                    "UHDMovies", "UHDMovies $tag", it
                                        ?: "", "", getQualityFromName(quality)
                                )
                            )
                    }
                }
            }
        }
        return true
    }
}
