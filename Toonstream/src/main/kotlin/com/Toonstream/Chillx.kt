package com.Toonstream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
        try {
            // Fetch the raw response from the URL
            val res = app.get(url).toString() ?: throw Exception("Failed to fetch URL")

            // Extract the encoded string using regex
            val encodedString = Regex("const\\sMatrixs\\s=\\s'(.*?)';").find(res)?.groupValues?.get(1) ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }
            // Decrypt the encoded string
            val password = "Fvv0O(0ep+X,q-Z+"
            val decryptedData = decryptAES(encodedString, password)
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



    private fun decryptAES(encryptedData: String, password: String): String {
        try {
            // Decode Base64-encoded input
            val decodedBytes = base64DecodeArray(encryptedData)

            // Convert bytes to 32-bit words (similar to bytes_to_32bit_words in Python)
            val resultWords = bytesTo32BitWords(decodedBytes)

            // Extract IV (first 16 bytes, 4 words)
            val ivBytes = ByteBuffer.allocate(16).apply {
                for (i in 0 until 4) putInt(resultWords[i])
            }.array()

            // Generate the key using SHA-256 hash of the password
            val key = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())

            // Initialize AES Cipher in CBC mode
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivBytes))

            // Extract ciphertext
            val cipherText = ByteBuffer.allocate((resultWords.size - 4) * 4).apply {
                for (i in 4 until resultWords.size) putInt(resultWords[i])
            }.array()

            // Decrypt and return the plaintext
            return String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return "Decryption Failed"
        }
    }

    // Convert byte array to 32-bit word array
    private fun bytesTo32BitWords(byteData: ByteArray): IntArray {
        val words = mutableListOf<Int>()
        for (i in byteData.indices step 4) {
            var word = 0
            for (j in 0 until 4) {
                if (i + j < byteData.size) {
                    word = word or (byteData[i + j].toInt() and 0xFF shl (24 - j * 8))
                }
            }
            words.add(word)
        }
        return words.toIntArray()
    }
}