package com.Tooniboy

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
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
        val key =
            app.get("https://raw.githubusercontent.com/rushi-chavan/multi-keys/keys/keys.json")
                .parsedSafe<Keys>()?.key?.get(0)
                ?: throw ErrorLoadingException("Unable to get key")
        val master = Regex("""JScript[\w+]?\s*=\s*'([^']+)""").find(doc)!!.groupValues[1]
        val decrypt = AesHelper.cryptoAESHandler(master, key.toByteArray(), false)
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
                source = "Tooniboy",
                name="Tooniboy",
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
        Log.d("Test",url)
        val file=url.substringAfter("tracks: [{\"file\":\"").substringAfter("file\":\"").substringBefore("\",\"")
        Log.d("Test",file)
        return file
    }
}

open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = false

    @Suppress("NAME_SHADOWING")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url=url.replace("/e","")
        val response=app.get(url,referer=url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).document
        val script = response.selectFirst("script:containsData(vplayer)")?.data().toString()
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to url,
        )

        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    link,
                    "https://rubystm.com",
                    Qualities.P1080.value,
                    type = INFER_TYPE,
                    headers
                )
            )
        }
    }
}

class Cdnwish : StreamWishExtractor() {
    override var mainUrl = "https://cdnwish.com"
}

class vidhidevip : VidhideExtractor() {
    override var mainUrl = "https://vidhidevip.com"
}

class D000d : DoodLaExtractor() {
    override var mainUrl = "https://d000d.com"
}


class FileMoonnl : Filesim() {
    override val mainUrl = "https://filemoon.nl"
    override val name = "FileMoon"
}

data class Keys(
    @JsonProperty("chillx") val key: List<String>
)