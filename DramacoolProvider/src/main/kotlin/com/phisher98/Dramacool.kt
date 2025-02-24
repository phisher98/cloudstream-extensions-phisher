package com.phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Dramacool2 : Dramacool() {
    override var mainUrl = "https://dramacool.com.tr"
    override var name = "Dramacool2"
}

open class Dramacool : MainAPI() {
    override val supportedTypes = setOf(
        TvType.AsianDrama
    )
    override var lang = "en"

    override var mainUrl = "https://dramacool.com.tr"
    override var name = "Dramacool"

    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "recently-added-drama" to "Recent Dramas",
        "recently-added-movie" to "Recent Movies",
        "country/korean" to "Korean Dramas",
        "country/chinese" to "Chinese Dramas",
        "country/thailand" to "Thailand Dramas",
        "most-popular-drama" to "Popular Dramas"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url=if (page==1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url, timeout = 30L).document
        val items = document.select("ul.switch-block.list-episode-item li").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3")?.text() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("a img")?.attr("data-original"))
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchtitle=query.createSlug()
        val url = "$mainUrl/?type=movies&s=$searchtitle"
        val document = app.get(url, referer = "$mainUrl/").document
        val items = document.select("ul.switch-block.list-episode-item li").mapNotNull {
            it.toSearchResult()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, referer = "$mainUrl/", timeout = 10L).document
        val detailsUrl = document.selectFirst("div.category > a")?.attr("href") ?:
            url.substringBefore("-episode").replace("video-watch", "drama-detail")
        val detailsDocument = app.get(detailsUrl, referer = "$mainUrl/", timeout = 10L).document
        val title = detailsDocument.selectFirst("h1")?.text()?.trim() ?: ""
        val tag = detailsDocument.select("div.info > p:nth-of-type(10) a").map { it.text().trim() }
        val description= detailsDocument.selectFirst("div.info > p:nth-of-type(3)")?.text()?.trim() ?: ""
        val posterurl = detailsDocument.selectFirst("div.details img")?.attr("src")
        val episodes = detailsDocument.select("ul.list-episode-item-2 li").mapNotNull { el ->
            val name=el.selectFirst("a h3")?.text()?.substringAfter("Episode")?.trim()
            val href=el.selectFirst("a")?.attr("href") ?: ""
            newEpisode(href)
            {
                this.name="Episode $name"
            }
        }.reversed()

        return newTvSeriesLoadResponse(title, detailsUrl, TvType.TvSeries, episodes) {
            posterUrl = posterurl
            this.plot=description
            this.tags=tag
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val url = fixUrl(document.selectFirst("div.anime_muti_link ul li")?.attr("data-video") ?:
            document.selectFirst("iframe")?.attr("src") ?: return false)
        loadExtractor(url, mainUrl, subtitleCallback, callback)
        return true
    }

    fun String?.createSlug(): String? {
        return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
            ?.trim()
            ?.replace("\\s+".toRegex(), "+")
            ?.lowercase()
    }
}
