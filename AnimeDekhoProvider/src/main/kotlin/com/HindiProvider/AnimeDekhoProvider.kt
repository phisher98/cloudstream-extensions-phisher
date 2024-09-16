package com.HindiProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

open class AnimeDekhoProvider : MainAPI() {
    override var mainUrl = "https://animedekho.net"
    override var name = "Anime Dekho"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true

    override val supportedTypes =
        setOf(
            TvType.Cartoon,
            TvType.Anime,
            TvType.AnimeMovie,
            TvType.Movie,
        )

    override val mainPage =
        mainPageOf(
            "/series/" to "Series",
            "/movie/" to "Movies",
            "/category/anime/" to "Anime",
            "/category/cartoon/" to "Cartoon",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val link = "$mainUrl${request.data}"
        val document = app.get(link).document
        val home =
            document.select("article").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = this.selectFirst("a.lnk-blk")?.attr("href") ?: return null
        val title = this.selectFirst("header h2")?.text() ?: "null"
        val posterUrl = this.selectFirst("div figure img")?.attr("src")

        return newAnimeSearchResponse(title, Media(href, posterUrl).toJson(), TvType.Anime, false) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = true, subExist = true)
        }
    }

    override suspend fun search(query: String): List<AnimeSearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("ul[data-results] li article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val media = parseJson<Media>(url)
        val document = app.get(media.url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim()?.substringAfter("Watch Online ")
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringAfter("Watch Online ")?.substringBefore(" Movie in Hindi Dubbed Free") ?: "No Title"
        val poster = fixUrlNull(document.selectFirst("div.post-thumbnail figure img")?.attr("src") ?: media.poster)
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[name=twitter:description]")?.attr("content")
        val year = (document.selectFirst("span.year")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:updated_time]")?.attr("content")
                ?.substringBefore("-"))?.toIntOrNull()
        val lst = document.select("ul.seasons-lst li")

        return if (lst.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, Media(
                media.url,
                mediaType = 1
            ).toJson()) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            @Suppress("NAME_SHADOWING") val episodes = document.select("ul.seasons-lst li").mapNotNull {
                val name = it.selectFirst("h3.title")?.ownText() ?: "null"
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val poster=it.selectFirst("div > div > figure > img")?.attr("src")
                val seasonnumber = it.selectFirst("h3.title > span")?.text().toString().substringAfter("S").substringBefore("-")
                val season=seasonnumber.toIntOrNull()
                Episode(Media(href, mediaType = 2).toJson(), name, posterUrl = poster,season = season)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val media = parseJson<Media>(data)
        val body = app.get(media.url).document.selectFirst("body")?.attr("class") ?: return false
        val term = Regex("""(?:term|postid)-(\d+)""").find(body)?.groupValues?.get(1) ?: throw ErrorLoadingException("no id found")
        for (i in 0..4) {
            val link = app.get("$mainUrl/?trdekho=$i&trid=$term&trtype=${media.mediaType}")
                .document.selectFirst("iframe")?.attr("src")
                ?: throw ErrorLoadingException("no iframe found")
            Log.d("Phisher",link)
            loadExtractor(link,subtitleCallback, callback)
        }
        return true
    }

    data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)

}
