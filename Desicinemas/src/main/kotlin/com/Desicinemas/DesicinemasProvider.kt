package com.Desicinemas

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

open class DesicinemasProvider : MainAPI() {
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "hi"
    override var mainUrl = "https://desicinemas.to"
    override var name = "Desicinemas"
    override val hasMainPage = true
    val proxy="https://desicinemas.phisherdesicinema.workers.dev/"
    override val mainPage = mainPageOf(
        "$proxy?url=https://desicinemas.to/" to "Home",
        "$proxy?url=https://desicinemas.to/category/punjabi/" to "Punjabi",
        "$proxy?url=https://desicinemas.to/category/bollywood/" to "Bollywood",
        "$proxy?url=https://desicinemas.to/category/hindi-dubbed/" to "Hindi Dubbed"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page == 1 || request.name == "Home") request.data else "${request.data}page/$page/"
        val doc = app.get(url, referer = mainUrl, timeout = 10000).document

        val homePages = listOfNotNull(
            doc.selectFirst(".MovieListTop")?.toHomePageList("Most popular").takeIf { request.name == "Home" },
            doc.selectFirst("#home-movies-post")?.toHomePageList("Latest Movies").takeIf { request.name == "Home" },
            doc.selectFirst(".MovieList")?.toHomePageList(request.name).takeIf { request.name != "Home" }
        )

        return newHomePageResponse(homePages, request.name != "Home" && homePages.isNotEmpty())
    }

    private fun Element.toHomePageList(name: String) =
        HomePageList(name, select("li, .TPostMv").mapNotNull { it.toHomePageResult() })

    private fun Element.toHomePageResult(): SearchResponse? {
        val title = selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("data-src"))

        return newAnimeSearchResponse(title, href) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String) =
        app.get("$proxy?url=$mainUrl/?s=$query", referer = mainUrl).document
            .select(".MovieList li").mapNotNull { it.toHomePageResult() }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get("$proxy?url=$url", referer = mainUrl, timeout = 10000).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(doc.select(".Image img").attr("src"))

        return newTvSeriesLoadResponse(title, url, TvType.Movie, listOf(newEpisode(url) { name = title })) {
            this.posterUrl = posterUrl
            plot = doc.selectFirst(".Description p")?.text()
            tags = doc.select(".Genre a").map { it.text() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get("${proxy}?url=${data}", referer = mainUrl).document.select(".MovieList .OptionBx").amap {
            val name = it.select("p.AAIco-dns").text()
            val link = it.select("a").attr("href")
            val headers = mapOf(
                "referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "en-US,en;q=0.5",
                "Connection" to "keep-alive",
                "Cache-Control" to "no-cache"
            )
            val src = app.get(link, headers = headers).document
            val iframe=src.select("iframe").attr("src")
            loadCustomExtractor(name,iframe,mainUrl,subtitleCallback, callback)
        }
        return true
    }
}
