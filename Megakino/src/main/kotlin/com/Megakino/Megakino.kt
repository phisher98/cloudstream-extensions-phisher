package com.Megakino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

class Megakino : MainAPI() {
    override var mainUrl              = "https://megakino.team"
    override var name                 = "Megakino"
    override val hasMainPage          = true
    override var lang                 = "de"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.TvSeries,TvType.Documentary)

    override val mainPage = mainPageOf(
        "" to "Trending",
        "kinofilme" to "Movies",
        "serials" to "Serials",
        "multfilm" to "Multfilm",
        "documentary" to "Documentary",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").documentLarge
        val home = document.select("#dle-content > a").mapNotNull {
            it.toSearchResult()
        }
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
        val title = this.select("h3").text()
        val href = fixUrl(this.attr("href"))
        val posterUrl = fixUrlNull(mainUrl+this.select("img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality= SearchQuality.HD
        }
    }

    private fun Element.toSearchResult1(): SearchResponse {
        val title = this.select("h3").text()
        val href = fixUrl(this.attr("href"))
        val posterUrl = fixUrlNull(mainUrl+this.select("img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality= SearchQuality.HD
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data= mapOf("do" to "search","subaction" to "search","story" to query.replace(" ","+"))
        val document=app.post(mainUrl, data = data).documentLarge
        val response = document.select("a.poster.grid-item").map {
                it.toSearchResult1()
            }
        return response
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title = document.selectFirst("div.page__subcols.d-flex h1")?.text() ?: "Unknown"
        val poster = fixUrl(mainUrl+document.select("div.pmovie__poster.img-fit-cover img").attr("data-src"))
        val year=document.select("div.pmovie__year > span:nth-child(2)").text().toIntOrNull()
        val hreflist=document.select("div.pmovie__player iframe").map { it.attr("src").ifEmpty { it.attr("data-src") } }.toJson()
        val description = document.selectFirst("div.page__cols.d-flex p")?.text()
        val trailer=document.select("link[itemprop=embedUrl]").attr("href")
        val genresText = document.selectFirst("div.pmovie__genres")?.text()
        val genresList = genresText?.split(" / ")?.map { it.trim() } ?: emptyList()
        val typetag= document.select("div.pmovie__genres").text()
        val type=if (typetag.contains("Filme")) TvType.Movie else TvType.TvSeries
        return if (type==TvType.TvSeries)
        {
            val episodes = mutableListOf<Episode>()
            document.select("select.flex-grow-1.mr-select option").map {
                val epnumber="Episode "+it.attr("data-season")
                val ephref=it.select("option").attr("value")
                episodes+=newEpisode(ephref)
                {
                    this.name=epnumber
                    this.season=1
                    this.episode=epnumber.toIntOrNull()
                    this.posterUrl=poster
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genresList
                this.year = year
                addTrailer(trailer)
            }
        }
        else newMovieLoadResponse(title, url, TvType.Movie, hreflist) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genresList
                this.year = year
                addTrailer(trailer)
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.startsWith("["))
            {
            data.removeSurrounding("[\"", "\"]") // Remove the brackets
                .split("\",\"").amap {
                    loadExtractor(it,subtitleCallback, callback)
                }
            }
        else
            loadExtractor(data,subtitleCallback, callback)
        return true
    }
}
