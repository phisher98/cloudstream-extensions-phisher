package com.lindilink4u

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Hindilink4u : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.hindilinks4u.today"
    override var name = "Hindilink4u"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
    )
    
 private fun toResult(post: Element,search:Int): SearchResponse {
    val url = post.select("a").attr("href")
    val title = post.select("a").attr("title")
    val imageUrl =if(search==0) post.select("img").attr("data-src") else post.select("img").attr("src")
    // Log.d("post", post.toString())
    // val quality = post.select(".video-label").text()
    return newMovieSearchResponse(title, url, TvType.Movie) {
        this.posterUrl = imageUrl
    }
 }
    override val mainPage = mainPageOf(
        "" to "Latest",
        "category/series" to "Series",
        "category/documentaries" to " Documentary",
        "category/romance" to "Adult"
        )
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url =if(page==1) "$mainUrl/${request.data}/" else  "$mainUrl/${request.data}/page/$page/" 
        val document = app.get(url).document
        val home = document.select("div.thumb").mapNotNull {
            toResult(it,0)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("div.thumb").mapNotNull {
            toResult(it,1)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text().toString()
        val plot = document.select("span[data-testid^=plot-xl]").text()
        val poster = fixUrlNull(document.select("meta[property^=og:image]").attr("content"))
        val tvType = if (document.select("tr.episode").isNotEmpty()) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }
        return if (tvType==TvType.TvSeries)
        {
            val episode=document.select("tr.episode").map {
                    val season=it.select("a").text().substringBefore("x").trim().toIntOrNull()
                    val episode=it.select("a").text().substringAfter("x").trim().toIntOrNull()
                    val href=it.select("a").attr("href")
                    newEpisode(href)
                        {
                            this.season=season
                            this.episode=episode
                        }
            }
            newTvSeriesLoadResponse(title,url,TvType.TvSeries,episode)
        }
        else
            newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot=plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("#video_tabs a").amap {
            val href=it.attr("href")
            loadExtractor(href, "$mainUrl/", subtitleCallback, callback)
        }
        return true
    }


}
class StreamT : StreamTape() {
    override var mainUrl = "https://streamtape.to"
}

class Mxdrop : MixDrop() {
    override var mainUrl = "https://mxdrop.to"
}