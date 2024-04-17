package com.horis.cloudstreamplugins

import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Element

class BollyzoneProvider : DesicinemasProvider() {
    override val supportedTypes = setOf(
        TvType.TvSeries
    )
    override var lang = "hi"

    override var mainUrl = "https://www.bollyzone.tv"
    override var name = "Bollyzone"

    override val mainPage = mainPageOf(
        "$mainUrl/series/" to "Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page == 1) {
            request.data
        } else {
            "${request.data}page/$page/"
        }

        val doc = app.get(url, referer = "$mainUrl/").document

        val pages = doc.selectFirst(".MovieList")
            ?.toHomePageList(request.name)

        val hasNext = pages?.list?.isNotEmpty() == true

        return HomePageResponse(arrayListOf(pages).filterNotNull(), hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url, referer = "$mainUrl/").document

        val items = doc.select(".MovieList li").mapNotNull {
            it.toHomePageResult()
        }
        return items
    }

    private fun Element.toHomePageList(name: String): HomePageList {
        val items = select("li")
            .mapNotNull {
                it.toHomePageResult()
            }
        return HomePageList(name, items)
    }

    private fun Element.toHomePageResult(): SearchResponse? {
        val title = selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val img = selectFirst("img")
        val posterUrl = fixUrlNull(img?.attr("src"))

        return newAnimeSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }

}
