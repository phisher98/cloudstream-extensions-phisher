package com.Phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element

class FourKHDHub : MainAPI() {
    override var mainUrl              = "https://4khdhub.fans"
    override var name                 = "4K HDHUB"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.TvSeries)


    companion object {
        private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        private var cachedDomains: DomainsParser? = null

        suspend fun getDomains(forceRefresh: Boolean = false): DomainsParser? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<DomainsParser>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }
    }

    override val mainPage = mainPageOf(
        "" to "Latest Releases",
        "category/movies-10810.html" to "Movies",
        "category/new-series-10811.html" to "Series",
        "category/anime-10812.html" to "Anime",
        "category/4k-hdr-10776.html" to "4K HDR"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val allResults = mutableListOf<SearchResponse>()
        var currentPage = page
        val maxPages = 3
        val FourKHDHubAPI = getDomains()?.n4khdhub

        while (true) {
            val url = "$FourKHDHubAPI/${request.data}/page/$currentPage.html"
            val document = app.get(url).document
            val results = document.select("div.card-grid a").mapNotNull {
                it.toSearchResult()
            }

            if (results.isEmpty() || currentPage - page + 1 >= maxPages) break

            allResults += results
            currentPage++
        }

        return newHomePageResponse(request.name, allResults)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text() ?: return null
        val href = this.attr("href")
        val posterUrl = this.select("img").attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val FourKHDHubAPI = getDomains()?.n4khdhub
        val document = app.get("$FourKHDHubAPI/?s=$query").document
        val results = document.select("div.card-grid a").mapNotNull {
            it.toSearchResult()
        }
        return results
    }

    @Suppress("NAME_SHADOWING")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.page-title")?.text()?.substringBefore("(")?.trim().toString()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.mt-2 span")?.text()?.toIntOrNull()
        val tvType = when (true) {
            ("Movies" in tags) -> TvType.Movie
            else -> TvType.TvSeries
        }

        val hrefs: List<String> = document.select("div.download-item a").eachAttr("href")

        val description = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val trailer = document.selectFirst("#trailer-btn")?.attr("data-trailer-url")

        val recommendations = document.select("div.card-grid-small a").mapNotNull {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val tvSeriesEpisodes = mutableListOf<Episode>()

            val episodesMap = mutableMapOf<Pair<Int, Int>, MutableList<String>>() // key: (season, episode) -> list of hrefs

            document.select("div.episodes-list div.season-item").forEach { seasonElement ->
                val seasonText = seasonElement.select("div.episode-number").text()
                val season = Regex("""S?([1-9][0-9]*)""").find(seasonText)?.groupValues?.get(1)?.toIntOrNull()

                seasonElement.select("div.episode-download-item").forEach { episodeItem ->
                    val episodeText = episodeItem.select("div.episode-file-info span.badge-psa").text()
                    val episode = Regex("""Episode-0*([1-9][0-9]*)""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()

                    val hrefs = episodeItem.select("a")
                        .mapNotNull { it.attr("href").takeIf { it.isNotBlank() } }

                    if (season != null && episode != null && hrefs.isNotEmpty()) {
                        val key = season to episode
                        episodesMap.getOrPut(key) { mutableListOf() }.addAll(hrefs)
                    }
                }
            }

            episodesMap.forEach { (seasonEpisode, hrefs) ->
                val (season, episode) = seasonEpisode
                val distinctHrefs = hrefs.distinct()

                tvSeriesEpisodes += newEpisode(distinctHrefs) {
                    this.season = season
                    this.episode = episode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, tvSeriesEpisodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
        else {
            newMovieLoadResponse(title, url, TvType.Movie, hrefs) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = Regex("""https?://[^\s'",\]\[]+""").findAll(data).map { it.value }

        val extractors = mapOf(
            "hubdrive" to Pair("HUB Drive", Hubdrive()),
            "hubcloud" to Pair("Hub Cloud", HubCloud())
        )

        for (link in links) {
            try {
                val resolvedLink = try {
                    if ("id=" in link.lowercase()) getRedirectLinks(link) else link
                } catch (e: Exception) {
                    Log.e("Phisher", "Redirect failed for $link $e")
                    continue
                }

                val resolvedLower = resolvedLink.lowercase()
                var matched = false

                for ((key, value) in extractors) {
                    if (key in resolvedLower) {
                        matched = true
                        try {
                            value.second.getUrl(resolvedLink, value.first, subtitleCallback, callback)
                        } catch (e: Exception) {
                            Log.e(key, "Failed: $resolvedLink $e")
                        }
                    }
                }

                if (!matched) {
                    Log.w("Extractor", "Unknown host: $resolvedLink")
                }
            } catch (e: Exception) {
                Log.e("Extractor", "Unexpected error with link: $link $e")
            }
        }

        return true
    }


}
