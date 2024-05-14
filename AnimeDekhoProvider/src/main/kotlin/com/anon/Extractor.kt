package com.anon


import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

open class Streamruby : ExtractorApi() {
    override var name = "Streamruby"
    override var mainUrl = "streamruby.com"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        if (url.contains("/e/"))
        {
            val newurl=url.replace("/e","")
            val txt = app.get(newurl).text
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1).toString()
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8,
                    mainUrl,
                    Qualities.Unknown.value,
                    type = INFER_TYPE
                )
            )
        }
        else
        {
            val txt = app.get(url).text
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1).toString()
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8,
                    mainUrl,
                    Qualities.Unknown.value,
                    type = INFER_TYPE
                )
            )
        }
    }
}


open class VidStream : ExtractorApi() {
    override var name = "VidStream"
    override var mainUrl = "https://vidstreamnew.xyz"
    override val requiresReferer = false
    private val serverUrl = "https://vidxstream.xyz"
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {
        val doc = app.get(url).text
        val master = Regex("""JScript[\w+]?\s*=\s*'([^']+)""").find(doc)!!.groupValues[1]
        val key =
            app.get("https://raw.githubusercontent.com/rushi-chavan/multi-keys/keys/keys.json")
                .parsedSafe<Keys>()?.key?.get(0)
                ?: throw ErrorLoadingException("Unable to get key")
        val decrypt =
            AesHelper.cryptoAESHandler(master, key.toByteArray(), false)?.replace("\\", "")
                ?: throw ErrorLoadingException("error decrypting")
        val vidFinal = Extractvidlink(decrypt)
        Log.d("Phisher Test key",key)
        Log.d("Phisher Test master",master)
        Log.d("Phisher Test vidFinal",vidFinal)
        Log.d("Phisher Test decrypt",decrypt)
        val subtitle = Extractvidsub(decrypt)
        val headers =
            mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to serverUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                // "Referer" to "https://vidxstream.xyz/",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            )

        callback.invoke(
            ExtractorLink(
                source = "VidStream",
                name = "VidStream",
                url = vidFinal,
                referer = "$serverUrl/",
                quality = Qualities.Unknown.value,
                isM3u8 = true,
                headers = headers,
            ),
        )
        subtitleCallback.invoke(
            SubtitleFile(
                "English",
                subtitle
            )
        )
    }
}

class Vidmolynet : Vidmoly() {
    override val mainUrl = "https://vidmoly.net"
}

class Cdnwish : Filesim() {
    override var name = "Streamwish"
    override var mainUrl = "https://cdnwish.com"
}

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {
        app.get(url).document.select("ul#videoLinks li").map {
            val link=it.attr("data-link")
            loadExtractor(link,subtitleCallback, callback)
        }
    }
}


fun Extractvidlink(url: String): String {
    Log.d("Phisher Test url",url)
    val file=Regex("""file: "(.*?)"""").find(url)!!.groupValues[1]
    return file
}

fun Extractvidsub(url: String): String {
    val file=url.substringAfter("tracks: [{\"file\":\"").substringAfter("file\":\"").substringBefore("\",\"")
    return file
}

data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)
data class Keys(
    @JsonProperty("chillx") val key: List<String>
)