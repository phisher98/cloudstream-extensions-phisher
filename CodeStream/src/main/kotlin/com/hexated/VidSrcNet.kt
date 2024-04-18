package com.KillerDogeEmpire

//import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities


/*
suspend fun Extractvidsrcnetservers(url: String): String? {
    val rcp=app.get("https://vidsrc.stream/rcp/$url", referer = "https://vidsrc.net/").document
    val link = rcp.selectFirst("script:containsData(player_iframe)")?.data()?.substringAfter("src: '")?.substringBefore("',")
    return "http:$link"
}

 */

class VidSrcNetExtractorServers : ExtractorApi() {
    override val mainUrl = "https://vidsrc.net/"
    override val name = "VidSrcNet"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val servername = referer.toString()
        if (url.isNotEmpty()) {
            when (servername) {
                "VidSrc PRO" -> {
                    val URI =
                        app.get(
                            url,
                            referer = "https://vidsrc.net/"
                        ).document.selectFirst("script:containsData(Playerjs)")?.data()
                            ?.substringAfter("file:\"#9")?.substringBefore("\"")
                            ?.replace(Regex("/@#@\\S+?=?="), "")?.let { base64Decode(it) }
                            .toString()
                    callback.invoke(
                        ExtractorLink(
                            "Vidsrc",
                            "Vidsrc",
                            URI
                                ?: return,
                            "https://vidsrc.stream/",
                            Qualities.P1080.value,
                            INFER_TYPE
                        )
                    )
                }
            }
        }
    }
}