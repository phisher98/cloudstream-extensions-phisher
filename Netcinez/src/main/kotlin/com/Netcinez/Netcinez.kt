package com.Netcinez

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class Netcinez : MainAPI() {
    override var mainUrl = "https://netcinez.si"
    override var name = "Netcinez"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "category/ultimos-filmes" to "Últimas Atualizações Filmes",
        "category/acao" to "Ação Filmes",
        "category/animacao" to "Animação Filmes",
        "category/aventura" to "Aventura Filmes",
        "category/comedia" to "Comédia Filmes",
        "category/crime" to "Crime Filmes",
        "tvshows" to "Últimas Atualizações Séries",
        "tvshows/category/acao" to "Ação Séries",
        "tvshows/category/animacao" to "Animação Séries",
        "tvshows/category/aventura" to "Aventura Séries",
        "tvshows/category/comedia" to "Comédia Séries",
        "tvshows/category/crime" to "Crime Séries",
    )


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}"
        val document = app.get(url).document

        val home = document.select("#box_movies > div.movie").mapNotNull { it.toSearchResult() }
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
        val title = this.select("h2").text().trim()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl =
            this.select("img").attr("data-src").ifEmpty { this.select("img").attr("src") }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = app.get("${mainUrl}/?s=$query").document
            val results = document.select("#box_movies > div.movie").mapNotNull { it.toSearchResult() }
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
        val title = document.selectFirst("div.dataplus h1")?.text() ?: document.select("div.dataplus span.original").text()
        val poster = fixUrl(document.select("div.headingder > div.cover").attr("data-bg"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val type = if (url.contains("tvshows")) TvType.TvSeries else TvType.Movie
        val imdbid = document.selectFirst("div.imdbdatos a")?.attr("href")?.substringAfterLast("/")
        val actors=document.select("#dato-1 > div:nth-child(4)").map { it.select("a").text() }
        val recommendations=document.select("div.links a").amap {
            val recName = it.select("div.data-r > h4").text()
            val recHref = it.attr("href")
            val recPosterUrl = it.select("img").attr("src")
            newTvSeriesSearchResponse(recName,recHref, TvType.Movie) {
                this.posterUrl = recPosterUrl
            }
        }
        val year = document.select("#dato-1 > div:nth-child(5)").text().toIntOrNull()
            if (type == TvType.TvSeries) {
                val episodes = mutableListOf<Episode>()
                document.select("div.post #cssmenu > ul li > ul > li").map {
                    val seasonno = it.select("a > span.datex").text().substringBefore("-").trim()
                        .toIntOrNull()
                    val episodeno= it.select("a > span.datex").text().substringAfterLast("-").trim()
                        .toIntOrNull()
                    val epname=it.select("a > span.datix").text()
                    val ephref = it.selectFirst("a")?.attr("href")
                    episodes += newEpisode(ephref)
                    {
                        this.name = epname
                        this.season = seasonno
                        this.episode = episodeno
                    }
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.year=year
                    this.recommendations=recommendations
                    addActors(actors)
                    addImdbId(imdbid)
                }
            }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year=year
            this.recommendations=recommendations
            addActors(actors)
            addImdbId(imdbid)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframeUrl = doc.selectFirst("#player-container iframe")?.absUrl("src")
        if (iframeUrl.isNullOrEmpty()) {
            Log.d("Error:", "Iframe not found")
            return false
        }

        val iframeDoc = app.get(iframeUrl).document
        val buttons = iframeDoc.select("div.btn-container a")
        if (buttons.isEmpty()) {
            Log.d("Error:", "No buttons found in iframe")
            return false
        }

        for (button in buttons) {
            val intermediateUrl = button.absUrl("href")

            val label = button.text().trim()
            try {
                val finalDoc = app.get(intermediateUrl).document
                val finalElement = finalDoc.selectFirst("div.container a, source")
                val finalUrl = when (finalElement?.tagName()) {
                    "a" -> finalElement.absUrl("href")
                    "source" -> finalElement.absUrl("src")
                    else -> null
                }

                if (finalUrl != null) {
                    if (finalUrl.isNotEmpty()) {
                        callback.invoke(
                            newExtractorLink(
                                "$name $label",
                                "$name $label",
                                finalUrl,
                                INFER_TYPE
                            )
                            {
                                this.referer=mainUrl
                            }
                        )
                    } else {
                        Log.d("Error:", "No final link found at $intermediateUrl")
                    }
                }
            } catch (e: Exception) {
                Log.e("Error:", "Error processing link: $intermediateUrl $e")
            }
        }
        return true
    }
}
