package com.Wapking

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Wapking : MainAPI() {
    override var mainUrl              = "https://wapking.name"
    override var name                 = "Wapking"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Music,TvType.Others)

    override val mainPage = mainPageOf(
        "featured" to "Trending",
        "category/7/indian-pop-songs" to "Indian Pop Songs",
        "category/110/a-to-z-bhakti-mp3-songs" to "Bhakti"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/$page.html").document
        val home     = document.select("div.fl").mapNotNull { it.toSearchResult() }

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
        val title     = this.select("a").attr("title")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src").toString())
        return newMovieSearchResponse(title, href, TvType.Music) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("$mainUrl/files-search/$query/new2old/$i.html").document
            val results = document.select("div.fl").mapNotNull { it.toSearchResult() }
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
        val document = app.get(url, referer = url).document
        val title       = document.selectFirst("meta[property=og:title]")?.attr("content").toString().substringBefore("Download")
        val poster = document.selectFirst("div.showimage img")?.attr("src")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content").toString().trim()

        return newMovieLoadResponse(title, url, TvType.Music, url) {
                this.posterUrl = poster
                this.plot = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            Log.d("Phisher Test",data)
        app.get(data).document.select("div.tCenter a").forEach {
                val links=it.attr("href")
            if (links.contains("download")) {
                callback.invoke(
                    ExtractorLink(
                        name = name,
                        source = name,
                        referer = "",
                        url = links,
                        quality = getQualityFromName(""),
                        type = INFER_TYPE
                    )
                )
            }
            }
        return true
    }
}
