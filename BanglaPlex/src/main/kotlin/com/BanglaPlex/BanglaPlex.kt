package com.BanglaPlex

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Banglaplex : MainAPI() {
    override var mainUrl              = "https://banglaplex.top"
    override var name                 = "Banglaplex"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val supportedTypes       = setOf(TvType.Movie)

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
                val document = app.get("$mainUrl/${request.data}.html").document
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
                val document = app.get("$mainUrl/${request.data}/$newpagenumber.html").document
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

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 0..4) {
            val newpagenumber=i*12
            val document = app.get("${mainUrl}/search?q=$query&per_page=$newpagenumber").document
            Log.d("Test",document.toString())
            val results = document.select("div.movie-container > div.col-md-2").mapNotNull { it.toSearchResult() }

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
        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString().substringBefore(" | Watch Online")
        val poster      = document.select("#info > div > div > img").attr("src")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("div.video-embed-container > iframe").attr("src").let {
            loadExtractor(it,mainUrl,subtitleCallback, callback) }
        val DownloadURLs=document.select("#download a ").attr("href")
         if (DownloadURLs.isNotEmpty())
         {
            val tokenres= app.get(DownloadURLs).document
            val csrf_token=tokenres.selectFirst("form input")?.attr("name")
            val csrf_token_vakue=tokenres.selectFirst("form input")?.attr("name")
            app.post(DownloadURLs, data = mapOf("$csrf_token" to "$csrf_token_vakue")).document.select("div.row > div.col-sm-8 > a").amap {
                val href=it.attr("href")
                if (href.contains("gdflix"))
                {
                    GDFlix().getUrl(href)
                }
                else
                loadExtractor(href,subtitleCallback,callback)
            }
        }
        return true
    }
}