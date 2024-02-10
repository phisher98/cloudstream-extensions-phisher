package com.IndianTV

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element


class IndianTVProvider : MainAPI() {
    override var mainUrl = "https://madplay.live/hls/tata/"
    override var name = "IndianTV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "hi"
    override val hasMainPage = false

    data class LiveStreamLinks (
        @JsonProperty("title")  val title: String,
        @JsonProperty("poster") val poster: String,
        @JsonProperty("link")   val link: String,
        @JsonProperty("subtitle")   val subtitle: String,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest
	): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.box1").mapNotNull {
            it.toSearchResult()
        }
        return HomePageResponse(arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        //Log.d("Got","got here")
        //val title= this.selectFirst("h2.text-center.text-sm.font-bold").text().trim()
        val titleElement = this.selectFirst("h2.text-center.text-sm.font-bold")
        val title = titleElement?.text()?.trim().toString()
        val subtitle = this.selectFirst("p.text-xs text-center").text().trim()
        val posterUrl = this.selectFirst("img").attr("src").toString()
        //Log.d("posterUrl", posterUrl)
        val href = this.selectFirst("a")!!.attr("href")
        //Log.d("", href)

        val loadData = LiveStreamLinks(
                title,
                posterUrl,
                href,
                subtitle,
            ).toJson()
        return newMovieSearchResponse(title, loadData, TvType.Live) {
                this.posterUrl = posterUrl
            }
        }

    
        override suspend fun search(query: String): List<SearchResponse> {
            val doc = app.get("$mainUrl/").document
            //Log.d("document", document.toString())
    
            return doc.select("div.col-6").mapNotNull {
                it.toSearchResult()
            }.filter { it.name.contains(query, true) }
        }

        override suspend fun load(url: String): LoadResponse {
            val data = parseJson<LiveStreamLinks>(url)
            val title = data.title
            val poster = data.poster
            val link = data.link
            val subtitle=data.subtitle

            return newMovieLoadResponse(title, link, TvType.Live, link) {
                this.posterUrl = poster
            }
        }
}
