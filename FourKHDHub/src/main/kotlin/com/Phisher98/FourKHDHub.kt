package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

class FourKHDHub : MainAPI() {
    override var mainUrl: String = runBlocking {
        FourKHDHubProvider.getDomains()?.n4khdhub ?: "https://4khdhub.fans"
    }
    override var name                 = "4K HDHUB"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.TvSeries)


    override val mainPage = mainPageOf(
        "" to "Latest Releases",
        "category/movies-10810" to "Movies",
        "category/new-series-10811" to "Series",
        "category/anime-10812" to "Anime",
        "category/4k-hdr-10776" to "4K HDR"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}/page/$page"
        val document = app.get(url).document
        val results = document.select("div.card-grid a").mapNotNull {
                it.toSearchResult()
        }
        return newHomePageResponse(request.name, results)
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
        val document = app.get("$mainUrl/?s=$query").document
        val results = document.select("div.card-grid a").mapNotNull {
            it.toSearchResult()
        }
        return results
    }

    @RequiresApi(Build.VERSION_CODES.N)
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
            val episodesMap = mutableMapOf<Pair<Int, Int>, MutableList<String>>() // season, episode -> hrefs
            val maxEpisodePerSeason = mutableMapOf<Int, Int>() // season -> highest episode

            document.select("div.episodes-list div.season-item").forEach { seasonElement ->
                val seasonText = seasonElement.select("div.episode-number").text()
                val season = Regex("""S?([1-9][0-9]*)""").find(seasonText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@forEach

                seasonElement.select("div.episode-download-item").forEach { episodeItem ->
                    val episodeText = episodeItem.select("div.episode-file-info span.badge-psa").text()
                    val episode = Regex("""Episode-0*([1-9][0-9]*)""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: return@forEach

                    val hrefs = episodeItem.select("a").mapNotNull { it -> it.attr("href").takeIf { it.isNotBlank() } }
                    if (hrefs.isNotEmpty()) {
                        val key = season to episode
                        episodesMap.getOrPut(key) { mutableListOf() }.addAll(hrefs)
                        maxEpisodePerSeason[season] = maxOf(maxEpisodePerSeason.getOrDefault(season, 0), episode)
                    }
                }
            }

            episodesMap.toSortedMap(compareBy({ it.first }, { it.second })).forEach { (seasonEpisode, hrefs) ->
                val (season, episode) = seasonEpisode
                tvSeriesEpisodes += newEpisode(hrefs.distinct()) {
                    this.season = season
                    this.episode = episode
                }
            }

            document.select("div.download-item").forEach { item ->
                val headerText = item.select("div.flex-1.text-left.font-semibold").text()

                val season = Regex("""S([0-9]+)""").find(headerText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@forEach

                val size = Regex("""(\d+(?:\.\d+)?\s*GB)""").find(headerText)?.groupValues?.get(1) ?: "Unknown Size"
                val quality = Regex("""(\d{3,4}p)""").find(headerText)?.groupValues?.get(1) ?: "Unknown Quality"

                val href = item.select("a").mapNotNull { it -> it.attr("href").takeIf { it.isNotBlank() } }

                val fileTitle = item.select("div.file-title").text()
                    .replace(Regex("""\[[^]]*]"""), "") // remove language/codec details
                    .replace(Regex("""\(.+?\)"""), "")   // remove source/site tags

                if (hrefs.isNotEmpty()) {
                    var nextEpisode = maxEpisodePerSeason.getOrDefault(season, 0) + 1
                        tvSeriesEpisodes += newEpisode(href.distinct()) {
                            this.season = season
                            this.episode = nextEpisode
                            this.name = "S${season.toString().padStart(2,'0')} – $fileTitle [$quality, $size]".trim()
                        }
                        nextEpisode++
                    maxEpisodePerSeason[season] = nextEpisode - 1
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
                    if ("id=" in link.lowercase()) {
                        getRedirectLinks(link)
                    } else {
                        link
                    }
                } catch (e: Exception) {
                    Log.e("Phisher", "Redirect failed for $link $e")
                    continue
                }
                Log.d("Phisher",resolvedLink.toJson())

                val resolvedLower = resolvedLink.lowercase()
                var matched = false

                for ((key, value) in extractors) {
                    if (key in resolvedLower) {
                        matched = true
                        try {
                            value.second.getUrl(resolvedLink, value.first, subtitleCallback, callback)
                        } catch (_: Exception) {
                            Log.e(key, "Extractor failed for $resolvedLink")
                        }
                    }
                }

                if (!matched) {
                    Log.w("Extractor", "No extractor matched: $resolvedLink")
                }
            } catch (_: Exception) {
                Log.e("Extractor", "Unexpected error while processing: $link")
            }
        }

        return true
    }

}
