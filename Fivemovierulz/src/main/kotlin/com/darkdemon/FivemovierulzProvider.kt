package com.darkdemon

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class FivemovierulzProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://5movierulz.gripe"
    override var name = "5movierulz"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/featured/page/" to "Latest",
        "$mainUrl/category/bollywood-featured/page/" to "Bollywood",
        "$mainUrl/language/hindi-dubbed/page/" to "Hindi Dubbed",
        "$mainUrl/category/hollywood-featured/page/" to "Hollywood"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).documentLarge
        val home = document.select("#main .cont_display").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title =
            this.selectFirst("a")?.attr("title")?.trim()?.substringBefore("(") ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").documentLarge

        return document.select("#main .cont_display").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).documentLarge

        val title = document.selectFirst("h2.entry-title")?.text()?.trim()?.substringBefore("(")
            ?: return null
        val poster = fixUrlNull(document.selectFirst(".entry-content img")?.attr("src"))
        val tags =
            document.select("div.entry-content > p:nth-child(5)").text().substringAfter("Genres:")
                .substringBefore("Country:").split(",").map { it }
        val yearRegex = Regex("""\d{4}""")
        val year = yearRegex.find(
            document.select("h2.entry-title").text()
        )?.groupValues?.getOrNull(0)?.toIntOrNull()
        val description = document.select("div.entry-content > p:nth-child(6)").text().trim()
        val actors =
            document.select("div.entry-content > p:nth-child(5)").text()
                .substringAfter("Starring by:")
                .substringBefore("Genres:").split(",").map { it }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge

        val links = document.select("p a")
            .filter { it.text().contains("watch online", ignoreCase = true) }
            .mapNotNull { it.attr("href") }
            .filter { it.isNotBlank() }

        for (link in links) {
            Log.d("Phisher",link)
            loadExtractor(
                link,
                "$mainUrl/",
                subtitleCallback,
                callback
            )
        }

        return links.isNotEmpty()
    }
}
