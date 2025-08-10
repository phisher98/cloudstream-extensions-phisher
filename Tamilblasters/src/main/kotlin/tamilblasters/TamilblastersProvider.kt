package com.tamilblasters

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TamilblastersProvider : MainAPI() {
    override var mainUrl = "https://www.tamilblasters.qpon/"
    private val streamhg = "https://tryzendm.com"
    override var name = "Tamilblasters"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "ta"
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/page/$page"
        val document = app.get(url).document
        val home = document.select("div.article-content-col").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse("Home", home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val name = selectFirst("h2>a")?.text() ?: return null
        val posterUrl = selectFirst("img")?.attr("src")
        val href = selectFirst("a")?.attr("href") ?: return null
        return newMovieSearchResponse(name, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchDoc = app.get("$mainUrl/?s=$query").document
        return searchDoc.select("div.article-content-col").mapNotNull {
            it.toSearchResult()
        }
    }

    data class VideoEntry(val title: String, val url: String)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val ogdesc = document.selectFirst("meta[property='og:description']")?.attr("content") ?: return null
        val title = ogdesc.substringAfter("Name:").substringBefore("(").trim()
        val year = "\\((\\d{4})\\)".toRegex().find(ogdesc)?.groupValues?.get(1)?.toIntOrNull()
        val type = if (ogdesc.startsWith("Movie")) TvType.Movie else TvType.TvSeries
        val posterUrl = document.selectFirst("meta[property='og:image']")?.attr("content")
        val plotParagraph = document.select("p:has(strong)")
        .firstOrNull { it.selectFirst("strong")?.text()?.contains("plot", ignoreCase = true) == true }
        val desc = plotParagraph?.apply { select("strong").remove() }?.text() ?: ""  
        return if (type == TvType.TvSeries) {
            val episodes = extractVideos(document).map { ep ->
                newEpisode(ep.toJson()) {
                    this.name = ep.title
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = desc
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = desc
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("{")) {
            val loadData = AppUtils.tryParseJson<VideoEntry>(data)
            if (loadData != null) {
                var streamurl = loadData.url
                if (streamurl.contains("hg")) {
                    val secondPart = streamurl.substringAfter("/e")
                    streamurl = "$streamhg/e/$secondPart"
                }
                loadExtractor(streamurl, "$mainUrl/", subtitleCallback, callback)
                return true
            }
        } else {
            val doc = app.get(data).document
            doc.select("iframe").mapNotNull { iframe ->
                var streamurl = iframe.attr("src")
                if (streamurl.contains("hg")) {
                    val secondPart = streamurl.substringAfter("/e")
                    streamurl = "$streamhg/e/$secondPart"
                }
                loadExtractor(streamurl, "$mainUrl/", subtitleCallback, callback)
            }
            return true
        }
        return false
    }

    fun extractVideos(document: Document): List<VideoEntry> {
        return document.select("iframe").mapNotNull { iframe ->
            val label = iframe.previousElementSiblings()
                .firstOrNull { it.tagName() == "p" }
                ?.text()
            label?.let { VideoEntry(it, iframe.attr("src")) }
        }
    }
}