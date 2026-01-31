package com.ohli24

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

class MichealCDN : Cdndania() {
    override val name = "MichealCDN"
    override val mainUrl = "https://michealcdn.com"
}

open class Cdndania : ExtractorApi() {
    override val name = "CDNdania"
    override val mainUrl = "https://cdndania.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host= getBaseUrl(url)
        if (url.contains("/video/")) {
            val doc = app.get(url, referer = referer).document.selectFirst("script:containsData(playerjsSubtitle)")?.data().orEmpty()
            val srtRegex = Regex("""playerjsSubtitle\s*=\s*"[^"]*(https?://[^"]+\.srt)"""")
            val srtUrl = srtRegex.find(doc)?.groupValues?.get(1) ?: ""

            val extractedHash = url.substringAfterLast("/")
            val m3u8Url = "$host/player/index.php?data=$extractedHash&do=getVideo"
            val header= mapOf("x-requested-with" to "XMLHttpRequest")
            val formdata= mapOf("hash" to extractedHash,"r" to "$referer")
            val response = app.post(m3u8Url, headers=header, data = formdata).parsedSafe<Response>()
            response?.videoSource?.let { m3u8 ->
                callback(
                    newExtractorLink(
                        "CDN",
                        "CDN",
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = ""
                        this.quality = Qualities.P1080.value
                    }
                )

                subtitleCallback.invoke(
                    newSubtitleFile(
                        "Korean",
                        srtUrl
                    )
                )
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}

data class Response(
    val hls: Boolean,
    val videoImage: String,
    val videoSource: String,
    val securedLink: String,
    val downloadLinks: List<Any?>,
    val attachmentLinks: List<Any?>,
    val ck: String,
)
