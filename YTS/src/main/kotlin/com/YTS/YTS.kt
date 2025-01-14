package com.YTS

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class YTS : MainAPI() {
    override var mainUrl              = "https://en.yts-official.mx"
    override var name                 = "YTS"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Torrent)

    override val mainPage = mainPageOf(
        "browse-movies?keyword=&quality=all&genre=all&rating=0&year=0&order_by=latest" to "Latest",
        "browse-movies?keyword=&quality=all&genre=all&rating=0&year=0&order_by=featured" to "Featured",
        "browse-movies?keyword=&quality=2160p&genre=all&rating=0&year=0&order_by=latest" to "4K Movies",
        "browse-movies?keyword=&quality=1080p&genre=all&rating=0&year=0&order_by=latest" to "1080p Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").document
        val home     = document.select("div.row div.browse-movie-wrap").mapNotNull { it.toSearchResult() }

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
        val title     = this.select("div.browse-movie-bottom a").text().trim()
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        val year=this.selectFirst("a div.browse-movie-year")?.text()?.toIntOrNull()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/browse-movies?keyword=$query&quality=all&genre=all&rating=0&order_by=latest&year=0&page=$i").document
            val results = document.select("div.row div.browse-movie-wrap").mapNotNull { it.toSearchResult() }
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
        val title = document.selectFirst("#mobile-movie-info h1")?.text()?.trim() ?:"No Title"
        val poster = getURL(document.select("#movie-poster img").attr("src"))
        val year = document.selectFirst("#mobile-movie-info h2")?.text()?.trim()?.toIntOrNull()
        val tags = document.selectFirst("#mobile-movie-info > h2:nth-child(3)")?.text()?.trim()
            ?.split(" / ")
            ?.map { it.trim() }
        val rating= document.select("#movie-info > div.bottom-info > div:nth-child(2) > span:nth-child(2)").text().toRatingInt()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = title
                this.year = year
                this.rating=rating
                this.tags = tags
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("p.hidden-md.hidden-lg a").amap {
            val href=getURL(it.attr("href").replace(" ","%20"))
            val quality =it.ownText().substringBefore(".").replace("p","").toInt()
            callback.invoke(
                ExtractorLink(
                    "$name $quality",
                    name,
                    fixUrl( href),
                    "",
                    quality,
                    INFER_TYPE
                )
            )
        }
        return true
    }

    fun getURL(url: String): String {
            return "${mainUrl}$url"
    }
}
