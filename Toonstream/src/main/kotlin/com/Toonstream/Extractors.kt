package com.Toonstream

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName

class StreamSB8 : StreamSB() {
    override var mainUrl = "https://streamsb.net"
}

open class Vidstreaming : ExtractorApi() {
    override var name = "Vidstreaming"
    override var mainUrl = "https://vidstreaming.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).text
        val master = Regex("""JScript[\w+]?\s*=\s*'([^']+)""").find(doc)!!.groupValues[1]
        val decrypt = AesHelper.cryptoAESHandler(master, "a7igbpIApajDyNe".toByteArray(), false)
            ?.replace("\\", "")
            ?: throw ErrorLoadingException("error decrypting")
        val vidFinal = Extractvidlink(decrypt)
        val subtitle = Extractvidsub(decrypt)
        val headers =
            mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                // "Referer" to "https://vidxstream.xyz/",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/116.0",
            )
        callback.invoke(
            ExtractorLink(
                source = "ToonStream",
                name="ToonStream",
                url=vidFinal,
                referer= mainUrl,
                quality = getQualityFromName(""),
                type = INFER_TYPE,
                headers = headers,
            )
        )
        subtitleCallback.invoke(
            SubtitleFile(
                "eng",
                subtitle
            )
        )
    }
    fun Extractvidlink(url: String): String {
        val file=url.substringAfter("sources: [{\"file\":\"").substringBefore("\",\"")
        return file
    }

    fun Extractvidsub(url: String): String {
        val file=url.substringAfter("tracks: [{\"file\":\"").substringAfter("file\":\"").substringBefore("\",\"")
        return file
    }

}