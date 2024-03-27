package com.javdoe

//import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Javdoe : MainAPI() {
    override var mainUrl              = "https://javdoe.sh"
    override var name                 = "Javdoe"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "recent" to "Latest",
        "releaseday" to "New Release",
        "english-subtitle" to "English Subtitle",
        "asian" to "Asian",
        "tag/uncensored" to "Uncensored",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page == 1) {
            val document = app.get("$mainUrl/${request.data}/").document
            //Log.d("Test","$document")
            val home = document.select("#content > div > div > div > ul > li")
                .mapNotNull { it.toSearchResult() }
            //Log.d("Test", "$home")
            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = false
                ),
                hasNext = true
            )
        }
        else {
            val document = app.get("$mainUrl/${request.data}/$page/").document
            //Log.d("Test","$document")
            val home = document.select("#content > div > div > div > ul > li")
                .mapNotNull { it.toSearchResult() }
            //Log.d("Test", "$home")
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

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.video > a").attr("title").trim()
        val href      = fixUrl(this.select("div.video > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.video > a > div > img").attr("src"))
        //Log.d("Test","$posterUrl")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query&id=5036").document

            val results = document.select("#content > div > div > div > ul > li").mapNotNull { it.toSearchResult() }

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

        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val recommendations =
            document.select("ul.videos.related >  li").map {
                val recomtitle = it.selectFirst("div.video > a")?.attr("title")?.trim().toString()
                val recomhref = it.selectFirst("div.video > a")?.attr("href").toString()
                val recomposterUrl = it.select("div.video > a > div > img").attr("src")
                val recomposter="https://javdoe.sh$recomposterUrl"
                newAnimeSearchResponse(recomtitle, recomhref, TvType.NSFW) {
                    this.posterUrl = recomposter
                }
            }
        //println(poster)
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
            this.recommendations=recommendations
        }
    }

    @Suppress("NAME_SHADOWING")
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val sourcelist = mutableListOf<String>()
        val onclickValue = document.select(".button_choice_server").attr("onclick")
        val playEmbedContent = Regex("'(.*?)'").find(onclickValue)?.groupValues?.get(1)
        //Log.d("Testlink","$playEmbedContent")
        val sources= app.get(playEmbedContent.toString()).document
        val liElements = sources.select("li.button_choice_server")
        for (liElement in liElements) {
            val onclickValue = liElement.attr("onclick")
            val url = onclickValue.substringAfter("playEmbed('").substringBefore("')")
            println("${liElement.text()}: $url")
            sourcelist.add(url)
        }
        sourcelist.forEach {
            loadExtractor(it,subtitleCallback,callback)
        }
        return true
    }
}
