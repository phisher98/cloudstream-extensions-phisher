package com.IndianTV

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SearchResponse
import com.fasterxml.jackson.annotation.JsonProperty

class IndianTVProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://livesportsclub.me/hls/tata/" 
    override var name = "IndianTV"
    override val supportedTypes = setOf(TvType.Live)

    data class LiveStreamLinks (
        @JsonProperty("title")  val title: String,
        @JsonProperty("poster") val poster: String,
        @JsonProperty("link")   val link: String,
    )
    override var lang = "hi"

    // enable this when your provider has a main page
    override val hasMainPage = true

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        
    //Log.d("Document", request.data)

        val home = if (request.data.contains("Search")) {
            document.select("div.col-span-4").mapNotNull {
                it.toEpicSearchResult()
            }
        } else {
            document.select("div.col-6").mapNotNull {
                it.toSearchResult()
            }
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)), hasNext = true)
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }

        override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LiveStreamLinks>(url)
        val title = data.title
        val poster = data.poster
        val link = data.link

        return newMovieLoadResponse(title, link, TvType.Live, link) {
                this.posterUrl = poster
            }
        }
}