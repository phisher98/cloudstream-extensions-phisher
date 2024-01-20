package com.IndianTV

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

class IndianTVProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://livesportsclub.me/hls/tata/" 
    override var name = "IndianTV"
    override val supportedTypes = setOf(TvType.Live)

    override var lang = "hi"

    // enable this when your provider has a main page
    override val hasMainPage = true

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }
}