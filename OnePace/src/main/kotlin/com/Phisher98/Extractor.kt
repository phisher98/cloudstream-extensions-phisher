package com.phisher98

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import java.net.URI

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
                newExtractorLink(
                    this.name,
                    this.name,
                    url = m3u8,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        else
        {
            val txt = app.get(url).text
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1).toString()
            return listOf(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = m3u8,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}


class Vidmolynet : Vidmoly() {
    override val mainUrl = "https://vidmoly.net"
}

class Animedekhoco : ExtractorApi() {
    override val name = "Animedekhoco"
    override val mainUrl = "https://animedekho.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc: Document? = if (url.contains("url=")) app.get(url).documentLarge else null
        val text: String? = if (!url.contains("url=")) app.get(url).text else null

        val links = mutableListOf<Pair<String, String>>()

        doc?.select("select#serverSelector option")?.forEach { option ->
            val link = option.attr("value")
            val name = option.text().ifBlank { "Unknown" }
            if (link.isNotBlank()) links.add(name to link)
        }

        text?.let {
            val regex = Regex("""file\s*:\s*"([^"]+)"""")
            regex.find(it)?.groupValues?.get(1)?.let { link ->
                links.add("Player File" to link)
            }
        }

        links.forEach { (serverName, serverUrl) ->
            callback.invoke(
                newExtractorLink(
                    serverName,
                    serverName,
                    url = serverUrl,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}


class Cdnwish : StreamWishExtractor() {
    override var name = "Streamwish"
    override var mainUrl = "https://cdnwish.com"
}

class vidcloudupns : VidStack() {
    override var mainUrl = "https://vidcloud.upns.ink"
}

class Dhcplay: VidHidePro() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
    override var requiresReferer = true
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
        val embedId = url.substringAfterLast("/")
        val postData = mapOf("sid" to embedId)

        val responseJson = app.post("$host/embedhelper.php", data = postData).text
        val jsonElement = JsonParser.parseString(responseJson)
        if (!jsonElement.isJsonObject) return

        val root = jsonElement.asJsonObject
        val siteUrls = root["siteUrls"]?.asJsonObject ?: return
        val siteFriendlyNames = root["siteFriendlyNames"]?.asJsonObject

        val decodedMresult: JsonObject = when {
            root["mresult"]?.isJsonObject == true -> {
                root["mresult"]?.asJsonObject!!
            }
            root["mresult"]?.isJsonPrimitive == true -> {
                val mresultBase64 = root["mresult"]?.asString ?: return
                try {
                    val jsonStr = base64Decode(mresultBase64)
                    JsonParser.parseString(jsonStr).asJsonObject
                } catch (e: Exception) {
                    Log.e("Error:", "Failed to decode mresult base64: $e")
                    return
                }
            }
            else -> return
        }

        val commonKeys = siteUrls.keySet().intersect(decodedMresult.keySet())

        for (key in commonKeys) {
            val base = siteUrls[key]?.asString?.trimEnd('/') ?: continue
            val path = decodedMresult[key]?.asString?.trimStart('/') ?: continue
            val fullUrl = "$base/$path"

            val friendlyName = siteFriendlyNames?.get(key)?.asString ?: key

            try {
                when (friendlyName) {
                    "EarnVids" -> {
                        VidhideExtractor().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    "StreamHG" -> {
                        VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    "RpmShare", "UpnShare", "StreamP2p" -> {
                        VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    else -> {
                        loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                Log.e("Error:", "Failed to extract from $friendlyName at $fullUrl")
                continue
            }
        }

    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}