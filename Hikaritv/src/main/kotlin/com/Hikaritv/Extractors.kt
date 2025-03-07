package com.hikaritv

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
        val headers = mapOf(
            "priority" to "u=0, i",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        try {
            val res = app.get(url,referer=mainUrl,headers=headers).toString()

            val encodedString = Regex("(?:const|let|var|window\\.(?:Delta|Alpha|Ebolt))\\s+\\w*\\s*=\\s*'(.*?)'").find(res)?.groupValues?.get(1) ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Decrypt the encoded string
            val keyBase64 = "fnBmd19PVzRyfSFmdWV0ZQ=="
            val decryptedData = decryptData(keyBase64, encodedString)
            // Extract the m3u8 URL from decrypted data
            val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decryptedData)?.groupValues?.get(1)?.trim() ?: ""
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
        val regex = """\[([^]]+)](https?://[^\s,]+\.srt)""".toRegex()
        return regex.findAll(subtitle).map { match ->
            val (language, url) = match.destructured
            language.trim() to url.trim()
        }.toList()
    }

    private fun decryptData(base64Key: String, encryptedData: String): String {
        return try {
            // Decode Base64-encoded encrypted data
            val decodedBytes = base64DecodeArray(encryptedData)

            // Extract IV, Authentication Tag, and Ciphertext
            val iv = decodedBytes.copyOfRange(0, 12)
            val authTag = decodedBytes.copyOfRange(12, 28)
            val ciphertext = decodedBytes.copyOfRange(28, decodedBytes.size)

            // Convert Base64-encoded password to a SHA-256 encryption key
            val password = base64Decode(base64Key)
            val keyBytes =
                MessageDigest.getInstance("SHA-256")
                    .digest(password.toByteArray(Charset.forName("UTF-8")))

            // Decrypt the data using AES-GCM
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Perform decryption
            val decryptedBytes = cipher.doFinal(ciphertext + authTag)
            String(decryptedBytes, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            "Decryption failed"
        }
    }

    /** End **/
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