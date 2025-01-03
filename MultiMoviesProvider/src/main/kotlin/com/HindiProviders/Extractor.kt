package com.Phisher98

import com.google.gson.JsonElement
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.json.Json
import java.net.URI
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lagradost.cloudstream3.utils.loadExtractor

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
    override var requiresReferer = true
}

class Multimovies: StreamWishExtractor() {
    override var name = "Multimovies Cloud"
    override var mainUrl = "https://multimovies.cloud"
    override var requiresReferer = true
}

class Animezia : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://animezia.cloud"
    override var requiresReferer = true
}

class server2 : VidhideExtractor() {
    override var name = "Multimovies Vidhide"
    override var mainUrl = "https://server2.shop"
    override var requiresReferer = true
}

class Asnwish : StreamWishExtractor() {
    override val name = "Streanwish Asn"
    override val mainUrl = "https://asnwish.com"
    override val requiresReferer = true
}

class CdnwishCom : StreamWishExtractor() {
    override val name = "Cdnwish"
    override val mainUrl = "https://cdnwish.com"
    override val requiresReferer = true
}

class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = getBaseUrl(app.get(url).url)
        val embed = url.substringAfter("embed/")
        val data = mapOf("sid" to embed)
        val jsonString = app.post("$host/embedhelper.php", data = data).toString()
        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
        val siteUrls = jsonObject.getAsJsonObject("siteUrls")
        val mresult = jsonObject.getAsJsonObject("mresult")
        siteUrls.keySet().forEach { key->
            val siteValue: JsonElement = siteUrls.get(key)
            val mresultValue: JsonElement = mresult.get(key)
            val href = siteValue.asString + mresultValue.asString
            android.util.Log.d("Phisher",href)
            loadExtractor(href,subtitleCallback, callback)
        }
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}

class Strwishcom : StreamWishExtractor() {
    override val name = "Strwish"
    override val mainUrl = "https://strwish.com"
    override val requiresReferer = true
}



open class VidhideExtractor : ExtractorApi() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhide.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(
            url, referer = referer ?: "$mainUrl/", interceptor = WebViewResolver(
                Regex("""master\.m3u8""")
            )
        )
        val sources = mutableListOf<ExtractorLink>()
        if (response.url.contains("m3u8"))
            sources.add(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = response.url,
                    referer = referer ?: "$mainUrl/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        return sources
    }
}
