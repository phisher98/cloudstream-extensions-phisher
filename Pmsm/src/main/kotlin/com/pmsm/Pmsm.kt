package com.pmsm

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidStack
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
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Pmsm : MainAPI() {
    override var mainUrl: String = runBlocking {
        PmsmPlugin.getDomains()?.pencurimoviesubmalay ?: "https://ww105.pencurimoviesubmalay.guru"
    }
    override var name = "PMSM"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "movies" to "Movies",
        "tvshows" to "TV Shows",
        "group_movie/indonesia" to "Indonesia",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val items = document.select("div.item-box").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(this.select("a").attr("href"))
        val title = this.select("h3").text().substringBeforeLast("(")
        val posterUrl = this.selectFirst("img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", timeout = 60).document
        val list = document.select("div.item-box").mapNotNull { it.toSearchResult() }
        return list.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val isSeries = url.contains("/tvshows/")
        val rawTitle = document.selectFirst("div.details-title h3")?.text()?.trim().orEmpty()
        val title = if (isSeries) rawTitle else rawTitle.substringBeforeLast("(").trim().ifBlank { rawTitle }
        val poster = fixUrlNull(document.selectFirst("div.content-poster img")?.attr("src"))
        val bgposter = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")?.replace("w780","original"))

        val description = document.selectFirst("div.details-desc p")?.text()?.trim()
        val tags = document.select("div.details-genre a").map { it.text().trim() }.filter { it.isNotBlank() }
        val actors = document.select("div.details-info p:contains(Stars) a").map { it.text().trim() }.filter { it.isNotBlank() }
        val year = extractYear(document.selectFirst("div.details-info p:contains(Year)")?.text() ?: rawTitle)
        val duration = document.selectFirst("span[itemprop=duration]")?.text()?.replace(Regex("\\D"), "")
            ?.toIntOrNull()
        val rating = Regex("""(\d+(\.\d+)?)""").find(document.selectFirst("span.data-imdb")?.text().orEmpty())
            ?.groupValues?.firstOrNull()?.toDoubleOrNull()
        val trailerId = document.selectFirst("span.data-trailer[data-tid], a.btn-trailer[data-tid]")
            ?.attr("data-tid")?.trim()
        val trailerUrl = trailerId?.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }
        val recommendations = document.select("div.module-item")
            .mapNotNull { it.toSearchResult() }
            .filter { it.url != url }
            .distinctBy { it.url }

        return if (isSeries) {
            val episodes = document.select("div.content-episodes ul.episodes-list li")
                .mapNotNull { it.toEpisode() }
                .distinctBy { it.data }
                .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgposter
                this.plot = description
                this.tags = tags
                this.year = year
                this.recommendations = recommendations
                if (duration != null) this.duration = duration
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                addTrailer(trailerUrl)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgposter
                this.plot = description
                this.tags = tags
                this.year = year
                this.recommendations = recommendations
                if (duration != null) this.duration = duration
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                addTrailer(trailerUrl)
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
        document.select("#playeroptionsul > li").amap { li->
            val post = li.attr("data-post").trim()
            val nume = li.attr("data-nume").trim()
            val type = li.attr("data-type").trim()

            val res = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "zeta_player_ajax",
                    "post" to post,
                    "nume" to nume,
                    "type" to type
                ),
                referer = data,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<ZetaPlayerResponse>()?.embedUrl ?: return@amap

            val link = Jsoup.parse(res).selectFirst("iframe")?.attr("src") ?: return@amap
            if (link.contains("/#"))
            {
                VidStack().getUrl(link,"",subtitleCallback,callback)
            }
            else
            {
                loadExtractor(link,mainUrl,subtitleCallback,callback)
            }
        }

        return true
    }

    private fun Element.toEpisode(): Episode? {
        val anchor = selectFirst("a[href]") ?: return null
        val href = fixUrl(anchor.attr("href"))
        val epNum = selectFirst("span.ep-num")?.text()?.trim()?.toIntOrNull()
        val title = selectFirst("span.ep-title")?.text()?.trim()
        val poster = selectFirst("span.ep-thumb img")?.attr("src")?.let { fixUrlNull(it) }
        val rawDate = selectFirst("span.ep-date")?.text()
        val date = rawDate
            ?.replace(".", "")
            ?.let {
                runCatching {
                    SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(it)
                }.getOrNull()
            }
        val classSeason = classNames()
            .firstNotNullOfOrNull { Regex("""ep-(\d+)-\d+""").find(it)?.groupValues?.getOrNull(1) }
            ?.toIntOrNull()
        val parentSeason = parent()?.id()
            ?.substringAfter("season-listep-", "")
            ?.toIntOrNull()
        val season = classSeason ?: parentSeason

        return newEpisode(href) {
            this.name = title
            this.posterUrl = poster
            this.episode = epNum
            this.season = season
            addDate(date)
        }
    }
    private fun extractYear(text: String?): Int? {
        return Regex("""(19|20)\d{2}""").find(text.orEmpty())?.value?.toIntOrNull()
    }

    private data class ZetaPlayerResponse(
        @param:JsonProperty("embed_url") val embedUrl: String? = null,
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("msg") val msg: String? = null
    )
}

