package com.coxju

import org.jsoup.nodes.Element
import org.jsoup.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*
import java.io.File
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import com.lagradost.cloudstream3.extractors.JWPlayer


public val homePoster ="https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg"

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
        //val category = this.select("p").text()

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/").document
        return document.select("div#listContainer div.box1:contains($query), div#listContainer div.box1:contains($query)").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title       = document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
        val poster      = fixUrl("https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg")
        var showname = document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
        //val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()
    

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot      = showname
        }
    }
    
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    val document = app.get(data).document
    document.select("div#jwplayer + script").text().toString().let{
        val decoded = if (JsUnpacker(script.data()).detect()) {
            JsUnpacker(script.data()).unpack()!!
        } else {
            script.data()
        }
        val result = AppUtils.parseJson<Map<String, String>>(decoded)
        }

        val key = result?.get("key")
        val file = result?.get("file")
        val keyId = result?.get("keyId")
        log.d("key","key")
        log.d("file","file")
        log.d("keyId","keyId")
       /*  val base64Key = key?.let {
            val bytes = javax.xml.bind.DatatypeConverter.parseHexBinary(it)
            val base64 = java.util.Base64.getEncoder().encodeToString(bytes).trimEnd('=')
        }
        val base64KeyId = keyId?.let {
            val bytes = javax.xml.bind.DatatypeConverter.parseHexBinary(it)
            val base64 = java.util.Base64.getEncoder().encodeToString(bytes).trimEnd('=')
        }
        */
        
        }
                    callback.invoke(
                    DrmExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = "file",
                        referer = "madplay.live",
                        type=INFER_TYPE,
                        quality = Qualities.Unknown.value,
                        //type = ExtractorLinkType.DASH, // You need to determine the type of ExtractorLinkType here
                        kid = "base64Key",
                        key = "base64KeyId",                        
                    )
                ) 
    return true
    }
}


