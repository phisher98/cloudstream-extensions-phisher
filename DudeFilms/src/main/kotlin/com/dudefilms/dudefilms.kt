package com.dudefilms

import com.google.gson.Gson
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


class Dudefilms : MainAPI() {
    override var mainUrl = "https://dudefilms.archi"
    override var name = "Dudefilms"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries
    )
    companion object
    {
        private val cinemeta_url = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta"
    }

    override val mainPage = mainPageOf(
        "" to "HomePage",
        "category/bollywood" to "Bollywood",
        "category/hollywood" to "Hollywood",
        "category/gujarati" to "Gujarati",
        "category/southindian" to "South Indian",
        "category/webseries" to "Web Series",
        "category/adult/" to "Adult",
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = if (page==1) app.get("$mainUrl/${request.data}").document else app.get("$mainUrl/${request.data}page/$page").document

        val home = doc.select("div.simple-grid-grid-post").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home, true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = cleanTitle(this.selectFirst("h3")?.text())
        val href = fixUrl(this.select("h3 a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").let {
            img -> img.attr("data-src").takeIf { it.isNotBlank() }
            ?: img.attr("src") })

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getSearchQuality(title)
        }
    }


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val doc = app.get("$mainUrl/page/$page/?s=$query").document
        val res = doc.select("div.simple-grid-grid-post").mapNotNull { it.toSearchResult() }
        return res.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.select("#movie_title > a").text()
        val poster = doc.select("meta[property=og:image]").attr("content")
        val plot = doc.selectFirst(".kno-rdesc .kno-rdesc")?.text()
        val descriptions = doc.selectFirst("#summary")?.ownText()
        val typeraw = doc.select("h1.post-title a").text()
        var year = doc.select("#movie_title > a > small").text().toIntOrNull()

        val tvtype =
            if (typeraw.contains("movie", ignoreCase = true)) TvType.Movie
            else TvType.TvSeries

        var genre: List<String>? = null
        var background: String? = null
        var description: String? = null
        var cast: List<String> = emptyList()

        val hrefs = doc.select("a.maxbutton")
            .amap { element ->
                app.get(element.absUrl("href"))
                    .document
                    .select("a.maxbutton")
                    .mapNotNull { it.absUrl("href").takeIf(String::isNotBlank) }
            }
            .flatten()
            .toJson()

        val imdbId = doc
            .select("div span a[href*='imdb.com']")
            .attr("href")
            .substringAfterLast("/")

        val typeset = if (tvtype == TvType.TvSeries) "series" else "movie"

        val responseData = if (imdbId.isNotEmpty()) {
            val jsonResponse = app.get("$cinemeta_url/$typeset/$imdbId.json").text
            if (jsonResponse.startsWith("{")) {
                Gson().fromJson(jsonResponse, ResponseData::class.java)
            } else null
        } else null

        if (responseData != null) {
            description = responseData.meta?.description ?: descriptions
            cast = responseData.meta?.appExtras?.cast?.mapNotNull { it.name } ?: emptyList()
            background = responseData.meta?.background ?: poster
            genre = responseData.meta?.genres
            year = responseData.meta?.year?.substringBefore("-")?.toIntOrNull()
        }

        if (tvtype == TvType.TvSeries) {

            val episodeUrlMap = mutableMapOf<Pair<Int, Int>, MutableList<String>>()

            doc.select("h4").forEach h4Loop@{ h4 ->

                val seasonNumber = Regex("""\bSeason\s*(\d+)\b""", RegexOption.IGNORE_CASE)
                    .find(h4.text())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: return@h4Loop

                var sibling = h4.nextElementSibling()

                while (sibling != null && sibling.tagName() == "p") {

                    sibling.select("a.maxbutton")
                        .filterNot(::isBlockedButton)
                        .forEach seasonBtnLoop@{ seasonButton ->

                            val seasonPageUrl = seasonButton.absUrl("href")
                            if (seasonPageUrl.isBlank()) return@seasonBtnLoop

                            val seasonPageDoc = app.get(seasonPageUrl).document

                            seasonPageDoc.select("a.maxbutton-ep").forEach epLoop@{ epButton ->

                                val epUrl = epButton.absUrl("href")
                                if (epUrl.isBlank()) return@epLoop

                                val episodeNumber = Regex(
                                    """(?:Episode|Ep|E)\s*(\d+)""",
                                    RegexOption.IGNORE_CASE
                                )
                                    .find(epButton.text())
                                    ?.groupValues
                                    ?.get(1)
                                    ?.toIntOrNull()
                                    ?: return@epLoop

                                val key = seasonNumber to episodeNumber
                                episodeUrlMap.getOrPut(key) { mutableListOf() }.add(epUrl)
                            }
                        }

                    sibling = sibling.nextElementSibling()
                }
            }

            val episodes = episodeUrlMap.map { (key, urls) ->
                val (seasonNumber, episodeNumber) = key

                val metaEpisode = responseData?.meta?.videos
                    ?.firstOrNull {
                        it.season == seasonNumber && it.episode == episodeNumber
                    }

                newEpisode(urls.toJson()) {
                    this.name = metaEpisode?.title
                    this.season = seasonNumber
                    this.episode = episodeNumber
                    this.posterUrl = metaEpisode?.thumbnail
                    this.description = metaEpisode?.overview
                    addDate(metaEpisode?.released)
                }
            }

            return newTvSeriesLoadResponse(
                responseData?.meta?.name ?: title,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.backgroundPosterUrl = background ?: poster
                this.posterUrl = poster
                this.year = responseData?.meta?.year?.toIntOrNull() ?: year
                this.plot = description ?: plot
                this.tags = genre
                addActors(cast)
                this.score = Score.from10(responseData?.meta?.imdbRating)
                addImdbId(imdbId)
            }
        }

        return newMovieLoadResponse(
            responseData?.meta?.name ?: title,
            url,
            TvType.Movie,
            hrefs
        ) {
            this.backgroundPosterUrl = background ?: poster
            this.posterUrl = poster
            this.year = responseData?.meta?.year?.toIntOrNull() ?: year
            this.plot = description ?: plot
            this.tags = genre
            addActors(cast)
            this.score = Score.from10(responseData?.meta?.imdbRating)
            addImdbId(imdbId)
        }
    }




    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links: List<String> = tryParseJson<List<String>>(data) ?: emptyList()
        links.amap {
            loadExtractor(it,"",subtitleCallback,callback)
        }
        return true
    }
}