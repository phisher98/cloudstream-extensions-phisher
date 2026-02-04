package com.animeworld

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
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
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AnimeWorld : MainAPI() {
    override var mainUrl = "https://myanimeworld.in"
    override var name = "AnimeWorld"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.Anime, TvType.Cartoon)

    override val mainPage =
        mainPageOf(
            "category/popular" to "Popular",
            "category/top-airing" to "Top Airing",
            "category/ongoing" to "OnGoing",
            "category/series" to "Series",
            "category/movies" to "Movies",
            "category/anime" to "Anime",
            "category/cartoon" to "Cartoon"
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse = newHomePageResponse(
        request.name,
        listOf(page, page + 1).flatMap { p ->
            app.get("$mainUrl/${request.data}?page=$p")
                .documentLarge
                .select("#movies-a ul li")
                .mapNotNull { it.toSearchResult() }
        }
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("header h2")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String,page: Int): SearchResponseList {
        val document = app.get("$mainUrl/?s=$query&page=$page").documentLarge
        return document.select("#movies-a ul li").mapNotNull { it.toSearchResult() }.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title =document.selectFirst("div.dfxb  h1.entry-title")?.text() ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: throw NotImplementedError("Unable to find title")
        val poster = fixUrlNull(document.selectFirst("div.dfxb img")?.attr("src"))
        val backgroundposter = fixUrlNull(document.selectFirst("div.bghd img")?.attr("src"))
        val tags = document.select("header.entry-header ul li:contains(Genres) p a").map { it.text() }
        val year = document.select("span.year span").text().trim().toIntOrNull()
        val tvType = if (url.contains("movie")) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("div.description p")?.text()?.trim()
        val recommendations = document.select("section.section.episodes div.owl-carousel article").mapNotNull { it.toSearchResult() }
        return if (tvType == TvType.TvSeries) {

            val seasonLinks = document
                .select("div.season-swiper a.season-btn")
                .map { it.attr("href") }

            val episodes = seasonLinks.flatMap { seasonUrl ->
                val seasonDoc = app.get(mainUrl + seasonUrl).documentLarge

                seasonDoc.select("#episode_by_temp li").map { ep ->
                    val headerSpan = ep.selectFirst("header.entry-header span")?.text().orEmpty()
                    val (season, episode) = headerSpan
                        .split("x", limit = 2)
                        .map { it.toIntOrNull() }
                        .let { it.getOrNull(0) to it.getOrNull(1) }

                    val episodeNumber = episode?.toString().orEmpty()
                    val name = "Episode $episodeNumber"

                    newEpisode(ep.selectFirst("a")?.attr("href").orEmpty()) {
                        this.name = name
                        this.season = season
                        this.episode = episode
                        this.posterUrl = ep.selectFirst("div.post-thumbnail img")?.getImageAttr()
                    }
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.backgroundPosterUrl = backgroundposter
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.backgroundPosterUrl = backgroundposter
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
        document.select("div.video iframe").forEach {
            val link=it.attr("src").ifBlank { it.attr("data-src") }
            loadExtractor(link, mainUrl, subtitleCallback, callback)
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("src") -> this.attr("src")
            this.hasAttr("data-src") -> this.attr("data-src")
            else -> this.attr("src")
        }
    }

}
