package com.hindilink4u
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


class Hindilink4u : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.hindilinks4u.pics"
    override var name = "Hindilink4u"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
    )
    
    private fun toResult(post: Element): SearchResponse {
        val url = post.select("a").attr("href")
        val title = post.select("a").attr("title").toString()
        var imageUrl = post.select("img").attr("data-src")
       // Log.d("post",post.toString())
        //val quality = post.select(".video-label").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }
    override val mainPage = mainPageOf(
        "" to "Latest",
        "category/series" to "Series",
        "category/series/netflix" to "Neflix",
        "category/series/amazon-prime" to "Prime",
        
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url =if(page==1) "$mainUrl/${request.data}/" else  "$mainUrl/${request.data}/page/$page/" 
        val document = app.get(url).document
        
        val home = document.select("div.thumb").mapNotNull {
            toResult(it)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("div.thumb").mapNotNull {
            toResult(it)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
      // val plot = document.select("span[role^=presentation]").text().toString()
        val title = document.selectFirst("h1")?.text().toString()
       
        val poster = fixUrlNull(document.select("img[src~=https://www\\.hindilinks4u\\.pics/wp-content/uploads]")?.attr("src"))
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            //this.plot=plot
            
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var src = app.get(data).document.selectFirst("a[href^=https://streamtape]").attr("href")
        
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
