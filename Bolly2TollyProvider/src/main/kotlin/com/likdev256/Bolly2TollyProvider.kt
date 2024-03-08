package com.likdev256

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Bolly2TollyProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.bolly2tolly.desi"
    override var name = "Bolly2Tolly"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Latest ",
        "$mainUrl/category/english-movies/page/" to "English",
        "$mainUrl/category/hindi-movies/page/" to "Hindi",
        "$mainUrl/category/telugu-movies/page/" to "Telugu",
        "$mainUrl/category/tamil-movies/page/" to "Tamil",
        "$mainUrl/category/kannada-movies/page/" to "Kannada",
        "$mainUrl/category/malayalam-movies/page/" to "Malayalam",
        "$mainUrl/category/bengali-movies/page/" to "Bengali"


    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("ul.MovieList article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = if (this.selectFirst("img")?.attr("alt").isNullOrEmpty())
            this.selectFirst("h3")?.text()?.substringBefore("(") else this.selectFirst("img")
            ?.attr("alt")?.trim()
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title ?: return null, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select(".result-item").mapNotNull {
            val title = it.select("SubTitle").text().trim()
            val href = fixUrl(it.selectFirst(".title a")?.attr("href").toString())
            val posterUrl = fixUrlNull(it.selectFirst(".thumbnail img")?.attr("src"))
            val quality = getQualityFromString(it.select("span.quality").text())
            val tvtype = if (href.contains("tvshows")) TvType.TvSeries else TvType.Movie
            newMovieSearchResponse(title, href, tvtype) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst(".SubTitle")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst(".Image img")?.attr("src"))
        val tags = document.select(".InfoList li:eq(2) a").map { it.text() }
        val year = document.select("span.Date").text().trim().toIntOrNull()
        val tvType =
            if (document.select(".AA-cont").isNullOrEmpty()) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst(".Description p")?.text()?.trim()
        //val rating = document.select(".post-ratings strong").last()!!.text().toRatingInt()
        val actors = document.select(".ListCast a").map { it.text().trim() }
        val recommendations = document.select(".Wdgt ul.MovieList li").mapNotNull {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("tbody tr").mapNotNull {
                val href = fixUrl(it.select(".MvTbTtl a").attr("href") ?: return null)
                Log.d("href", href)
                val name = it.select(".MvTbTtl a").text().trim()
                val thumbs = "https:" + it.select("img").attr("src")
                val season = document.select(".AA-Season").attr("data-tab").toInt()
                val episode = it.select("span.Num").text().toInt()
                Episode(
                    href,
                    name,
                    season,
                    episode,
                    thumbs
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                //this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                //this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        println(data)
        val sources = mutableListOf<String>()
        val document = app.get(data).document
        sources.add(document.select(".TPlayer iframe").attr("src"))
        val srcRegex = Regex("""(https.*?)"\s""")
        srcRegex.find(
            document.select(".TPlayer").text()
        )?.groupValues?.map { sources.add(it.replace("#038;", "")) }
        println(sources)
        sources.forEach {
            val source = app.get(it, referer = data).document.select("iframe").attr("src")
            println(source)
            loadExtractor(
                source,
                subtitleCallback,
                callback
            )
        }
        return true
    }
}
