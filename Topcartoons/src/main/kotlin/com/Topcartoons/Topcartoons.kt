package com.Topcartoons

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Topcartoons : MainAPI() {
    override var mainUrl              = "https://www.topcartoons.tv"
    override var name                 = "Topcartoons"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Cartoon)

    override val mainPage = mainPageOf(
        "serie" to " Shows",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}").documentLarge
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
        val title     = this.select("a > img").attr("alt")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}/?s=$query").documentLarge
            val results = document.select("article").mapNotNull { it.toSearchResult() }
            return results
    }

    @Suppress("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val document = request.documentLarge
        val title =document.selectFirst("div.header-content h1")?.text()?.trim().toString()
        val poster = document.select("a.blog-img img").attr("data-src")
        val description = document.selectFirst("div.entry-content")?.text()

         val episodes = mutableListOf<Episode>()
            document.select("article article").map {
                    val href = it.select("a").attr("href")
                    val name = fixTitle(it.select("h3 a").text().trim())
                    val ps = it.selectFirst("a img")?.attr("data-src").toString().trim()
                episodes.add(newEpisode(href){
                    this.name=name
                    this.posterUrl=ps
                })
                }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot=description
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        val file=document.selectFirst("meta[property=og:video:url]")?.attr("content").toString()
        callback.invoke(
            newExtractorLink(
                name = name,
                source = name,
                url = file,
                type = INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getQualityFromName("")
            }
        )

        return true
    }
}
