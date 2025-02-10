package com.toonhub4u

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

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
        val embed = url.substringAfterLast("/")
        val data = mapOf("sid" to embed)
        val jsonString = app.post("$host/embedhelper.php", data = data).toString()
        val jsonElement: JsonElement = JsonParser.parseString(jsonString)
        if (!jsonElement.isJsonObject) {
            Log.e("Error:", "Unexpected JSON format: Response is not a JSON object")
            return
        }
        val jsonObject = jsonElement.asJsonObject
        val siteUrls = jsonObject["siteUrls"]?.takeIf { it.isJsonObject }?.asJsonObject
        val mresult = jsonObject["mresult"]?.takeIf { it.isJsonObject }?.asJsonObject
        val siteFriendlyNames = jsonObject["siteFriendlyNames"]?.takeIf { it.isJsonObject }?.asJsonObject
        if (siteUrls == null || siteFriendlyNames == null || mresult == null) {
            return
        }
        val commonKeys = siteUrls.keySet().intersect(mresult.keySet())
        commonKeys.forEach { key ->
            val siteName = siteFriendlyNames[key]?.asString
            if (siteName == null) {
                Log.e("Error:", "Skipping key: $key because siteName is null")
                return@forEach
            }
            val siteUrl = siteUrls[key]?.asString
            val resultUrl = mresult[key]?.asString
            if (siteUrl == null || resultUrl == null) {
                Log.e("Error:", "Skipping key: $key because siteUrl or resultUrl is null")
                return@forEach
            }
            val href = siteUrl + resultUrl
            loadExtractor(href, subtitleCallback, callback)
        }

    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}


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


