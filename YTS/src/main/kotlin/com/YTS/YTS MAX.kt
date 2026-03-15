package com.YTS

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class YTSMX : YTS(){
    override var mainUrl              = "https://yts.bz"
    override var name                 = "YTS MX"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.Torrent)
    override val mainPage = mainPageOf(
        "browse-movies" to "Latest",
        "browse-movies/0/all/all/0/featured/0/all" to "Featured Movies",
        "browse-movies/0/1080p.x265/all/0/latest/0/all" to "1080p Movies",
        "browse-movies/0/2160p/all/0/latest/0/all" to "4K Movies",
        "browse-movies/0/all/all/0/seeds/0/all" to "Best Seeds Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url:String
        if (page==1)
        {
            url="$mainUrl/${request.data}"
        }
        else
        {
            url="$mainUrl/${request.data}?page=$page"
        }
        val document = app.get(url, timeout = 10000L).document
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
        val rating = this.select("h4.rating").text().substringBefore("/".trim())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year = year
            this.score = Score.from10(rating)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("#mobile-movie-info h1")?.text()?.trim() ?:"No Title"
        val poster = document.select("#movie-poster img").attr("src")
        val year = document.selectFirst("#mobile-movie-info h2")?.text()?.trim()?.toIntOrNull()
        val tags = document.selectFirst("#mobile-movie-info > h2:nth-child(3)")?.text()?.trim()
            ?.split(" / ")
            ?.map { it.trim() }
        val description= document.selectFirst("#synopsis p")?.text()?.trim()
        val rating= document.select("#movie-info > div.bottom-info > div:nth-child(2) > span:nth-child(2)").text()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.score = Score.from10(rating)
            this.tags = tags
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("a.magnet-download.download-torrent.magnet").map {
            val magnet=it.attr("href")
            val quality =getIndexQuality(it.attr("title"))

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = magnet,
                    ExtractorLinkType.MAGNET
                ) {
                    this.referer = ""
                    this.quality = quality
                }
            )
        }
        return true
    }
}