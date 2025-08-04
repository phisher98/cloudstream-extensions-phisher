package com.phisher98

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

class Animesalt : MainAPI() {
    override var mainUrl = "https://animesalt.cc"
    override var name = "Animesalt"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage =
            mainPageOf(
                    "category/status/ongoing" to "On-Air Shows",
                    "category/type/anime/?type=series" to "New Anime Arrivals",
                    "category/type/cartoon/?type=series" to "Just In: Cartoon Series",
                    "category/type/anime/?type=movies" to "Latest Anime Movies",
                    "category/type/cartoon/?type=movies" to "Fresh Cartoon Films",
                    "category/network/crunchyroll" to "Crunchyroll",
                    "category/network/netflix" to "Netflix",
                    "category/network/prime-video" to "Prime Video"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url=if(request.data.contains("type="))
        {
            val data=request.data.split("/?type=")
            "$mainUrl/${data[0]}/page/$page/?type=${data[1]}"
        }
        else
        {
            "$mainUrl/${request.data}/page/$page"
        }
        val document = app.get(url).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("header h2")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun Element.toRecommend(): SearchResponse {
        val title = ""
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").document
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title =document.selectFirst("h1")?.text()?: throw NotImplementedError("Unable to find title")
        val poster = fixUrlNull(document.selectFirst("div.bgft img")?.attr("data-src"))
        val sections = listOf("Genres", "Languages")
        val tags: List<String> = sections.flatMap { label ->
            document.select("h4:contains($label)")
                .first()
                ?.nextElementSibling()
                ?.select("a")
                ?.map { it.text() }
                ?: emptyList()
        }
        val yearDiv = document.select("div").firstOrNull { it.text().trim().matches(Regex("\\d{4}")) }
        val year = yearDiv?.text()?.trim()?.toIntOrNull()
        val tvType = if (url.contains("movies")) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("#overview-text p")?.text()?.trim()
        val recommendations = document.select("section.section.episodes div.owl-carousel article").mapNotNull { it.toRecommend() }
        return if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.season-buttons a").forEach { seasonBtn ->
                val postId = seasonBtn.attr("data-post")
                val dataSeason = seasonBtn.attr("data-season")

                val seasonResponse = app.post(
                    "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "action_select_season",
                        "season" to dataSeason,
                        "post" to postId
                    )
                )

                seasonResponse.document.select("li article").forEachIndexed { index, ep ->
                    val href = ep.select("a").attr("href")
                    val image = ep.select("div.post-thumbnail img").attr("src")
                    val spanText = ep.select("h2.entry-title").text()
                    val season = dataSeason.toIntOrNull()
                    val episodeNumber = index + 1
                    val epName = if (spanText.contains("x$episodeNumber")) {
                        "Episode $episodeNumber"
                    } else {
                        spanText
                    }

                    episodes.add(newEpisode(href) {
                        this.name = epName
                        this.season = season
                        this.episode = episodeNumber
                        this.posterUrl = image
                    })
                }
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
        app.get(data).document.select("#options-0 iframe").forEach { iframeElement ->
            loadExtractor(iframeElement.attr("data-src"),mainUrl,subtitleCallback, callback)
        }
        return true
    }
}
