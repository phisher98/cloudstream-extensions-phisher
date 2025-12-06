package com.toonhub4u


import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.json.JSONArray

class Toonhub4u : MainAPI() {
    override var mainUrl              = "https://toonhub4u.me"
    override var name                 = "ToonHub4u"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.Cartoon)

    override val mainPage = mainPageOf(
        "category/anime/anime-series" to "Anime Series",
        "category/anime/anime-movies" to "Anime Movies",
        "category/animated/animation-movies" to "Animated Movies",
        "category/animated/animated-series" to "Animated Series",
        "category/channel-list/cartoon-network" to "Cartoon Network",
        "category/channel-list/disney-xd-india" to "Disney XD India",
        "category/channel-list/disney" to "Disney",
        "category/ott-network/crunchyroll" to "Crunchyroll",
        "category/ott-network/amazon-prime-video" to "Amazon Prime Video",
        "category/ott-network/netflix" to "Netflix",
        "category/ott-network/jio-cinema" to "Jio Cinema",
        "category/language/hindi" to "Hindi Language"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").documentLarge
        val home     = document.select("li.post-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("a").text().trim().substringBefore("[")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.select("a img").attr("data-src").ifEmpty { this.select("a img").attr("src") }
        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=$query").documentLarge
        val results = document.select("li.post-item").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore("[")?.substringBefore("1080")?.trim().toString()
        val backgroundposter = document.select("meta[property=og:image]").attr("content")
        val poster= document.select("p:nth-child(3) > img").attr("src")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val tvtag=if (document.select("div.entry-content p strong").text().contains("TV Series")) TvType.TvSeries else TvType.Movie
        val hrefs = document.select("div.mks_toggle_content a").map { it.attr("href").replace("/file/","/embed/") }.toJson()
        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select(".entry-content.entry.clearfix").forEach { content ->
                content.select("p").forEach { pTag ->
                    val episodeMatch = Regex("Episode\\s*(\\d+)").find(pTag.text())
                    if (episodeMatch != null) {
                        val episodeNumber = episodeMatch.groupValues[1].toIntOrNull()
                        val episodeLinks = mutableListOf<String>()
                        var nextSibling = pTag.nextElementSibling()
                        while (nextSibling != null && nextSibling.tagName() != "hr") {
                            if (nextSibling.tagName() == "p") {
                                nextSibling.select("a[href]").forEach { aTag ->
                                    episodeLinks.add(aTag.attr("href").replace("/file/","/embed/"))
                                }
                            }
                            nextSibling = nextSibling.nextElementSibling()
                        }
                        episodes+=newEpisode(episodeLinks.toJson())
                        {
                            this.name="Episode $episodeNumber"
                        }
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter
                this.plot = description
            }
        }
        else {
            newMovieLoadResponse(title, url, TvType.Movie, hrefs) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val jsonArray = JSONArray(data)
        val links = List(jsonArray.length()) { jsonArray.getString(it) }
        coroutineScope {
            links.map { link ->
                launch {
                    try {
                        loadExtractor(link, subtitleCallback, callback)
                    } catch (e: Exception) {
                        Log.e("ToonHub", "Error loading $link: ${e.message}")
                    }
                }
            }.joinAll()
        }

        return true
    }
}
