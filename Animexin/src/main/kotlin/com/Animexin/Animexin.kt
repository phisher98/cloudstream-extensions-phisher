package com.Animexin

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class Animexin : MainAPI() {
    override var mainUrl              = "https://animexin.dev"
    override var name                 = "Animexin"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=ongoing&order=update" to "Recently Updated",
        "anime/?status=ongoing&order&order=popular" to "Popular",
        "anime/?" to "Donghua",
        "anime/?status=&type=movie&page=" to "Movies",
        "anime/?sub=raw" to "Anime (RAW)",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").documentLarge
        val home     = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.bsx > a").attr("title")
        val href      = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val document = app.get("${mainUrl}/page/$page/?s=$query").documentLarge
        val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }.toNewSearchResponseList()
        return results
    }

    @Suppress("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href=document.selectFirst("div.eplister > ul > li a")?.attr("href") ?:""
        val poster = document.select("div.thumb img").attr("src").ifEmpty { document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString() }
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type=document.selectFirst(".spe")?.text().toString()
        val tvtag=if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val episodeRegex = Regex("(\\d+)")

            val episodes = document.select("div.eplister > ul > li").map { info ->
                val href1 = info.select("a").attr("href")
                val posterr = info.selectFirst("a img")?.attr("src") ?: ""

                val epText = info.selectFirst("div.epl-num")?.text().orEmpty()
                val epnum = episodeRegex.find(epText)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(href1) {
                    this.episode = epnum
                    this.name = epnum?.let { "Episode $it" } ?: epText
                    this.posterUrl = posterr
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        document.select(".mobius option").forEach { server->
            val base64 = server.attr("value")
            val decoded=base64Decode(base64)
            val doc = Jsoup.parse(decoded)
            val href=doc.select("iframe").attr("src")
            val url=Http(href)
            loadExtractor(url,subtitleCallback, callback)
        }
        return true
    }
}
