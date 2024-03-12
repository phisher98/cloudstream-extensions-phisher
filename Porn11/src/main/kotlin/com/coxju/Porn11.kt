package com.coxju

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

class Porn11 : MainAPI() {
    override var mainUrl              = "https://pornx11.com"
    override var name                 = "Porn11"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
            "${mainUrl}/page/" to "Home",
            "${mainUrl}/category/kooku-originals-web-series/page/" to "Kooku",
            "${mainUrl}/category/ullu-originals-web-series/page/" to "Ullu",
            "${mainUrl}/category/flizmovies-originals-web-series/page/" to "Fliz movies",
            "${mainUrl}/category/uncutadda-web-series/page/" to "Uncutadda Webseries",
            "${mainUrl}/category/hotshots-web-series/page/" to "Hotshots",
            "${mainUrl}/category/niks-indian-porn/page/" to "Niks Indian",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home     = document.select("div.videos-list > article.post").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = fixTitle(this.select("a").attr("title"))
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a > div.post-thumbnail>div.post-thumbnail-container>img").attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i?s=$query").document

            val results = document.select("article.post").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster      = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content").toString())
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        Log.d("Phisher Test","$document")
        val url = mutableListOf<String>()
        document.select("div.responsive-player").map { res ->
            url.add(res.select("iframe").attr("src").toString())
            //val link =res.select("iframe").attr("src").toString()
            D0000dExtractor().getUrl(data, data)?.forEach { link -> callback.invoke(link) }
    }
        return true
}
    class D0000dExtractor : ExtractorApi() {
        override var name = "DoodStream"
        override var mainUrl = "https://d000d.com"
        override val requiresReferer = false

        override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            // html of DoodStream page to look for /pass_md5/...
            val response0 = app.get(url).text

            // get https://dood.ws/pass_md5/...
            val md5 = mainUrl + (Regex("/pass_md5/[^']*").find(response0)?.value ?: return null)
            val res = app.get(md5, referer = mainUrl + "/e/" + url.substringAfterLast("/"))

            // (zUEJeL3mUN is random)
            val trueUrl =
                if (res.toString().contains("cloudflarestorage")) res.toString()
                else res.text + "zUEJeL3mUN?token=" + md5.substringAfterLast("/")

            val quality =
                Regex("\\d{3,4}p")
                    .find(response0.substringAfter("<title>").substringBefore("</title>"))
                    ?.groupValues
                    ?.get(0)

            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    trueUrl,
                    mainUrl,
                    getQualityFromName(quality),
                    false
                )
            ) // links are valid for 8h
        }
    }
}
