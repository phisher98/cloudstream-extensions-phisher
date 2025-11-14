package com.BanglaPlex

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking

class Banglaplex : MainAPI() {
    override var mainUrl: String = runBlocking {
        BanglaPlexProvider.getDomains()?.banglaplex ?: "https://banglaplex.top"
    }
    override var name                 = "Banglaplex"
    override val hasMainPage          = true
    override var lang                 = "bn"
    override val supportedTypes       = setOf(TvType.Movie,TvType.TvSeries)

    override val mainPage = mainPageOf(
        "#hot" to "Trending",
        "genre/bollywood-movies" to "Bollywood",
        "genre/hollywood-movies" to "Hollywood",
        "genre/south-indian-movies" to "South Indian Movies",
        "genre/bollywood-series" to "Bollywood Series",
        "genre/dual-audio-movies" to "Dual Audio Movies",
        "genre/korean-web-series" to "Korean Web Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val res=app.get("$mainUrl/${request.data}.html")
        if (res.code==200) {
            if (page == 1) {
                val document = app.get("$mainUrl/${request.data}.html").documentLarge
                val home = document.select("div.movie-container > div.col-md-2")
                    .mapNotNull { it.toSearchResult() }

                return newHomePageResponse(
                    list = HomePageList(
                        name = request.name,
                        list = home,
                        isHorizontalImages = false
                    ),
                    hasNext = true
                )
            } else {
                val newpagenumber = page * 12
                val document = app.get("$mainUrl/${request.data}/$newpagenumber.html").documentLarge
                val home = document.select("div.movie-container > div.col-md-2")
                    .mapNotNull { it.toSearchResult() }

                return newHomePageResponse(
                    list = HomePageList(
                        name = request.name,
                        list = home,
                        isHorizontalImages = false
                    ),
                    hasNext = true
                )
            }
        }
        return null
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = fixTitle(this.select("div.movie-img > div.movie-title > h3 >a").text()).trim()
        val href      = fixUrl(this.select("div.movie-img > div.movie-title > h3 >a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div > div.latest-movie-img-container").attr("style")).toString().substringAfter("url('").substringBefore("')")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String,page: Int): SearchResponseList {
        val newpagenumber=page*12
        val document = app.get("${mainUrl}/search?q=$query&per_page=$newpagenumber").documentLarge
        val results = document.select("div.movie-container > div.col-md-2").mapNotNull { it.toSearchResult() }
        return results.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString().substringBefore(" | Watch Online")
        val poster      = document.select("#info > div > div > img").attr("src")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        document.select("div.video-embed-container > iframe").attr("src").let {
            loadExtractor(it,mainUrl,subtitleCallback, callback) }
        val downloadURLs=document.select("#download a ").attr("href")
         if (downloadURLs.isNotEmpty())
         {
            val tokenres= app.get(downloadURLs).documentLarge
            val csrftoken=tokenres.selectFirst("form input")?.attr("name")
            val csrftokenvakue=tokenres.selectFirst("form input")?.attr("name")
            app.post(downloadURLs, data = mapOf("$csrftoken" to "$csrftokenvakue")).documentLarge.select("div.row > div.col-sm-8 > a").amap {
                val href=it.attr("href")
                if (href.contains("xcloud",ignoreCase = true))
                {
                    Xcloud().getUrl(href,"",subtitleCallback,callback)
                }
                loadExtractor(href,subtitleCallback,callback)
            }
        }
        return true
    }
}