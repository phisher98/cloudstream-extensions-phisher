package com.ToonTales

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class ToonTales : MainAPI() {
    override var mainUrl              = "https://www.toontales.net"
    override var name                 = "ToonTales"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Cartoon)

    override val mainPage = mainPageOf(
        "series/popeye-the-sailor/" to " Popeye the Sailor",
        "series/the-pink-panther-show" to "The Pink Panther Show",
        "series/tom-and-jerry" to "Tom and Jerry",
        "series/disney" to "Disney",
        "series/looney-tunes" to "Looney Tunes",
        "series/merrie-melodies" to "Merrie Melodies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/?page=$page").documentLarge
        val home     = document.select("section > div.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("a > img").attr("alt")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a > img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/?s=$query&paged=$i").documentLarge

            val results = document.select("#movies-a > ul > li").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    @Suppress("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, referer = url).documentLarge
        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim().toString()
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        val file=document.selectFirst("script:containsData(file)")?.data().toString().substringAfter("file: \"").substringBefore("\"")

        callback.invoke(
            newExtractorLink(
                name = name,
                source = name,
                url = file,
                type = INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getQualityFromName("")
            }
        )
        return true
    }
}
