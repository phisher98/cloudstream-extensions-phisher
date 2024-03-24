package com.likdev256


import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element


class BMovies : MainAPI() {
    override var mainUrl = "https://bmovies.vip"
    override var name = "BMovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "country/india" to "Bollywood",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/$page").document
        Log.d("Mandik","$document")
        val home =document.select("div.movies-list.movies-list-full > div.ml-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.ml-item > div > a > img").attr("title")
        val href = this.select("div.ml-item > div > a").attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("div.ml-item > div > a > img")?.attr("data-original"))
        Log.d("url","$posterUrl")
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/search-movies/$query/page-$i.html").document

            val results =
                document.select("div.shortItem.listItem").mapNotNull { it.toSearchResult() }

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
        val title =document.selectFirst("div.imdbwp > div.imdbwp__content > div.imdbwp__header > span")!!.text()
        val poster =
            fixUrlNull(
                document.selectFirst("div.imdbwp > div > a > img")
                    ?.attr("src")
                    ?.trim()
            )
        val description = document.selectFirst("div.imdbwp > div.imdbwp__content > div.imdbwp__teaser")!!.text().trim()
        val tvType =
            if (document.select("#details.section-box > a").isNullOrEmpty()) TvType.Movie
            else TvType.TvSeries
        // Log.d("TVtype","$tvType")
        return if (tvType == TvType.TvSeries) {
            val episodes =
                document.select("#cont_player > #details > a").mapNotNull {
                    val href = it.selectFirst("a.episode.episode_series_link")!!.attr("href")
                    Log.d("href episodes", href)
                    // val description = document.selectFirst("div.film-detail >
                    // div.textSpoiler").text().trim()
                    val episode = it.select("#details > a").text().toString()
                    val fullepisode="Episode"+ episode
                    Episode(href, fullepisode)
                }
            Log.d("Phisher Epe", "$episodes")

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
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

        return true
    }
}
