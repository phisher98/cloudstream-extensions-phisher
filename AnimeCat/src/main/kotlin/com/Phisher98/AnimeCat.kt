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

class AnimeCat : MainAPI() {
    override var mainUrl = "https://anime.cat"
    override var name = "AnimeCat"
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
                    "category/status/ongoing" to "On-Air Shows",
                    "category/type/anime/?type=series" to "New Anime Arrivals",
                    "category/type/cartoon/?type=series" to "Just In: Cartoon Series",
                    "category/type/anime/?type=movies" to "Latest Anime Movies",
                    "category/type/cartoon/?type=movies" to "Fresh Cartoon Films",
                    "category/network/crunchyroll" to "Crunchyroll",
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
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").document
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title =document.selectFirst("article h1")?.text()?: throw NotImplementedError("Unable to find title")
        val poster = fixUrlNull(document.selectFirst("div.bgft img")?.attr("src"))
        val tags = document.select("header.entry-header ul li:contains(Genres) p a").map { it.text() }
        val year = document.select("span.year span").text().trim().toIntOrNull()
        val tvType = if (url.contains("movie")) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("div.description p")?.text()?.trim()
        val recommendations = document.select("section.section.episodes div.owl-carousel article").mapNotNull { it.toSearchResult() }
        return if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("ul.aa-cnt.sub-menu li").map {
                    val id=it.select("a").attr("data-post")
                    val dataseason=it.select("a").attr("data-season")
                    app.post("${mainUrl}/wp-admin/admin-ajax.php", data = mapOf("action" to "action_select_season","season" to dataseason,"post" to id)).document.select("li article").forEach { ep->
                        val href = ep.select("a").attr("href")
                        val name = ep.select("header.entry-header h2").text().trim()
                        val image = ep.select("div.post-thumbnail img").attr("src")
                        val episode =ep.select("header.entry-header span").text().substringAfter("x").toIntOrNull()
                        val season =ep.select("header.entry-header span").text().substringBefore("x").toIntOrNull()
                        episodes.add(newEpisode(href)
                        {
                            this.name=name
                            this.season=season
                            this.episode=episode
                            this.posterUrl=image
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
        app.get(data).document.select("section.section.player iframe").forEach { iframeElement ->
            Log.d("Phisher",iframeElement.toString())
            loadExtractor(iframeElement.attr("data-src"),mainUrl,subtitleCallback, callback)
        }
        return true
    }
}
