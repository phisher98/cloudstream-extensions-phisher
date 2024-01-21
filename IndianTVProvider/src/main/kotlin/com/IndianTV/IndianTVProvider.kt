package com.IndianTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SearchResponse
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.JWPlayer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.nicehttp.NiceResponse

class IndianTVProvider : MainAPI() { // all providers must be an instance of MainAPI
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
        val home = document.select("div.box1").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }
    private fun Element.toSearchResult(): SearchResponse {
        val href = this.selectFirst("a")!!.attr("href")
        val titleRaw = this.selectFirst("h2.text-center text-sm font-bold")?.text()?.trim()
        val title = if (titleRaw.isNullOrBlank()) "Unknown LiveStream" else titleRaw.toString()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
    return newMovieSearchResponse(title, href, TvType.Live) {
        this.posterUrl = posterUrl
    }
}

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }
}