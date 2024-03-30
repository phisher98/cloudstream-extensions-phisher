package com.likdev256

import android.annotation.SuppressLint
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Full4Movies : MainAPI() {
    override var mainUrl = "https://www.full4movies.works"
    override var name = "Full4movies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "category/bollywood-movies-download" to "Bollywood Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        //Log.d("Testg","$multipliedPage")
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home =
            document.select("div.posts-wrapper > article > div > div")
                .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a > img").attr("title")
        val href = this.select("a").attr("href")
        val posterUrl = this.selectFirst("a > img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = "$posterUrl" }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val previouspage = i - 1
            val multipliedPage = previouspage * 16
            val document = app.get("${mainUrl}/search_movies/page/$query/$multipliedPage").document

            val results = document.select("div.content.home_style > ul > li")
                .mapNotNull { it.toSearchResult() }

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
            fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content").toString())
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        //Log.d("Tesy12","$poster")
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    @SuppressLint("SuspiciousIndentation")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val extractedUrls = mutableListOf<String>()
        //Log.d("Test1244","$document")
        document.select("div.wp-block-image > p > a.myButton").forEach {
            val urls = it.attr("href")
            //Log.d("Test124", urls)
            extractedUrls.add(urls)
        }
        //Log.d("Test124", "$extractedUrls")
        val Alllinks = emptySet<String>().toMutableSet()
        extractedUrls.forEach {
            val links = Full4MoviesExtractor().getStreamUrl(it)
            links.forEach { url->
                Log.d("Test124 url", url)
                if (url.contains("gofile"))
                {
                    loadExtractor(url,referer = url,subtitleCallback,callback)
                }
            }
            }
        return true
    }
}
