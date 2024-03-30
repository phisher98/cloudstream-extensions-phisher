package com.coxju

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Mydesi : MainAPI() {
    override var mainUrl              = "https://bee121.com"
    override var name                 = "Mydesi"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/" to "Latest",
        "category/paid" to "Paid",
        "category/tango" to "Tango",
        "category/village" to "Village",
        "category/pornography/onlyfans-video-collection" to "Onlyfans",
        "category/bhabi" to "Bhabi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home     = document.select("div.video-block.video-with-trailer").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("a.infos").attr("title").trim()
        val href      = fixUrl(this.select("a.infos").attr("href"))
        val posterUrl = fixUrlNull(this.select("a.thumb > img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
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

        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()

        val dataSetup = document.select("video[data-setup]").attr("data-setup")
        val regex = Regex(""""poster":\s*"(.*?\.jpg)\?""")
        val matchResult = regex.find(dataSetup)
        val poster = matchResult?.groups?.get(1)?.value
        // Use regex to extract the poster URL until .jpg extension
        // Check if the poster URL is found and print it
        //val poster = document.select("#censor-player_html5_api").attr("data-setup").substringAfter("\"poster\":").substringBefore("\"").trim()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        println(poster)

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        //println(document)
        //val sources = mutableListOf<String>()
        document.select("video > source").forEach {
            val url = it.attr("src")
            println(url)
            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = url,
                    referer = data,
                    quality = Qualities.Unknown.value
                )
            )
        }
        return true
    }
}
