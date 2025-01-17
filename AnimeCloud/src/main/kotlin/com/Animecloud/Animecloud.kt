package com.Animecloud

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl

class Animecloud : MainAPI() {
    override var mainUrl              = "https://fireani.me"
    override var name                 = "Animecloud"
    override val hasMainPage          = true
    override var lang                 = "de"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "hot/1" to "Trending",
        "genre/Action" to "Action",
        "genre/Drama" to "Drama",
        "genre/Komödie" to "Comedy",
        "genre/Mystery" to "Mystery",
        "genre/Romanze" to "Romanze",
        "genre/Abenteuer" to "Abenteuer",
        "genre/EngSub" to "EngSub",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val home     = document.select("#__nuxt div.grid a").mapNotNull { it.toSearchResult() }

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
        val title     = this.select("span strong").text()
        val href      = this.attr("href")
        val posterUrl = fixUrlNull(this.select("img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val searchResponse = app.get("$mainUrl/api/anime/search?q=$query").parsedSafe<SearchParser>()?.data?.map { it.toSearchResponse() }
        return searchResponse
    }

    private fun Daum.toSearchResponse(): SearchResponse {
        val title=this.title
        val poster= "$mainUrl/img/posters/${this.poster}"
        val href="${mainUrl}/anime/${this.slug}"
        return newAnimeSearchResponse(
            title,
            href,
            TvType.TvSeries,
        ) {
            this.posterUrl=poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("#__nuxt h1")?.ownText()?.trim().toString()
        val imdburl=document.selectFirst("div.flex.flex-col.gap-4.w-full > div:nth-child(6) > a.btn.btn-warning.btn-sm")?.attr("href")
        val poster = document.select("#__nuxt img.fixed").attr("data-src")
        val description = document.selectFirst("div.flex.flex-col.gap-4.w-full > p > span")?.text()?.trim()
        val genres=document.select("div.flex.flex-wrap.gap-2 a.badge").map { it.text() }
        val episodes=document.select("div.flex.flex-col.gap-4.w-full > div.grid.grid-cols-1.gap-4 button").map { info->
             val season = info.select("span").text().substringAfter("S").substringBeforeLast("E").trim().toIntOrNull()
             val episode = info.select("span").text().substringAfter("E").trim().toIntOrNull()
             val epname="Episode $episode "
             val epposter=info.selectFirst("img")?.attr("data-src") ?:""
             val animename=url.substringAfterLast("/")
             val href = "$mainUrl/api/anime/episode?slug=$animename&season=$season&episode=$episode"
             Episode(href, epname,season,episode,epposter)
        }
        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                addImdbUrl(imdburl)
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        app.get(data).parsedSafe<AnimecloudEP>()?.data?.animeEpisodeLinks?.map {
            val dubtype=it.lang
            val href=it.link
            loadSourceNameExtractor("$name ${dubtype.uppercase()}",href, "", subtitleCallback, callback)
        }
        return true
    }
}
