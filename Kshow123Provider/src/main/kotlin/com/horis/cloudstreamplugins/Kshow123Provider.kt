package com.horis.cloudstreamplugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Kshow123Provider : MainAPI() {
    override val supportedTypes = setOf(
        TvType.AsianDrama
    )
    override var lang = "en"

    override var mainUrl = "https://kshow123online.com"
    override var name = "Kshow123"

    override val hasMainPage = true

    override val mainPage = mainPageOf(
        mainUrl to "Recent Posts",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page == 1) {
            request.data
        } else {
            "${request.data}/page/$page/"
        }

        val document = app.get(url, referer = "$mainUrl/").document
        val items = document.select(".posts-items > li").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, LoadUrl(href, posterUrl).toJson()) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, referer = "$mainUrl/").document

        val items = document.select("#posts-container > li").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val name = it.selectFirst("h2")?.text() ?: return@mapNotNull null
            val posterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))
            val href = a.attr("href")
            newMovieSearchResponse(name, LoadUrl(href, posterUrl).toJson()) {
                this.posterUrl = posterUrl
            }
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = tryParseJson<LoadUrl>(url) ?: return null
        val document = app.get(d.url, referer = "$mainUrl/").document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null

        val episodes = arrayListOf(newEpisode(d.url) {
            name = title
        })

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = d.posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, verify = false).document
        val src = doc.select("iframe").map { it.attr("src") }
            .firstOrNull { it.contains("streaming.php") }
            ?.substringBefore("&") ?: return false
        loadExtractor(src, subtitleCallback, callback)
        return true
    }

    data class LoadUrl(
        val url: String,
        val posterUrl: String?
    )

}
