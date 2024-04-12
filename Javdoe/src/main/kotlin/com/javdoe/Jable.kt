package com.javdoe

//import android.util.Log
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.*

class Jable : MainAPI() {
    override var mainUrl              = "https://javgg.net/"
    override var name                 = "Javgg"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "genre/stepmother" to "Stepmother",
        "genre/married-woman" to "Married Woman",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            val document = app.get("$mainUrl/${request.data}/page/$page").document
            //Log.d("Test","$document")
            val home = document.select("div.items.normal > article")
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

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.poster > a").attr("title")
        val href      = fixUrl(this.select("div.poster > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.poster > img").attr("data-src"))
        //Log.d("Test","$posterUrl")
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/search/video/?s=$query&page=$i").document

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
        val sourcenumber = mutableListOf<String>()
        val datapost = document.selectFirst("#playeroptions > ul > li")?.attr("data-post") ?:""
        document.selectFirst("#playeroptions > ul > li").let {
            sourcenumber.add(it.toString())
        }
        sourcenumber.forEach {
            val doc=app.get("${mainUrl}/wp-json/dooplayer/v2/$datapost/movie/$it").parsedSafe<Root>()
            val serverurl=doc?.embedUrl.toString()
            if (serverurl.contains("javggvideo.xyz"))
            {
                val url=app.get(serverurl).toString().substringAfter("urlPlay = '").substringBefore("';")
                Log.d("Test",url)
                loadExtractor(serverurl,subtitleCallback,callback)
            }
            loadExtractor(serverurl,subtitleCallback,callback)
            }

        return true
    }

    data class Root(
        @JsonProperty("embed_url")
        val embedUrl: String,
        val type: String,
    )

    class Yipsu : Voe() {
        override val name = "Yipsu"
        override var mainUrl = "https://yip.su"
    }
}
