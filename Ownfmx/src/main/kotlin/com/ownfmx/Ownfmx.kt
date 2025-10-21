package com.phisher98
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


class Ownfmx : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://ownfmx.com"
    override var name = "ownfmx"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
    )
    
    private fun toResult(post: Element): SearchResponse {
        val url = post.select("a").attr("href")
        val title = post.select("img").attr("alt")
        val imageUrl = post.select("img").attr("src")
       // Log.d("post",post.toString())
        //val quality = post.select(".video-label").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }
    override val mainPage = mainPageOf(
        "movies" to "Latest",
        "movies?category=bollywood" to "Bollywood",
        "movies?category=hindi-dubbed" to "Hindi Dubbed",
        "movies?category=web-series" to "Webseries"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}" 
        val document = app.get(url).document
        
        val home = document.select(".movie-card").mapNotNull {
            toResult(it)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/movies?title=$query").document

        return document.select(".movie-card").mapNotNull {
            toResult(it)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.text-muted > span")?.text().toString()
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
 
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val src = app.get(data).document.select("a[href^=https://streamtape]").attr("href")
        //Log.d("link",src)
        loadExtractor(
                src,
                "$mainUrl/",
                subtitleCallback,
                callback
           
        )
        return true
    }


}
class StreamT : StreamTape() {
    
    override var mainUrl = "https://streamtape.to"
}
