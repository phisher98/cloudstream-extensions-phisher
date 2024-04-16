package com.javdoe

import android.util.Base64
//import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Javguru : MainAPI() {
    override var mainUrl              = "https://jav.guru/"
    override var name                 = "Javguru"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "category/english-subbed" to "English Subbed",
        "category/jav-uncensored" to "Jav Uncensored",
        "category/4k" to "4K Category",
        "tag/married-woman" to "Married Woman",
        "tag/mature-woman" to "Mature Woman",
        "tag/big-tits" to "Big Tits",
        "tag/stepmother" to "Stepmother",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("#main > div")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div > div > div > a > img").attr("alt")
        val href      = fixUrl(this.select("div > div > div > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div > div > div > a > img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document
            val results = document.select("#main > div").mapNotNull { it.toSearchResult() }
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

        val title= document.selectFirst("div.posts > h1")?.text().toString()
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

     override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val script=document.select("script:containsData(iframe_url)").html()
        val IFRAME_B64_REGEX = Regex(""""iframe_url":"([^"]+)"""")
         val iframeUrls = IFRAME_B64_REGEX.findAll(script)
             .map { it.groupValues[1] }
             .map { Base64.decode(it, Base64.DEFAULT).let(::String) }
             .toList()
         iframeUrls.map {
             val iframedoc=app.get(it, referer = it).document
             val olid=iframedoc.toString().substringAfter("var OLID = '").substringBefore("'")
             val newreq=iframedoc.toString().substringAfterLast("src=\"").substringBefore("'+OLID")
             val reverseid= olid.edoceD()
             val location= app.get("$newreq$reverseid", referer = it, allowRedirects = false)
             val link=location.headers["location"].toString()
             if (link.contains(".m3u"))
             {
                 callback.invoke(
                     ExtractorLink(
                         source = name,
                         name=name,
                         url = link,
                         referer = "",
                         quality = getQualityFromName(""),
                         type = INFER_TYPE
                     )
                 )
             }
             else{
                 loadExtractor(link, referer = it,subtitleCallback,callback)
             }
         }
        return true
    }

    fun String.edoceD(): String {
        var x = this.length - 1
        var edoceD = ""
        while (x >= 0) {
            edoceD += this[x]
            x--
        }
        return edoceD
    }
}
