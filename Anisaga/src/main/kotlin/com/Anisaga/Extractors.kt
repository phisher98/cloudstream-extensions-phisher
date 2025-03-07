package com.Anisaga

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** 
New Imports
**/
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.MessageDigest

class AnisagaStream : Chillx() {
    override val name = "Anisaga"
    override val mainUrl = "https://plyrxcdn.site"
}

// Well done! 👏 This time, you really implemented some top-tier security.  
// But unfortunately for you, I’ve already broken through.  
// AES-GCM? 😂 Nice attempt!  
// Am I late? Yeah, but only because I was busy with other projects.

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
