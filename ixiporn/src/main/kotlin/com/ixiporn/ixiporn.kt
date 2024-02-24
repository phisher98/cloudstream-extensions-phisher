package com.coxju

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class ixiporn : MainAPI() {
    override var mainUrl              = "https://ixiporn.org"
    override var name                 = "ixiporn"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
            "${mainUrl}/?filter=latest/page/" to "Latest Release",
            "${mainUrl}/tag/ullu-web-series/page/" to "Ullu Web Series",
            "${mainUrl}/search/Hunters/page/" to "Hunter Web Series",
            "${mainUrl}/search/fugi/page/" to "Fugi Web Series",
            "${mainUrl}/search/besharams/page/" to "Besharams Web Series",
            "${mainUrl}/search/primeplay/page/" to "Prime Play",
            "${mainUrl}/search/neonx/page/" to "Neonx",
            "${mainUrl}/search/Bang+Bros/page/" to "BangBros",
            "${mainUrl}/search/brazzers/page/" to "Brazzers",
            "${mainUrl}/search/voovi/page/" to "Voovi Web Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home     = document.select("div.col-12.col-md-4.col-lg-3.col-xl-3 > div.video-block").mapNotNull { it.toSearchResult() }

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
        val title     = fixTitle(this.select("a.infos").attr("title")).trim().toString()
        val href      = fixUrl(this.select("a.infos").attr("href"))
        val posterUrl = fixUrlNull(this.select("a.thumb > img").attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..10) {
            val document = app.get("${mainUrl}/page/$i?s=$query").document

            val results = document.select("div.col-12.col-md-4.col-lg-3.col-xl-3 > div.video-block").mapNotNull { it.toSearchResult() }

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
        val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
    

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        document.select("div.video-player").map { res ->
            callback.invoke(
                    ExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = fixUrl(res.selectFirst("meta[itemprop=contentURL]")?.attr("content")?.trim().toString()),
                        referer = data,
                        quality = Qualities.Unknown.value
                    )
            )
        }

        return true
    }
}