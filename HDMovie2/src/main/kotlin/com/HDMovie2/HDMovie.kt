package com.HDMovie2

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Hdmovie : MainAPI() {
    override var mainUrl = "https://hdmovie2.at"
    override var name = "Hdmovie2 (AT)"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "" to "Trending",
        "bollywood" to "Bollywood",
        "hollywood" to "Hollywood",
        "series" to "TVSeries",
        "cartoon" to "Cartoon",
        "bollywood" to "Bollywood",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        Log.d("Mandik", "$document")
        val home =
            document.select(
                "div.items.normal > article,div.items.featured > article"
            )
                .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.data > h3 > a").text()
        val href = this.select("div.data > h3 > a").attr("href")
        val imageurl = this.selectFirst("div.poster > img")?.attr("src")
        val posterUrl = "$mainUrl$imageurl"
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = "$posterUrl" }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query&id=5036").document

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

        val title =
            document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster =
            document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return true
    }
}
