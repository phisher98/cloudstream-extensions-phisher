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
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AnisagaStream : Chillx() {
    override val name = "Anisaga"
    override val mainUrl = "https://plyrxcdn.site"
}

// @Chillx, did you really think I spared (left) you?
// No, I had exams, that's why! 😁 But now, I'm back for you!
// Using modules? That's good, but nothing can stop me.

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

            val encodedString = Regex("(?:const|let|var|window\\.(?:Delta|Alpha))\\s+\\w*\\s*=\\s*'(.*?)'").find(res)?.groupValues?.get(1) ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Decrypt the encoded string
            val keyBase64 = "ZmJlYTcyMGU5MDY0NDE3Mzg1MDc0MjMzOThiYTcwMjg5ZTQwNjJmZTU2NGFhNTU5OTY5OWZhNjA2NDVmNzdjZA=="
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
        // Method: AES (CBC)
        return try {
            val keyHex = base64Decode(base64Key)
            val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            val bytesData = base64DecodeArray(encryptedData)

            // Extract the IV and ciphertext
            val iv = bytesData.copyOfRange(0, 16)
            val ciphertext = bytesData.copyOfRange(16, bytesData.size)

            // Initialize the cipher for AES CBC decryption
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))

            // Perform decryption and return the result as a string
            String(cipher.doFinal(ciphertext), Charset.forName("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            "Decryption failed"
        }
    }

}
