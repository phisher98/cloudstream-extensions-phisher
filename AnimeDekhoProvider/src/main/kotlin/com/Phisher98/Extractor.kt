package com.phisher98

import com.lagradost.cloudstream3.USER_AGENT
import com.google.gson.JsonParser
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = " https://filemoon.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url).document.selectFirst("iframe")?.attr("src") ?:""
        val res= app.get(href, headers = mapOf("Accept-Language" to "en-US,en;q=0.5","sec-fetch-dest" to "iframe")).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        val m3u8= JsUnpacker(res).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)
        }
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?:"",
                url,
                Qualities.P1080.value,
                type = ExtractorLinkType.M3U8,
            )
        )
    }
}

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

class VidStream : Chillx() {
    override val name = "VidStream"
    override val mainUrl = "https://vidstreaming.xyz"
    override val requiresReferer = true
}
open class Raretoon : Chillx() {
    override val name = "VidStream"
    override val mainUrl = "https://raretoonsindia.co"
    override val requiresReferer = true
}

open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "priority" to "u=0, i",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        try {
            val res = app.get(url, referer = mainUrl, headers = headers).toString()

            val encodedString =
                Regex("(?:const|let|var|\\bwindow\\.\\w+)\\s+\\w*\\s*=\\s*'([^']*)'").find(
                    res
                )?.groupValues?.get(1)?.trim() ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Decrypt the encoded string
            val payload = """
             {
                "input": "$encodedString",
                "key": "ojl,&[y^-{cH!ux1"
             }
            """.trimIndent().toRequestBody("application/json".toMediaTypeOrNull())
            val decryptedData = app.post("https://interesting-zebra-51.deno.dev", requestBody  = payload, headers= mapOf("Content-Type" to "application/json")).text
            // Extract the m3u8 URL from decrypted data
            val m3u8 = Regex("(https?://[^\\s\"'\\\\]*m3u8[^\\s\"'\\\\]*)").find(decryptedData)?.groupValues?.get(1)?.trim() ?: ""
            if (m3u8.isEmpty()) {
                throw Exception("m3u8 URL not found")
            }

            // Prepare headers
            val header = mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT
            )

            // Return the extractor link
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    m3u8,
                    mainUrl,
                    Qualities.P1080.value,
                    INFER_TYPE,
                    headers = header
                )
            )

            // Extract and return subtitles
            val subtitles = extractSrtSubtitles(decryptedData)
            subtitles.forEachIndexed { _, (language, url) ->
                subtitleCallback.invoke(SubtitleFile(language, url))
            }

        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """\[(.*?)](https?://[^\s,"]+\.srt)""".toRegex()
        val matches = regex.findAll(subtitle)

        return matches.map { match ->
            val label = match.groupValues[1]
            val file = match.groupValues[2]
            label to file
        }.toList()
    }

    /** End **/
}


class Multimovies: StreamWishExtractor() {
    override var name = "Multimovies Cloud"
    override var mainUrl = "https://multimovies.cloud"
    override var requiresReferer = true
}

class FileMoonNL : Filesim() {
    override val mainUrl = "https://filemoon.nl"
    override val name = "FileMoon"
}

class Vidmolynet : Vidmoly() {
    override val mainUrl = "https://vidmoly.net"
}

class Cdnwish : StreamWishExtractor() {
    override var name = "Streamwish"
    override var mainUrl = "https://cdnwish.com"
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
        val siteUrls = jsonObject.getAsJsonObject("siteUrls").asJsonObject
        val mresult = jsonObject.getAsJsonObject("mresult").toString()
        val regex = """"(\w+)":"([^"]+)"""".toRegex()
        val mresultMap = regex.findAll(mresult).associate {
            it.groupValues[1] to it.groupValues[2]
        }

        val matchingResults = mutableListOf<Pair<String, String>>()
        siteUrls.keySet().forEach { key ->
            if (mresultMap.containsKey(key)) { // Use regex-matched keys and values
                val value1 = siteUrls.get(key).asString
                val value2 = mresultMap[key].orEmpty()
                matchingResults.add(Pair(value1, value2))
            }
        }

        matchingResults.amap { (siteUrl, result) ->
            val href = "$siteUrl$result"
            loadExtractor(href, subtitleCallback, callback)
        }

    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}

class Animezia : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://animezia.cloud"
    override var requiresReferer = true
}

data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)
