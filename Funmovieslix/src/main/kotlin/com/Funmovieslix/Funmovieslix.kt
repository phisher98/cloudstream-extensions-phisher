package com.Funmovieslix

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
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

class Funmovieslix : MainAPI() {
    override var mainUrl = "https://funmovieslix.com"
    override var name = "Funmovieslix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "category/action" to "Action Category",
        "category/science-fiction" to "Sci-Fi Category",
        "category/drama" to "Drama Category",
        "category/kdrama" to "KDrama",
        "category/crime" to "Crime Category",
        "category/fantasy" to "Fantasy Category",
        "category/mystery" to "Mystery Category",
        "category/comedy" to "Comedy Category",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").documentLarge
        val home = document.select("#gmr-main-load div.movie-card").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h3").text()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.select("a img").firstOrNull()?.let { img ->
            val srcSet = img.attr("srcset")
            val bestUrl = if (srcSet.isNotBlank()) {
                srcSet.split(",")
                    .map { it.trim() }
                    .maxByOrNull { it.substringAfterLast(" ").removeSuffix("w").toIntOrNull() ?: 0 }
                    ?.substringBefore(" ")
            } else {
                img.attr("src")
            }

            fixUrlNull(bestUrl?.replace(Regex("-\\d+x\\d+"), ""))
        }
        val searchQuality = getSearchQuality(this)
        val score=this.select("div.rating-stars").text().substringAfter("(").substringBefore(")")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = searchQuality
            this.score=Score.from10(score)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query").documentLarge
            val results =document.select("#gmr-main-load div.movie-card").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title =document.select("meta[property=og:title]").attr("content").substringBefore("(").substringBefore("-").trim()
        val poster = document.select("meta[property=og:image]").attr("content")
        val description = document.select("div.desc-box p,div.entry-content p").text()
        val actors=document.select("div.cast-grid a").map { it.text() }
        val type = if (url.contains("tv")) TvType.TvSeries else TvType.Movie
        val trailer = document.select("meta[itemprop=embedUrl]").attr("content")
        val genre = document.select("div.gmr-moviedata:contains(Genre) a,span.badge").map { it.text() }
        val year =document.select("div.gmr-moviedata:contains(Year) a").text().toIntOrNull()
        val recommendation = document.select("div.movie-grid div").mapNotNull {
            val recName = it.select("p").text()
            val recHref = it.select("a").attr("href")
            val img = it.selectFirst("img")
            val srcSet = img?.attr("srcset").orEmpty()
            val bestUrl = if (srcSet.isNotBlank()) {
                srcSet.split(",")
                    .map { s -> s.trim() }
                    .maxByOrNull { s -> s.substringAfterLast(" ").removeSuffix("w").toIntOrNull() ?: 0 }
                    ?.substringBefore(" ")
            } else {
                img?.attr("src")
            }
            val recPosterUrl = fixUrlNull(bestUrl?.replace(Regex("-\\d+x\\d+"), ""))
            newMovieSearchResponse(recName, recHref, TvType.Movie) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.gmr-listseries a").forEach { info ->
                    if (info.text().contains("All episodes", ignoreCase = true)) return@forEach
                    val text=info.text()
                    val season = Regex("S(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    val ep=Regex("Eps(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    val name = "Episode $ep"
                    val href = info.attr("href")
                    episodes.add(
                        newEpisode(href)
                        {
                            this.episode=ep
                            this.name=name
                            this.season=season
                        }
                    )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge

        // 1. Get all <script> tags that contain "embeds"
        val scriptContent = document.select("script")
            .map { it.data() }
            .firstOrNull { it.contains("const embeds") }
            ?: return false

        val regex = Regex("""https:\\/\\/[^"]+""")
        val urls = regex.findAll(scriptContent)
            .map { it.value.replace("\\/", "/").replace("\\", "") } // unescape \/ → / and remove \
            .toList()
        urls.forEach { url ->
            loadExtractor(url,subtitleCallback,callback)
        }
        return true
    }

    fun getSearchQuality(parent: Element): SearchQuality {
        val qualityText = parent.select("div.quality-badge").text().uppercase()

        return when {
            qualityText.contains("HDTS") -> SearchQuality.HdCam
            qualityText.contains("HDCAM") -> SearchQuality.HdCam
            qualityText.contains("CAM") -> SearchQuality.Cam
            qualityText.contains("HDRIP") -> SearchQuality.WebRip
            qualityText.contains("WEBRIP") -> SearchQuality.WebRip
            qualityText.contains("WEB-DL") -> SearchQuality.WebRip
            qualityText.contains("BLURAY") -> SearchQuality.BlueRay
            qualityText.contains("4K") -> SearchQuality.FourK
            qualityText.contains("HD") -> SearchQuality.HD
            else -> SearchQuality.HD
        }
    }

}

