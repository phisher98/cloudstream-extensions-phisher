package com.Streamblasters

//import android.util.Log
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

class Streamblasters : MainAPI() {
    override var mainUrl              = "https://www.streamblasters.pm"
    override var name                 = "Streamblasters"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "" to "Latest",
        "category/hindi" to "Hindi",
        "category/english" to "English",
        "category/tamil" to "Tamil",
        "category/telugu" to "Telugu",
        "category/malayalam" to "Malayalam",
        "category/kannada" to "Kannada",
        "category/web-series" to "Web Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home     = document.select("div.blog-items > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.blog-items > article > div > div > div > a").attr("title").trim().replace("Watch Online","")
        val href      = fixUrl(this.select("div.blog-items > article > div > div > div > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.blog-items > article > div > div > div > a img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.blog-items > article").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title       = document.selectFirst("header.entry-header > h1")?.text()?.trim().toString().replace("Watch Online","")
        var poster = document.select("header.entry-header > header").attr("style").substringAfter("background-image:url(").substringBefore(");").trim()
        if (poster.isEmpty())
        {
            poster="https://img.freepik.com/free-photo/assortment-cinema-elements-red-background-with-copy-space_23-2148457848.jpg?size=626&ext=jpg&ga=GA1.1.2082370165.1716422400&semt=ais_user"
        }
        val description = document.selectFirst("div.actor-element > p")?.text()?.trim()
        val trailer = document.selectFirst("div.tmdb-trailer > iframe")?.attr("src")
        val actors = document.select("div.ac-di-content").map {
            Actor(
                it.select("div.ac-di-content > div.post-content > h6").text(),
                it.select("div.ac-di-content > div.post-img > span > img").attr("src")
            )
        }
        val tvType=if (document.select("div.series-listing").isEmpty()) TvType.Movie else TvType.TvSeries
        return if (tvType == TvType.TvSeries) {
            val episodes =
                document.select("div.series-listing > a").mapNotNull {
                    val href = it.attr("href")
                    val episode = it.select("span").text().toString()
                    Episode(href, episode)
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        }
        else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster.ifEmpty {
                    ({
                        posterUrl =
                            "https://www.streamblasters.link/wp-content/uploads/2022/05/cropped-png12.png"
                    }).toString()
                }
                this.plot = description
                addTrailer(trailer)
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val server=document.selectFirst("#player-api-control > iframe")?.attr("src") ?:""
        Log.d("Phisher test",server)
        loadExtractor(server,subtitleCallback, callback)
        return true
    }
}
