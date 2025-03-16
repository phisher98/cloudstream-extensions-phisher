package com.Tooniboy

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.JsUnpacker.Companion.load

class Tooniboy : MainAPI() {
    override var mainUrl              = "https://www.tooniboy.com"
    override var name                 = "Tooniboy"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.Cartoon)

    override val mainPage = mainPageOf(
        "series" to "Series",
        "movies" to "Movies",
        "category/crunchyroll" to "Crunchyroll",
        "category/netflix" to "Netflix",
        "category/cartoon-network" to "Cartoon Network",
        "category/disney" to "Disney",
        "category/hungama" to "Hungama",
        "category/sony-yay" to "Sony Yay",
    )


    companion object
    {
        val header= mapOf("Cookie" to com.phisher98.BuildConfig.TooniboyCookie)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/", headers = header).document
        val home     = document.select("main article").mapNotNull { it.toSearchResult() }

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
        val title     = this.select("h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.selectFirst("figure img")?.attr("src")?.let { httpsify(it) }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearch(): SearchResponse {
        val title     = this.select("header > h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("figure > img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query",headers = header).document

            val results = document.select("main article").mapNotNull { it.toSearch() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    @Suppress("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = header).document
        val title= document.selectFirst("article h1")?.text()?.trim().toString().replace("Watch Online","")
        val poster = document.select("div.Container figure.Objf img").attr("src").let { httpsify(it) }
        val description = document.selectFirst(".Description > p:nth-child(1)")?.text()?.trim()
        val tags = document.select("p.Genre a").map { it.text() }
        val tvtag=if (url.contains("series")) TvType.TvSeries else TvType.Movie
        return if (tvtag == TvType.TvSeries) {
            val seriesposter = document.select("header.Container figure img").attr("src").let { httpsify(it) }
            val episodes = mutableListOf<Episode>()

            val seasonPages = document.select("section.SeasonBx a")
                .mapNotNull { it.attr("href") }

            seasonPages.forEach { seasonUrl ->
                val seasonDocument = app.get(seasonUrl).document

                seasonDocument.select("section.SeasonBx").forEach { seasonSection ->
                    seasonSection.select("tr").forEach { tdElement ->
                        val href = tdElement.selectFirst("a.MvTbImg")?.attr("href") ?: ""
                        val posterUrl = tdElement.selectFirst("a.MvTbImg img")?.attr("src")?.let { httpsify(it) }
                        val episodeText = tdElement.selectFirst("span.Num")?.text()?.trim()
                        val episodeNum = episodeText?.toIntOrNull()
                        val episodeName=tdElement.selectFirst("td.MvTbTtl a")?.text()
                        val seasonNumber = seasonSection.selectFirst("div.Title span")
                            ?.text()
                            ?.substringBefore("-")
                            ?.trim()
                            ?.toIntOrNull()

                        episodes.add(newEpisode(href) {
                            this.name=episodeName
                            this.episode = episodeNum
                            this.posterUrl = posterUrl
                            this.season = seasonNumber
                        })
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.tags = tags
                this.posterUrl = seriesposter
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.tags=tags
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
       val document = app.get(data, headers=header).document
       document.select("ul.ListOptions li").mapNotNull { li ->
            val dataKey = li.attr("data-key")
            val dataId = li.attr("data-id")
            val href=app.get("$mainUrl/?trembed=$dataKey&trid=$dataId&trtype=2").document.selectFirst("iframe")?.attr("src")
            if (href != null) {
                Log.d("Phisher",href)
               loadExtractor(href,subtitleCallback, callback)
            }
       }
        return true
    }
}


