package com.phisher98

import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
    override var requiresReferer = true
}

class Techinmind: GDMirrorbot() {
    override var name = "Techinmind Cloud AIO"
    override var mainUrl = "https://stream.techinmind.space"
    override var requiresReferer = true
}

class Dhcplay: VidHidePro() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
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

class server1 : VidStack() {
    override var name = "MultimoviesVidstack"
    override var mainUrl = "https://server1.uns.bio"
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
open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (sid, host) = if (!url.contains("key=")) {
            Pair(url.substringAfterLast("embed/"), getBaseUrl(app.get(url).url))
        } else {
            var pageText = app.get(url).text
            val finalId = Regex("""FinalID\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val myKey = Regex("""myKey\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val idType = Regex("""idType\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1) ?: "imdbid"
            val baseUrl = Regex("""let\s+baseUrl\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val hostUrl = baseUrl?.let { getBaseUrl(it) }

            if (finalId != null && myKey != null) {
                val apiUrl = "$mainUrl/mymovieapi?$idType=$finalId&key=$myKey"
                pageText = app.get(apiUrl).text
            }

            val jsonElement = JsonParser.parseString(pageText)
            if (!jsonElement.isJsonObject) return
            val jsonObject = jsonElement.asJsonObject

            val embedId = url.substringAfterLast("/")
            val sidValue = jsonObject["data"]?.asJsonArray
                ?.takeIf { it.size() > 0 }
                ?.get(0)?.asJsonObject
                ?.get("fileslug")?.asString
                ?.takeIf { it.isNotBlank() } ?: embedId

            Pair(sidValue, hostUrl)
        }

        val postData = mapOf("sid" to sid)
        val responseText = app.post("$host/embedhelper.php", data = postData).text

        val rootElement = JsonParser.parseString(responseText)
        if (!rootElement.isJsonObject) return
        val root = rootElement.asJsonObject

        val siteUrls = root["siteUrls"]?.asJsonObject ?: return
        val siteFriendlyNames = root["siteFriendlyNames"]?.asJsonObject

        val decodedMresult = when {
            root["mresult"]?.isJsonObject == true -> root["mresult"]!!.asJsonObject
            root["mresult"]?.isJsonPrimitive == true -> try {
                base64Decode(root["mresult"]!!.asString)
                    .let { JsonParser.parseString(it).asJsonObject }
            } catch (e: Exception) {
                Log.e("Phisher", "Failed to decode mresult: $e")
                return
            }
            else -> return
        }

        siteUrls.keySet().intersect(decodedMresult.keySet()).forEach { key ->
            val base = siteUrls[key]?.asString?.trimEnd('/') ?: return@forEach
            val path = decodedMresult[key]?.asString?.trimStart('/') ?: return@forEach
            val fullUrl = "$base/$path"
            val friendlyName = siteFriendlyNames?.get(key)?.asString ?: key

            try {
                Log.d("Phisher","$friendlyName $fullUrl")
                when (friendlyName) {
                    "StreamHG","EarnVids" -> VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)
                    "RpmShare", "UpnShare", "StreamP2p" -> VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)
                    else -> loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                Log.e("Phisher", "Failed to extract from $friendlyName at $fullUrl: $e")
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}

class Streamcasthub : ExtractorApi() {
    override var name = "Streamcasthub"
    override var mainUrl = "https://multimovies.streamcasthub.store"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id=url.substringAfterLast("/#")
        val m3u8= "https://ss1.rackcloudservice.cyou/ic/$id/master.txt"
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                m3u8,
                ExtractorLinkType.M3U8
            )
            {
                this.referer = url
            }
        )
    }
}



class Strwishcom : StreamWishExtractor() {
    override val name = "Strwish"
    override val mainUrl = "https://strwish.com"
    override val requiresReferer = true
}