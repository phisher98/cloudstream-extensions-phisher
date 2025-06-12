package com.phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AnimeWorld : MainAPI() {
    override var mainUrl = "https://myanimeworld.in"
    override var name = "AnimeWorld"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)
    companion object
    {
        const val API="https://z.awstream.net"
    }

    override val mainPage =
            mainPageOf(
                "series" to "Series",
                "movies" to "Movies",
                "platform/netflix" to "Netflix",
                "platform/crunchyroll" to "Crunchyroll",
                "genre/kids" to "Cartoon Network"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val currentPageUrl = "$mainUrl/${request.data}?page=$page"
        val nextPageUrl = "$mainUrl/${request.data}?page=${page + 1}"

        val currentPageDocument = app.get(currentPageUrl).document
        val nextPageDocument = app.get(nextPageUrl).document

        val currentResults = currentPageDocument.select("#movies-a ul li").mapNotNull { it.toSearchResult() }
        val nextResults = nextPageDocument.select("#movies-a ul li").mapNotNull { it.toSearchResult() }

        val combinedResults = currentResults + nextResults

        return newHomePageResponse(request.name, combinedResults)
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("header h2")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?q=$query").document
        return document.select("#movies-a ul li").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title =document.selectFirst("div.dfxb  h2.entry-title")?.text() ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: throw NotImplementedError("Unable to find title")
        val poster = fixUrlNull(document.selectFirst("div.bghd img")?.attr("src"))
        val tags = document.select("header.entry-header ul li:contains(Genres) p a").map { it.text() }
        val year = document.select("span.year span").text().trim().toIntOrNull()
        val tvType = if (url.contains("movie")) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("div.description p")?.text()?.trim()
        val recommendations = document.select("section.section.episodes div.owl-carousel article").mapNotNull { it.toSearchResult() }
        return if (tvType == TvType.TvSeries) {
                val episodes = mutableListOf<Episode>()
                document.select("#episode_by_temp li").map { ep->
                        val href = ep.select("a").attr("href")
                        val name = "Episode "+ ep.select("header.entry-header h2").text().substringAfter("EP").trim()
                        val image = ep.select("div.post-thumbnail img").attr("data-src")
                        val episode =ep.select("header.entry-header span").text().substringAfter("x").toIntOrNull()
                        episodes.add(newEpisode(href)
                        {
                            this.name=name
                            this.episode=episode
                            this.posterUrl=image
                        })
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val scriptWithServerData = document.select("script")
            .firstOrNull { it.data().contains("serverData") }?.data() ?: return false
        val regex = Regex("""https:\\/\\/[^\s"']+""")
        val urls = regex.findAll(scriptWithServerData).map { it.value.replace("\\/", "/") }.toList()
        urls.forEach { url ->
            Log.d("Phisher",url)
            loadExtractor(url, mainUrl, subtitleCallback, callback)
        }

        return true
    }

}
