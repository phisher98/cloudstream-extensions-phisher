package com.DoraBash

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DoraBash : MainAPI() {
    override var mainUrl = "https://dorabash.in"
    override var name = "DoraBash"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "anime-type/tv" to "Seasons",
        "anime-type/movie" to "Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}").documentLarge
        val home = document.select("article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private suspend fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h3 a").attr("title").substringAfter("Doraemon")
        val href = fixUrl(this.select("h3 > a").attr("href"))
        val sourceURL = app.get(href).document.select("div.anime-data h4 a").attr("href")
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newMovieSearchResponse(title.capitalize(), sourceURL, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("meta[property=og:title]").attr("content").substringBeforeLast("-").trim()
        val backgroundposter = doc.select("main div.absolute img").attr("src")
        val description = doc.selectFirst("div.mb-6 > section > p:nth-child(1)")?.text()?.trim()
        val poster = doc.select("meta[property=og:image]").attr("content").trim()
        val rating = doc.select("div.flex.flex-wrap.justify-center.lg\\:justify-start.gap-1.lg\\:gap-2.mb-4.text-sm.font-semibold span:nth-child(1)").text()
        val year = doc.select("div.flex.flex-wrap.justify-center.lg\\:justify-start.gap-1.lg\\:gap-2.mb-4.text-sm.font-semibold span:nth-child(4)").text()
        val contentRating = doc.select("div.flex.flex-wrap.justify-center.lg\\:justify-start.gap-1.lg\\:gap-2.mb-4.text-sm.font-semibold span:nth-child(7)").text()
        val duration = doc.select("div.flex.flex-wrap.justify-center.lg\\:justify-start.gap-1.lg\\:gap-2.mb-4.text-sm.font-semibold span:nth-child(8)").text()

        val type = doc.select("div.flex.flex-wrap.justify-center.lg\\:justify-start.gap-1.lg\\:gap-2.mb-4.text-sm.font-semibold span:nth-child(2)").text()
        val tvtag = if (type.contains("Movie",ignoreCase = true)) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val seasonId = doc.selectFirst("#seasonContent")?.attr("data-season")

            val episodes = app.get("$mainUrl/wp-admin/admin-ajax.php?action=get_episodes&anime_id=$seasonId&page=1&order=desc").parsed<EpJson>().data.episodes.map { ep ->
                newEpisode(ep.url) {
                    this.episode = ep.metaNumber.toIntOrNull()
                    this.name = ep.number
                    this.posterUrl = ep.thumbnail
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter
                this.score = Score.from10(rating)
                this.year = year.toIntOrNull()
                this.duration = duration.toIntOrNull()
                this.contentRating = contentRating
                this.plot = description
            }

        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url.replace("series","watch")) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter
                this.score = Score.from10(rating)
                this.year = year.toIntOrNull()
                this.duration = duration.toIntOrNull()
                this.contentRating = contentRating
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
        val document = app.get(data).documentLarge

        document.select("div.player-selection").forEach { container ->

            val type = when {
                container.hasClass("player-dub") -> "DUB"
                container.hasClass("player-sub") -> "SUB"
                else -> return@forEach
            }

            container.select("span[data-embed-id]").forEach { span ->
                val raw = span.attr("data-embed-id")
                val parts = raw.split(":", limit = 2)
                if (parts.size != 2) return@forEach
                val name = base64Decode(parts[0]).replace(type, ignoreCase = true, newValue = "")
                val url  = base64Decode(parts[1])
                Log.d("DoraBash","$name $type $url")
                loadCustomExtractor(
                    "$name $type",
                    url,
                    url,
                    subtitleCallback,
                    callback
                )
            }
        }

        return true
    }
    suspend fun loadCustomExtractor(
        name: String? = null,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        name ?: link.source,
                        name ?: link.name,
                        link.url,
                    ) {
                        this.quality = when {
                            else -> quality ?: link.quality
                        }
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }

}

