package com.likdev256

//import com.fasterxml.jackson.annotation.JsonProperty
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class OlaMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://olamovies.cyou"
    override var name = "OlaMovies"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/movies/bollywood/" to "Bollywood Movies",
        "$mainUrl/movies/hollywood/" to "Hollywood Movies",
        "$mainUrl/movies/south-indian/" to "South Indian Movies",
        "$mainUrl/movies/anime-movies/" to "Anime Movies",
        "$mainUrl/tv-series/" to "TV Series",
        "$mainUrl/tv-series/anime-tv-series-tv-series/" to "Anime TV Series",
        "$mainUrl/tv-series/english-tv-series/" to "English TV Series",
        "$mainUrl/tv-series/hindi-tv-series/" to "Hindi TV Series",
        "$mainUrl/tv-shows/" to "TV Shows",
        "$mainUrl/tv-shows/cartoon-tvs/" to "Cartoon TV Shows",
        "$mainUrl/tv-shows/documentary/" to "Documentary TV Shows",
        "$mainUrl/tv-shows/hindi-tv-shows/" to "Hindi TV Shows"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        //Log.d("Document", request.data)
        val home = document.select("div.layout-simple").mapNotNull {
            it.toSearchResult()
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val titleS = this.selectFirst("article > div.entry-overlay h2.entry-title > a")?.text().toString()
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleS)?.groups?.get(1)?.value.toString()
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("article > div.entry-overlay h2.entry-title > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.select("article > div.entry-image > a > img").attr("data-lazy-src").trim())
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN).post-152185 > div:nth-child(2) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > a:nth-child(1)
        val quality = getQualityFromString(if (titleS.contains("2160p")) "4k" else "hd")
        val type = ArrayList<String>()
        this.select("article div.entry-category a").forEach { type.add(it.ownText()) }
        //Log.d("mygodtype", type.toString())
        return if (type.contains("Movies")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("div.layout-simple").mapNotNull {
            val titleS = it.selectFirst("article > div.entry-overlay h2.entry-title > a")?.text().toString()
            val titleRegex = Regex("(^.*\\)\\d*)")
            val title = titleRegex.find(titleS)?.groups?.get(1)?.value.toString()
            //Log.d("title", title)
            val href = fixUrl(it.selectFirst("article > div.entry-overlay h2.entry-title > a")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.select("article > div.entry-image > a > img").attr("src").trim())
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN).post-152185 > div:nth-child(2) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > a:nth-child(1)
            val quality = getQualityFromString(if (titleS.contains("2160p")) "4k" else "hd")
            val type = ArrayList<String>()
            it.select("article div.entry-category a").forEach { type.add(it.ownText()) }
            //Log.d("mygodtype", type.toString())
            if (type.contains("Movies")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val titleS = doc.select("h1.entry-title").text().toString().trim()
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleS)?.groups?.get(1)?.value.toString()
        //Log.d("title", title)
        val href = fixUrl(
            mainUrl + doc.select("#UIMovieSummary > ul > li > div.block2 > a.title").attr("href")
                .toString()
        )
        //Log.d("href", href)
        val poster = fixUrlNull(doc.select("span.gridlove-cover > a").attr("href"))
        //Log.d("poster", poster.toString())
        fun String.containsAnyOfIgnoreCase(keywords: List<String>): Boolean {
            for (keyword in keywords) {
                if (this.contains(keyword, true)) return true
            }
            return false
        }
        val bloat = listOf("720p","1080p","2160p","Bluray","x264","x265","60FPS","120fps","WEB-DL")
        val tags = doc.select("div.entry-tags > a").map { if (it.text().containsAnyOfIgnoreCase(bloat)) "" else it.text() }.filter { !it.isNullOrBlank() }
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value?.toIntOrNull()
        val trailer = fixUrlNull(doc.select("div.perfmatters-lazy-youtube").attr("data-src"))
        //Log.d("year", year.toString())
        val type = ArrayList<String>()
        doc.select("article div.entry-category a").forEach { type.add(it.ownText()) }
        val recommendations = doc.select("div.col-lg-6").mapNotNull {
            it.toSearchResult()
        }

        val titRegex = Regex("\\d+")
        val episodes = ArrayList<Episode>()
        doc.select("div.w3-margin-bottom").forEach { me ->
            val seasonNum = me.select("button").text()
            me.select("div > div.wp-block-button > a").forEach {
                episodes.add(
                    Episode(
                        data = mainUrl + it.attr("href").toString(),
                        name = it.ownText().toString(),//.replaceFirst(epName.first().toString(), ""),
                        season = titRegex.find(seasonNum)?.value?.toInt(),
                        episode = titRegex.find(it.ownText().toString())?.value?.toInt()
                    )
                )
            }
        }

        return if (type.contains("Movies")) {
            newMovieLoadResponse(title, href, TvType.Movie, href) {
                this.posterUrl = poster
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, href, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
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
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:103.0) Gecko/20100101 Firefox/103.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Referer" to data,
            "Alt-Used" to "olamovies.ink",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "same-origin",
            "Sec-Fetch-User" to "?1"
        )

        val doc = app.get(
            data,
            headers
        )

        return true
    }
}
