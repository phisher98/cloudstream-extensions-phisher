package com.coxju

import org.jsoup.nodes.Element
import org.jsoup.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*


public val homePoster ="https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg"

class IndianTVPlugin : MainAPI() {
    override var mainUrl              = "https://madplay.live/hls/tata"
    override var name                 = "TATA Sky"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.Live)

   override val mainPage = mainPageOf(
        "${mainUrl}/" to "TATA",
)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div#listContainer > div.box1").mapNotNull { it.toSearchResult()}

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("h2.text-center").text()
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        //val category = this.select("p").text()

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
            val document = app.get("${mainUrl}/").document
            val results = document.select("div.box1").mapNotNull { it.toSearchResult() }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title       = document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
        val poster      = fixUrl("https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg")
        var showname = document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
        val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()
    

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot      = showname
        }
    }
    
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        /*val document = app.get(data).document
        val linksData = AppUtils.parseJson<Links>(data)
        //Log.d("King", "videoUrl:$mainUrl/play.php?id=${linksData.id}")
        val document = app.get(
            url = "$mainUrl/play.php?id=${linksData.id}",
            headers = mapOf(
                "user-agent" to userAgent
            )).document

        val servers = document.select("ul[id=\"xservers\"]").select("button").mapNotNull {
            it.attr("data-embed")
        }
        //Log.d("King", "servers:$servers")

        servers.map {
            app.get(it).document.select("script").mapNotNull { script ->

                val finalScript = if (JsUnpacker(script.data()).detect()) {
                    JsUnpacker(script.data()).unpack()!!
                } else {
                    script.data()
                }

                if (finalScript.contains("sources:")) {
                    val link = finalScript.substringAfter("sources:")
                        .substringBefore("\"}]").replace("[{file:\"","")

                    callback.invoke(
                        ExtractorLink(
                            source = it,
                            name = URL(it).host,
                            url = link,
                            referer = "",
                            quality = Qualities.Unknown.value,
                            isM3u8 = false,
                        )
                    )
                }
            }
        }
        return true
        */
    }
}
