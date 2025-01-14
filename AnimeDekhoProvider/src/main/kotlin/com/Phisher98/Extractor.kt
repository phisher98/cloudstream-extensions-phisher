package com.Phisher98

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
import org.json.JSONObject
import java.net.URI
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


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


class VidStream : ExtractorApi() {
    override val name = "Vidstreaming"
    override val mainUrl = "https://vidstreamnew.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url,referer=referer).toString()
        val encodedString =
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1) ?:""
        val decoded = decrypt(encodedString)
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

    fun decrypt(encrypted: String): String {
        // Decode the Base64-encoded JSON string
        val data = JSONObject(String(Base64.getDecoder().decode(encrypted)))

        // Function to derive the key
        fun deriveKey(password: String, salt: String): ByteArray {
            val saltBytes = if (salt.contains("=")) Base64.getDecoder().decode(salt) else salt.toByteArray()
            val passwordBytes = (password + "sB0mZOqlRTy8CVpL").toCharArray()
            val spec = PBEKeySpec(passwordBytes, saltBytes, 1000, 64 * 8) // 64 bytes = 512 bits
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            return factory.generateSecret(spec).encoded
        }

        // Derive the key
        val key = deriveKey("NMhG08LLwixKRmgx", data.getString("salt"))

        // Decode IV and ciphertext
        val ivBytes = Base64.getDecoder().decode(data.getString("iv"))
        val iv = IvParameterSpec(ivBytes)
        val ciphertext = Base64.getDecoder().decode(data.getString("data"))

        // Decrypt the ciphertext using AES
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(32), "AES"), iv)
        val decrypted = cipher.doFinal(ciphertext)

        return String(decrypted, Charsets.UTF_8)
    }
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
