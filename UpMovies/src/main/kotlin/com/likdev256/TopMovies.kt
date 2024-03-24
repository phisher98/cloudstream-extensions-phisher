package com.likdev256


import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI


class TopMovies : MainAPI() {
    override var mainUrl = "https://topmovies.asia"
    override var name = "Topmovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage =
        mainPageOf(
            "" to "Latest",
            "web-series/tv-shows-by-network/netflix" to "Netflix",

            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        // Log.d("Mandik","$document")
        val home =
            document.select(
                "div.post-cards > article"
            )
                .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("title").toString()
        val href = this.select("a").attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("div.post-cards > article > a > div > img")?.attr("src"))
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
        val driveurls = mutableListOf<String>()
        val Gdrivelinks = mutableListOf<String>()
        val document = app.get(data).document
        document.select("a.maxbutton-1").forEach {
            val drive=it.attr("href")
            driveurls.add(drive)
            //Log.d("Test23","$drive")
        }
        driveurls.forEach {
            val doc= app.get(it).document
            val gdrive=doc.select("a.maxbutton-5").attr("href")
            Gdrivelinks.add(gdrive)
            Log.d("Test23","$gdrive")
        }
        Gdrivelinks.forEach {
            fun Document.getFormUrl(): String {
                return this.select("form#landing").attr("action")
            }

            fun Document.getFormData(): Map<String, String> {
                return this.select("form#landing input").associate { it.attr("name") to it.attr("value") }
            }

            fun getBaseUrl(url: String): String {
                return URI(url).let {
                    "${it.scheme}://${it.host}"
                }
            }

            val host = getBaseUrl(it)
            var res = app.get(it).document
            var formUrl = res.getFormUrl()
            var formData = res.getFormData()

            res = app.post(formUrl, data = formData).document
            formUrl = res.getFormUrl()
            formData = res.getFormData()

            res = app.post(formUrl, data = formData).document
            //Log.d("Test445","$res")
            val script=res.selectFirst("script:containsData(verify_button)").data().toString().trim().replace(" ","")
            Log.d("Test1234","$script")
            val regex = Regex("""s_343\('([^']+)'.+?,\s*'([^']+)'.+?\);""")
            val matchResult = regex.find("$script")
            if (matchResult != null) {
                val (cookieName, cookieValue) = matchResult.destructured
                println("$cookieName=$cookieValue")
            } else {
                println("Cookie not found in the script.")
            }
        }
        return true
    }
}
