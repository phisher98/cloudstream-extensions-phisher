package com.tamilblasters

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TamilblastersProvider : MainAPI() {
    override var mainUrl: String = runBlocking {
        TamilblastersPlugin.getDomains()?.tamilblasters ?: "https://www.1tamilblasters.business/"
    }
    private val streamhg = "https://cavanhabg.com"
    override var name = "Tamilblasters"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "ta"
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/page/$page"
        val document = app.get(url).documentLarge
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
        val searchDoc = app.get("$mainUrl/?s=$query").documentLarge
        return searchDoc.select("div.article-content-col").mapNotNull {
            it.toSearchResult()
        }
    }

    data class VideoEntry(val title: String, val url: String)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).documentLarge
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
            }.reversed()
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
            val loadData = tryParseJson<VideoEntry>(data)
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
            val doc = app.get(data).documentLarge
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

    private fun extractVideos(document: Document): List<VideoEntry> {
        return document.select("iframe").mapNotNull { iframe ->
            val label = iframe.previousElementSiblings()
                .firstOrNull { it.tagName() == "p" }
                ?.text()
            label?.let { VideoEntry(it, iframe.attr("src")) }
        }
    }
}