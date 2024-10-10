package com.Phisher98

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class OnePace : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://rentry.org"
    override var name = "One Pace"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes =setOf(TvType.Anime)
    override val mainPage =mainPageOf("/onepace/" to "OnePace")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val link = "$mainUrl${request.data}"
        val document = app.get(link).document
        val home = document.select("article div h3").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val title = this.text()
        val posterUrl = this.nextElementSibling()?.select("p span img")?.attr("src") ?:""
        return newAnimeSearchResponse(title, url=title, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun load(url: String): LoadResponse {
        val title=url.substringAfterLast("/")
        val document = app.get("https://rentry.org/onepace").document
        val poster = "https://images3.alphacoders.com/134/1342304.jpeg"
        val episodes = mutableListOf<Episode>()
        val elements= document.selectFirst("article div h3:contains($title)")
        val description= elements?.nextElementSibling()?.nextElementSibling()?.selectFirst("p")?.text()
        var PTag = elements
        repeat(6) {
            PTag = PTag?.nextElementSibling() // Move to the next sibling 5 times
        }
        PTag?.select("div.ntable-wrapper td")?.map { Ep ->
            val href= Ep.selectFirst("a")?.attr("href") ?:""
            val episode=Ep.selectFirst("a")?.text()
            if (href.isNotEmpty())
            {
                episodes.add(Episode(href, episode, posterUrl = poster))
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot=description
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, subtitleCallback, callback)
        return true
    }
}
