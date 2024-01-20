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
    override val hasMainPage = false

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document

        //Log.d("Got","got here")
        val titleRaw = this.selectFirst("div.card > a > div.info > span")?.text()?.trim()
        val title = if (titleRaw.isNullOrBlank()) "Unknown LiveStream" else titleRaw.toString()
        //Log.d("title", title)
        val posterUrl = fixUrl(this.select("img").attr("src"))
        //Log.d("posterUrl", posterUrl)
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        //Log.d("mybadhref", href)
        val loadData = LiveStreamLinks(
                title,
                posterUrl,
                href
            ).toJson()

            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, title, loadData, isHorizontalImages = true)), hasNext = true)
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }