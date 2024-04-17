package com.horis.cloudstreamplugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnimehubProvider : MainAPI() {
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )
    override var lang = "en"

    override var mainUrl = "https://123animehub.cc"
    override var name = "Animehub"

    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/home" to "Recently Updated",
        "$mainUrl/type/tv%20series" to "Tv Series",
        "$mainUrl/type/movies" to "Movies",
        "$mainUrl/type/ona" to "Ona"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page == 1) {
            request.data
        } else {
            "${request.data}/page/$page/"
        }

        val document = app.get(url, referer = "$mainUrl/").document
        val items = document.select(".film-list > .item").mapNotNull {
            it.toHomePageResult()
        }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toHomePageResult(): SearchResponse? {
        val title = select("a").lastOrNull()?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("data-src"))

        return newAnimeSearchResponse(title, LoadUrl(href, posterUrl).toJson()) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?keyword=$query"
        val document = app.get(url, referer = "$mainUrl/").document

        val items = document.select(".film-list > .item").mapNotNull {
            it.toHomePageResult()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = tryParseJson<LoadUrl>(url) ?: return null
        val document = app.get(d.url, referer = "$mainUrl/").document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null

        val id = document.select("#servers-container").attr("data-id")

        val doc = Jsoup.parse(
            app.get(
                "https://123animehub.cc/ajax/film/sv?id=$id&ts=001&_=840",
                referer = "$mainUrl/"
            ).parsedSafe<HtmlData>()?.html ?: return null
        )
        val server = doc.selectFirst(".mass")?.attr("data-name") ?: return null

        val episodes = doc.select(".episodes li a").mapNotNull {
//            val href = fixUrl(it?.attr("href") ?: return@mapNotNull null)
            val name = it.text()
            val epid = it.attr("data-id")
            newEpisode(EpisodeData(epid, server)) {
                this.name = name
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = d.posterUrl
            plot = document.select(".desc").text()
            year = document.select("dl.meta dt:containsOwn(Released:) + dd").text().toIntOrNull()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val data1 = tryParseJson<EpisodeData>(data) ?: return false

        val data2 = app.get(
            "https://123animehub.cc/ajax/episode/info?epr=${data1.epid}/${data1.server}&ts=001&_=${System.currentTimeMillis()}",
            verify = false,
            referer = "$mainUrl/"
        ).parsedSafe<PlayUrl>() ?: return false
        val newUrl = app.options(data2.target, referer = "$mainUrl/").okhttpResponse.request.url.toString()
        loadExtractor(newUrl, subtitleCallback, callback)
        return true
    }

    data class LoadUrl(
        val url: String,
        val posterUrl: String?
    )

    data class EpisodeData(
        val epid: String,
        val server: String
    )

    data class PlayUrl(
        val target: String
    )

    data class HtmlData(
        val html: String
    )

}
