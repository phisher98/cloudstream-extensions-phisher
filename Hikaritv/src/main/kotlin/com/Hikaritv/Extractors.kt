package com.hikaritv


import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import android.util.Base64
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import org.json.JSONObject
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class Ghbrisk : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://ghbrisk.com"
}

class Swishsrv : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://swishsrv.com"
}


class Boosterx : Chillx() {
    override val name = "Boosterx"
    override val mainUrl = "https://boosterx.stream"
}

// Why are so mad at us Cracking it
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
        Log.d("Phisher",encodedString.toString())
        val decoded = decodeEncryptedData(encodedString) ?:""
        Log.d("Phisher",decoded.toString())
        val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decoded)?.groupValues?.get(1)
            ?.trim()
            ?:""
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


    private fun decodeEncryptedData(encryptedString: String): String? {
        val decodedData = Base64.decode(encryptedString, Base64.DEFAULT).toString(Charsets.UTF_8)
        val parsedJson = JSONObject(decodedData)
        val salt = stringTo32BitWords(parsedJson.getString("salt"))
        val password = stringTo32BitWords("=e{27)K~vfTftY9s4hRsRx\$j=P~w2mpN")
        val derivedKey = deriveKey(password, salt, keySize = 32, iterations = 1000, hashAlgo = "SHA-512")

        val iv = Base64.decode(parsedJson.getString("iv"), Base64.DEFAULT)
        val encryptedContent = Base64.decode(parsedJson.getString("data"), Base64.DEFAULT)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(derivedKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        val decryptedData = cipher.doFinal(encryptedContent)

        val finalResult = String(decryptedData) // Simplified for demonstration
        return finalResult

    }

    private fun stringTo32BitWords(text: String): IntArray {
        val words = IntArray((text.length + 3) / 4)
        for (i in text.indices) {
            words[i shr 2] = words[i shr 2] or (text[i].toInt() and 255 shl (24 - (i % 4) * 8))
        }
        return words
    }

    private fun deriveKey(password: IntArray, salt: IntArray, keySize: Int, iterations: Int, hashAlgo: String): ByteArray {
        val passwordBytes = password.flatMap { it.toByteArray() }.toByteArray()
        val saltBytes = salt.flatMap { it.toByteArray() }.toByteArray()

        // Use PBKDF2 with SHA-512 as the hash algorithm
        val keySpec = PBEKeySpec(
            passwordBytes.map { it.toChar() }.toCharArray(), // Convert password to CharArray
            saltBytes,
            iterations,
            keySize * 8 // The size in bits
        )
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val derivedKey = secretKeyFactory.generateSecret(keySpec).encoded

        return derivedKey
    }

    private fun Int.toByteArray(): List<Byte> {
        return listOf(
            (this shr 24 and 0xFF).toByte(),
            (this shr 16 and 0xFF).toByte(),
            (this shr 8 and 0xFF).toByte(),
            (this and 0xFF).toByte()
        )
    }
}

class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url).document.selectFirst("iframe")?.attr("src") ?:""
        Log.d("Phisher",href)
        val res= app.get(href, headers = mapOf("Accept-Language" to "en-US,en;q=0.5","sec-fetch-dest" to "iframe")).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        val m3u8= JsUnpacker(res).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)
        }
        Log.d("Phisher",m3u8.toString())

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