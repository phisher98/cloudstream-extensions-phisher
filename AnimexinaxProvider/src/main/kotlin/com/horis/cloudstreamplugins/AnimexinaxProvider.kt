package com.horis.cloudstreamplugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AnimexinaxProvider : MainAPI() {
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )
    override var lang = "en"

    override var mainUrl = "https://animexinax.com"
    override var name = "Animexinax"

    override val hasMainPage = true

    override val mainPage = mainPageOf(
        mainUrl to "Latest Release",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page == 1) {
            request.data
        } else {
            "${request.data}/page/$page/"
        }

        val document = app.get(url, referer = "$mainUrl/").document
        val items = document.select(".listupd article").mapNotNull {
            it.toHomePageResult()
        }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toHomePageResult(): SearchResponse? {
        val title = selectFirst(".eggtitle")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, referer = "$mainUrl/").document

        val items = document.select(".listupd article").mapNotNull {
            it.toHomePageResult()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse? {
        var doc = app.get(url, referer = "$mainUrl/").document
        val lis = doc.select(".ts-breadcrumb ol li")
        val isVideoPage = lis.size > 2
        if (isVideoPage) {
            val homePageUrl = lis[1].select("a").attr("href")
            doc = app.get(homePageUrl, referer = "$mainUrl/").document
        }
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null

        val episodes = doc.select(".eplister ul li a").mapNotNull {
            val href = fixUrl(it?.attr("href") ?: return@mapNotNull null)
            val name = it.select(".epl-num").text()
            newEpisode(href) {
                this.name = name
            }
        }.reversed()

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = doc.select(".thumb img").attr("src")
            plot = doc.select(".entry-content").text()
            tags = doc.select(".genxed a").map { it.text() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, referer = "$mainUrl/").document
        val links = doc.select("select.mirror option")
            .asSequence()
            .map { it.attr("value") }
            .filter { it.isNotBlank() }
            .map { base64Decode(it) }
            .map { it.substring("src=\"", "\"") }
            .map { fixUrl(it) }
            .toList()
        links.amap {
            loadExtractor(it, subtitleCallback, callback)
        }
        return true
    }

}
