package com.WatchMMAFull

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class WatchMMAFull : MainAPI() {
    override var mainUrl = "https://watchmmafull.com"
    override var name = "WatchMMAFull"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Others, TvType.Live)

    override val mainPage = mainPageOf(
        "new" to "Latest",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/$page").documentLarge
        val home = document.select("ul.list-movies li").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}/search/$query").documentLarge
            val results = document.select("ul.list-movies li").mapNotNull { it.toSearchResult() }
            return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title = document.select("meta[property=og:title]").attr("content")
        val rawposter = document.select("meta[property=og:image]").attr("content")
        val poster="$mainUrl$rawposter"
        val description = document.selectFirst("#app > main > div.body.player.has-sidebar > div.info-movie > article > p:nth-child(3)")?.ownText()?.trim()
        val tag=document.select("#extras a").map { it.text() }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tag
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).documentLarge.select("div.user-action  li").amap {
            val onclick=it.attr("onclick").substringAfter("(").substringBefore(")")
            val server= onclick.split(",")[0]
            val id= onclick.split(",")[1]
            val response= app.post("$mainUrl/ajax/change_link", data = mapOf("vl3x_server" to "1","id" to id,"server" to server)).parsedSafe<Root>()?.player
            val doc=Jsoup.parse(response!!)
            val iframe=doc.select("iframe").attr("src")
            loadExtractor(iframe,mainUrl,subtitleCallback, callback)
        }
        return true
    }
}


data class Root(
    val player: String,
)

