package com.coxju

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class mydesi : MainAPI() {
    override var mainUrl              = "https://hubmasa.com"
    override var name                 = "Desi49"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
            "${mainUrl}/page/" to "New Videos",
            "${mainUrl}/category/web-series/page/" to "Web Series",
            "${mainUrl}/category/indian-amateur-porn/page/" to "Indian",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document      
        val home     = document.select("article").mapNotNull { it.toSearchResult() }

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
        for (i in 1..5) {
        val title     = fixTitle(this.select("a").attr("title")).trim().toString()
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.post-thumbnail>div.inner-border>img").attr("data-src"))
        }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i?s=$query").document

            val results = document.select("article").mapNotNull { it.toSearchResult() }

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
        val poster      = fixUrlNull(document.selectFirst("#censor-player_html5_api")?.attr("poster"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
    

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        document.select("div.responsive-player").map { res ->
            callback.invoke(
                    ExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = fixUrl(res.selectFirst("video > source")?.attr("src")?.trim().toString()),
                        referer = data,
                        quality = Qualities.Unknown.value
                    )
            )
        }

        return true
    }
}