package com.IndianTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URL

class IndianTVProvider : MainAPI() {
    override var mainUrl = "https://livesportsclub.me"
    override var name = "IndianTV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "hi"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/hls/tata/" to "Tata",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val document = app.get(request.data + page).document
            val home = document.select("div.box1").mapNotNull {
                it.toSearchResult()
            }
            println("Home data: $home")
            return newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            println("Error fetching data: $e")
            return newErrorHomeResponse("Error fetching data")
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = resolveUrl(this.select("a").attr("href"))
        val title = this.selectFirst("h2.text-center.text-sm.font-bold")?.text()?.trim() ?: "Unknown Title"
        val posterUrl = resolveUrl(this.selectFirst("img")?.attr("src"))
        println("Search Result - Title: $title, Href: $href, PosterUrl: $posterUrl")
        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    private fun resolveUrl(url: String?): String {
        return URL(URL(mainUrl), url).toString()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList() // Modify this if you implement search functionality
    }
}
