package com.IndianTV


import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.getRhinoContext
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import org.mozilla.javascript.Scriptable
import android.util.Base64
import java.nio.charset.StandardCharsets

fun hexStringToByteArray(hexString: String): ByteArray {
    val length = hexString.length
    val byteArray = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        byteArray[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
    }
    return byteArray
}

fun byteArrayToBase64(byteArray: ByteArray): String {
    val base64ByteArray = Base64.encode(byteArray, Base64.NO_PADDING)
    return String(base64ByteArray, StandardCharsets.UTF_8)
}

class IndianTVPlugin : MainAPI() {
    override var mainUrl = "https://madplay.live/hls/tata"
    override var name = "Indian TV"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "TATA",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home =
            document.select("div#listContainer > div.box1").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h2.text-center").text()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        //val category = this.select("p").text()

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/").document
        return document.select("div#listContainer div.box1:contains($query), div#listContainer div.box1:contains($query)")
            .mapNotNull {
                it.toSearchResult()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
        val poster =
            fixUrl("https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg")
        val showname =
            document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
        //val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()


        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = showname
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        callback.invoke(
            DrmExtractorLink(
                source = this.name,
                name = this.name,
                url = "https://bpprod7linear.akamaized.net/bpk-tv/irdeto_com_Channel_307/output/manifest.mpd",
                referer = "madplay.live",
                type=INFER_TYPE,
                quality = Qualities.Unknown.value,
                //type = ExtractorLinkType.DASH, // You need to determine the type of ExtractorLinkType here
                kid = "db6f1dbe0c005c2694914cca4791f6d5",
                key = "8923ad5aed715a8bf4f78239e10496dc",
            )
        )
        return true
    }
}
    

