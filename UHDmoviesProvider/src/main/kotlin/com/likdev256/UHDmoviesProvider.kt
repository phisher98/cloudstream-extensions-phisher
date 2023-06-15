package com.likdev256

//import android.util.Log
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
    override var mainUrl = "https://uhdmovies.cc"
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
        val tvTags = listOf("TV Series", "TV Shows", "WeB Series")
        val type = if (tags.joinToString().containsAnyOfIgnoreCase(tvTags)) TvType.TvSeries else TvType.Movie

        val iframeRegex = Regex("""\[.*\]""")
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

        val episodes = ArrayList<Episode>()
        /*doc.select("section.container > div.border-b").forEach { me ->
            val seasonNum = me.select("button > span").text()
            me.select("div.season-list > a").forEach {
                episodes.add(
                    Episode(
                        data = mainUrl + it.attr("href").toString(),
                        name = it.ownText().toString().removePrefix("Episode ").substring(2),//.replaceFirst(epName.first().toString(), ""),
                        posterUrl = poster,
                        season = titRegex.find(seasonNum)?.value?.toInt(),
                        episode = titRegex.find(it.select("span.flex").text().toString())?.value?.toInt()
                    )
                )
            }
        }*/

        return if (type == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, data) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.tags = tags
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.tags = tags
            }
        }
    }

    private fun String.containsAnyOfIgnoreCase(keywords: List<String>): Boolean {
        for (keyword in keywords) {
            if (this.contains(keyword, true)) return true
        }
        return false
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

    suspend fun bypassTechmny(url: String): String? {
        val postUrl = url.substringBefore("?id=").substringAfter("/?")
        var res = app.post(
            postUrl, data = mapOf(
                "_wp_http_c" to url.substringAfter("?id=")
            )
        )
        val (longC, catC, _) = getTechmnyCookies(res.text)
        var headers = mapOf("Cookie" to "$longC; $catC")
        var formLink = res.document.selectFirst("center a")?.attr("href")

        res = app.get(formLink ?: return null, headers = headers)
        val (longC2, _, postC) = getTechmnyCookies(res.text)
        headers = mapOf("Cookie" to "$catC; $longC2; $postC")
        formLink = res.document.selectFirst("center a")?.attr("href")

        res = app.get(formLink ?: return null, headers = headers)
        val goToken = res.text.substringAfter("?go=").substringBefore("\"")
        val tokenUrl = "$postUrl?go=$goToken"
        val newLongC = "$goToken=" + longC2.substringAfter("=")
        headers = mapOf("Cookie" to "$catC; rdst_post=; $newLongC")

        val driveUrl =
            app.get(tokenUrl, headers = headers).document.selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")?.substringAfter("url=")
        val path = app.get(driveUrl ?: return null).text.substringAfter("replace(\"")
            .substringBefore("\")")
        if (path == "/404") return null
        return fixUrl(path, getBaseUrl(driveUrl))
    }

    suspend fun bypassDriveleech(url: String): String? {
        val path = app.get(url).text.substringAfter("replace(\"")
            .substringBefore("\")")
        if (path == "/404") return null
        return fixUrl(path, getBaseUrl(url))
    }

    private fun getTechmnyCookies(page: String): Triple<String, String, String> {
        val cat = "rdst_cat"
        val post = "rdst_post"
        val longC = page.substringAfter(".setTime")
            .substringAfter("document.cookie = \"")
            .substringBefore("\"")
            .substringBefore(";")
        val catC = if (page.contains("$cat=")) {
            page.substringAfterLast("$cat=")
                .substringBefore(";").let {
                    "$cat=$it"
                }
        } else {
            ""
        }

        val postC = if (page.contains("$post=")) {
            page.substringAfterLast("$post=")
                .substringBefore(";").let {
                    "$post=$it"
                }
        } else {
            ""
        }

        return Triple(longC, catC, postC)
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

        val sources = parseJson<ArrayList<UHDLinks>>(data)
        
        sources.apmap { me ->
            val name = me.sourceName
            val link = me.sourceLink
            val driveLink =
                if (link.contains("driveleech")) bypassDriveleech(link) else bypassTechmny(link)
            val base = getBaseUrl(driveLink ?: return@apmap)
            val resDoc = app.get(driveLink).document
            val bitLink = resDoc.selectFirst("a.btn.btn-outline-success")?.attr("href")
            val downloadLink = if (bitLink.isNullOrEmpty()) {
                val backupIframe = resDoc.select("a.btn.btn-outline-warning").attr("href")
                extractBackupUHD(backupIframe ?: return@apmap)
            } else {
                extractMirrorUHD(bitLink, base)
            }

            val tags =
                Regex("(\\d{3,4}[Pp]\\.?.*?)\\[").find(name)?.groupValues?.getOrNull(1)
                    ?.replace(".", " ")?.trim()
                    ?: ""
            val qualities =
                Regex("(\\d{3,4})[Pp]").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Qualities.Unknown.value
            val size =
                Regex("(?i)\\[(\\S+\\s?(gb|mb))[]/]").find(name)?.groupValues?.getOrNull(1)
                    ?.let { "[$it]" } ?: name

            callback.invoke(
                ExtractorLink(
                    "UHDMovies",
                    "$tags $size",
                    downloadLink ?: return@apmap,
                    "",
                    qualities
                )
            )

        }
        return true
    }
}
