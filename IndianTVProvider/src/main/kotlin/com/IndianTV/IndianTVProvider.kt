package com.IndianTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class IndianTVProvider : MainAPI() {
    override var mainUrl = "https://livesportsclub.me"
    override var name = "IndianTV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "hi"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/hls/tata/" to "Tata",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        if (request.name == "Tata")
        {
        val home = document.select("div.box1").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }
    }
    else
    {
        //none
    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(this.select("a").attr("href"))
        val titleRaw = this.selectFirst("h2.text-center.text-sm.font-bold")?.text()?.trim() ?: "Unknown Title"
        val title = if (titleRaw.isNullOrBlank()) "Unknown LiveStream" else titleRaw.toString()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf()
    }
}
