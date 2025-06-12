package com.phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.AnimeWorld.Companion.API
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

class Pixdrive : Filesim() {
    override var mainUrl = "https://pixdrive.cfd"
}

class Ghbrisk : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://ghbrisk.com"
    override val requiresReferer = true
}

class Techinmind  : GDMirrorbot() {
    override var name = "Techinmind"
    override var mainUrl = "https://dlx.techinmind.space"
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



open class AWSStream : ExtractorApi() {
    override val name = "AWSStream"
    override val mainUrl = "https://z.awstream.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (url.contains("/video/")) {
            val extractedHash = url.substringAfterLast("/")
            val m3u8Url = "$API/player/index.php?data=$extractedHash&do=getVideo"
            Log.d("Phisher",m3u8Url)
            val header= mapOf("x-requested-with" to "XMLHttpRequest")
            val formdata= mapOf("hash" to extractedHash,"r" to "https://anime-world.co/")
            val response = app.post(m3u8Url, headers=header, data = formdata).parsedSafe<Response>()
            response?.videoSource?.let { m3u8 ->
                callback(
                    newExtractorLink(
                        name,
                        name,
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = ""
                        this.quality = Qualities.P1080.value
                    }
                )

                subtitleCallback.invoke(
                    SubtitleFile(
                        "English",
                        "$API/subs/m3u8/$extractedHash/subtitles-eng.vtt"
                    )
                )
            }
        }
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







/*

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
        Log.d("Phisher",url)
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
 */