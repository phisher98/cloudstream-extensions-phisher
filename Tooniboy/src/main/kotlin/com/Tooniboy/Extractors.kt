package com.Tooniboy

import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI
import java.util.zip.Inflater

class StreamSB8 : StreamSB() {
    override var mainUrl = "https://streamsb.net"
}

class Vidstreamxyz : Chillx() {
    override val name = "VidStream"
    override val mainUrl = "https://vidstreaming.xyz"
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
        val res = app.get(url).toString()
        val encodedString =
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1) ?:""
        val decoded = decodeEncryptedData(encodedString)
        android.util.Log.d("Phisher",decoded.toString())

        val m3u8 =Regex("file:\\s*\"(.*?)\"").find(decoded ?:"")?.groupValues?.get(1) ?:""
        val header =mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.5",
            "Origin" to mainUrl,
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "user-agent" to USER_AGENT,)
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

        val subtitles = extractSrtSubtitles(decoded ?:"")
        subtitles.forEachIndexed { _, (language, url) ->
            subtitleCallback.invoke(
                SubtitleFile(
                    language,
                    url
                )
            )
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """\[([^]]+)](https?://[^\s,]+\.srt)""".toRegex()

        return regex.findAll(subtitle).map { match ->
            val (language, url) = match.destructured
            language.trim() to url.trim()
        }.toList()
    }


    private fun decodeEncryptedData(encryptedString: String?): String? {
        if (encryptedString == null) return null

        return try {
            val decodedBytes = android.util.Base64.decode(encryptedString, android.util.Base64.DEFAULT)

            val decodedCharacters = decodedBytes.map { byte ->
                val binaryRepresentation = byte.toUByte().toString(2).padStart(8, '0')
                val reversedBinary = binaryRepresentation.reversed()
                reversedBinary.toInt(2).toByte()
            }
            val byteArray = ByteArray(decodedCharacters.size) { decodedCharacters[it] }
            val decompressedData = Inflater().run {
                setInput(byteArray)
                val output = ByteArray(1024 * 4)
                val decompressedSize = inflate(output)
                output.copyOf(decompressedSize).toString(Charsets.UTF_8)
            }
            val specialToAlphabetMap = mapOf(
                '!' to 'a', '@' to 'b', '#' to 'c', '$' to 'd', '%' to 'e',
                '^' to 'f', '&' to 'g', '*' to 'h', '(' to 'i', ')' to 'j'
            )
            val processedData = decompressedData.map { char ->
                specialToAlphabetMap[char] ?: char
            }.joinToString("")
            val finalDecodedData = android.util.Base64.decode(processedData, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
            android.util.Log.d("Phisher",finalDecodedData)
            finalDecodedData
        } catch (e: Exception) {
            println("Error decoding string: ${e.message}")
            null
        }
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
                Log.d("Phisher",value1)
                val value2 = mresultMap[key].orEmpty()
                matchingResults.add(Pair(value1, value2))
            }
        }

        matchingResults.amap { (siteUrl, result) ->
            val href = "$siteUrl$result"
            android.util.Log.d("Phisher", "Generated Href: $href")
            loadExtractor(href, subtitleCallback, callback)
        }

    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}
