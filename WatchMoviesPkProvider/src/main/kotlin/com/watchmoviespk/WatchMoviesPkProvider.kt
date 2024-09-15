package com.watchmoviespk

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class WatchMoviesPkProvider : MainAPI() {

    override var mainUrl = "https://www.watch-movies.com.pk"
    override var name = "WatchMoviesPk"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "" to "Latest Movies",
        "/category/indian-movies/action-movies/" to "Action Movies",
        "/category/indian-movies/funny-movies/" to "Funny Movies",
        "/category/romantic-movies/" to "Romantic Movies",
        "/category/horror-movies/" to "Horror Movies",
        "/category/old-bollywood-movies/" to "Old Bollywood Movies",
        "/category/indian-movies/south-indian-dubbed-hindi/" to "South Indian Hindi Dubbed Movies",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {


       val document = if(request.data == "")
        {
            app.get("$mainUrl/page/$page/").document
        }
        else
       {
           app.get("$mainUrl${request.data}page/$page/").document
       }
        val home = document.select(".postbox").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home,hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select(".boxtitle a:nth-of-type(1)").attr("title").replace("Free","").replace("Download","")
        val href = this.select(".boxtitle a:nth-of-type(1)").attr("href")
        val posterUrl = this.select(".boxtitle a:nth-of-type(1) img").attr("data-src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select(".postbox").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.select("meta[property=\"og:title\"]").attr("content").replace("Free","").replace("Download","")
        val poster = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")
        val tags = document.select(".rightinfo p:nth-of-type(1) a").map { it.text() }
        return  newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.tags = tags
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val servers = mutableListOf<String>()
        val doc = app.get(data).document
        val links = doc.select(".singcont p a")
        links.forEach { item ->
            val url = item.attr("href")
            var finalUrl = url
            if(url.contains("d0000d.com"))
            {
                finalUrl = url.replace("/d/","/e/")
            }
            if(!servers.contains(finalUrl))
            {
                servers.add(finalUrl)
            }
        }
        servers.forEach{item ->
            loadExtractor(item,subtitleCallback,callback)
        }
        return true
    }
}
