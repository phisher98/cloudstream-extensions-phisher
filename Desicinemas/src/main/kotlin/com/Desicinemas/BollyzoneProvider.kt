package com.Desicinemas

import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Element

class BollyzoneProvider : DesicinemasProvider() {
    override val supportedTypes = setOf(
        TvType.TvSeries
    )
    override var lang = "hi"
    override var mainUrl = "https://www.bollyzone.to"
    override var name = "Bollyzone"

    override val mainPage = mainPageOf(
        "$proxy?url=$mainUrl/tv-channels/" to "Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url, referer = "$mainUrl/").document

        val homePageList = mutableListOf<HomePageList>()

        val headers = doc.select("h2.Title").filter { it->
            it.text().contains("Shows", ignoreCase = true)
        }
        for (header in headers) {
            val sectionName = header.selectFirst("a")?.text()?.trim() ?: continue
            val movieListDiv = header.nextElementSiblings()
                .firstOrNull { it.tagName() == "div" && it.hasClass("MovieListTop") } ?: continue
            val list = movieListDiv.toHomePageList(sectionName)
            homePageList.add(list)
        }
        val hasNext = homePageList.any { it.list.isNotEmpty() }
        return newHomePageResponse(homePageList, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$proxy?url=$mainUrl/?s=$query"
        val doc = app.get(url, referer = "$mainUrl/").document

        val items = doc.select(".MovieList li").mapNotNull {
            it.toHomePageResult()
        }
        return items
    }

    private fun Element.toHomePageList(name: String): HomePageList {
        val items = select("div.TPostMv")
            .mapNotNull {
                it.toHomePageResult()
            }
        return HomePageList(name, items)
    }

    private fun Element.toHomePageResult(): SearchResponse? {
        val title = selectFirst("h2.Title")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val img = selectFirst("img")
        val posterUrl = fixUrlNull(img?.getImageAttr())

        return newAnimeSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get("$proxy?url=$url", referer = mainUrl, timeout = 10000).document

        // Handle single movie under "series"
        if (url.contains("/series/")) {
            val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
            val posterUrl = fixUrlNull(doc.selectFirst(".Image img")?.getImageAttr())

            return newTvSeriesLoadResponse(title, url, TvType.Movie, listOf(newEpisode(url) { name = title })) {
                this.posterUrl = posterUrl
                plot = doc.selectFirst(".Description p")?.text()
                tags = doc.select(".Genre a").map { it.text() }
            }
        }

        // Handle TV series
        val title = doc.select("meta[property=og:title]").attr("content")
        val posterUrl = doc.selectFirst("div.Image img")?.getImageAttr()
        val description = doc.select("meta[property=og:description]").attr("content")
        val tags = doc.select(".Genre a").map { it.text() }.distinct()

        val lastPageNumber = doc.select("section > nav > div > a")
            .mapNotNull { it.text().toIntOrNull() }
            .maxOrNull() ?: 1

        val dateRegex = Regex("""\b\d{1,2}(st|nd|rd|th)?\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4}\b""")

        val episodes = (1..lastPageNumber).flatMap { page ->
            val pageUrl = "$proxy?url=$url/page/$page/"
            val pageDoc = app.get(pageUrl, referer = mainUrl, timeout = 10000).document

            pageDoc.select("ul.MovieList li").mapNotNull { element ->
                val epUrl = fixUrlNull(element.select("a").attr("href")) ?: return@mapNotNull null
                val titleText = element.selectFirst("a h2")?.text()?.trim()
                val match = titleText?.let { dateRegex.find(it) }
                val epName = match?.value ?: titleText ?: "Episode"
                val epPoster = element.select("img").attr("src")

                newEpisode(epUrl) {
                    name = epName
                    this.posterUrl = epPoster
                }
            }
        }.toMutableList()

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = posterUrl
            this.plot = description
            this.tags = tags
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("src") -> this.attr("src")
            else -> this.attr("src")
        }
    }
}
