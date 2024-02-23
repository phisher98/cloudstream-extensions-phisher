package com.coxju

import org.jsoup.nodes.Element
import org.jsoup.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*


public val homePoster ="https://github.com/phisher98/HindiProviders/blob/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg"

class IndianTVPlugin : MainAPI() {
    override var mainUrl              = "https://madplay.live/hls/tata"
    override var name                 = "TATA Sky"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "TATA",
)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div#listContainer > div.box1").mapNotNull { it.toSearchResult()}

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("h2.text-center").text()
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
/* 
        for (i in 1..10) {
            val document = app.get("${mainUrl}/page/$i?s=$query").document

            val results = document.select("div.box1").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }
*/
        return searchResponse
        
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title       = document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
        val poster      = homePoster
        val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()
    

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    fun extractValuesFromWebsite(url: String): Triple<String?, String?, String?> {
        val document = app.get(url).document
    
        val scriptElements = doc.select("script")
    
        var file: String? = null
        var keyId: String? = null
        var key: String? = null
    
        scriptElements.forEach { script ->
            val scriptContent = script.html()
    
            // Extract 'file'
            val fileMatch = "'file':\\s*'([^']*)'".toRegex().find(scriptContent)
            if (fileMatch != null) {
                file = fileMatch.groupValues[1]
            }
    
            // Extract 'keyId'
            val keyIdMatch = "'keyId':\\s*'([^']*)'".toRegex().find(scriptContent)
            if (keyIdMatch != null) {
                keyId = keyIdMatch.groupValues[1]
            }
    
            // Extract 'key'
            val keyMatch = "'key':\\s*'([^']*)'".toRegex().find(scriptContent)
            if (keyMatch != null) {
                key = keyMatch.groupValues[1]
            }
        }
    
        return Triple(file, keyId, key)
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        document.select("video.vjs-tech").map { res ->
            callback.invoke(
                    ExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = fixUrl(res.attr("src")?.trim().toString()),
                        referer = data,
                        quality = Qualities.Unknown.value
                    )
            )
        }

        return true
    }
}