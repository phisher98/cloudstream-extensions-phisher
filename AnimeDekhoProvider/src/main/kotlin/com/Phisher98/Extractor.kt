package com.Phisher98


//import android.util.Log
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import android.util.Log
import com.lagradost.cloudstream3.USER_AGENT
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

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


class VidStream : ExtractorApi() {
    override val name = "Vidstreaming"
    override val mainUrl = "https://vidstreamnew.xyz"
    override val requiresReferer = true
    private var key: String? = null

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url,referer=referer).toString()
        val encodedString =
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1)?.replace("_", "/")
                ?.replace("-", "+")?.trim()
                ?: ""
        val fetchkey = fetchKey() ?: throw ErrorLoadingException("Unable to get key")
        val key = logSha256Checksum(fetchkey)
        val decodedBytes: ByteArray = decodeBase64WithPadding(encodedString)
        val byteList: List<Int> = decodedBytes.map { it.toInt() and 0xFF }
        val processedResult = decryptWithXor(byteList, key)
        val decoded= base64Decode(processedResult)
        val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decoded)?.groupValues?.get(1)
            ?.trim()
            ?:""
        com.lagradost.api.Log.d("Phisher","$decoded $m3u8")

        val header =
            mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT,
            )
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
    }

    private fun logSha256Checksum(input: String): List<Int> {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val sha256Hash = messageDigest.digest(input.toByteArray())
        val unsignedIntArray = sha256Hash.map { it.toInt() and 0xFF }
        return unsignedIntArray
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun decodeBase64WithPadding(xIdJ2lG: String): ByteArray {
        // Ensure padding for Base64 encoding (if necessary)
        var paddedString = xIdJ2lG
        while (paddedString.length % 4 != 0) {
            paddedString += '=' // Add necessary padding
        }

        // Decode using standard Base64 (RFC4648)
        return Base64.getDecoder().decode(paddedString)
    }

    private fun decryptWithXor(byteList: List<Int>, xorKey: List<Int>): String {
        val result = StringBuilder()
        val length = byteList.size

        for (i in 0 until length) {
            val byteValue = byteList[i]
            val keyValue = xorKey[i % xorKey.size]  // Modulo operation to cycle through NDlDrF
            val xorResult = byteValue xor keyValue  // XOR operation
            result.append(xorResult.toChar())  // Convert result to char and append to the result string
        }

        return result.toString()
    }

    private suspend fun fetchKey(): String? {
        return app.get("https://raw.githubusercontent.com/Rowdy-Avocado/multi-keys/refs/heads/keys/index.html")
            .parsedSafe<Keys>()?.key?.get(0)?.also { key = it }
    }
    data class Keys(
        @JsonProperty("chillx") val key: List<String>)
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
        val siteUrls = jsonObject.getAsJsonObject("siteUrls")
        val mresult = jsonObject.getAsJsonObject("mresult")
        siteUrls.keySet().forEach { key->
            val siteValue: JsonElement = siteUrls.get(key)
            val mresultValue: JsonElement = mresult.get(key)
            val href = siteValue.asString + mresultValue.asString
            loadExtractor(href,subtitleCallback, callback)
        }
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}

data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)
