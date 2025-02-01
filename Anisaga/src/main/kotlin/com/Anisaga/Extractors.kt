package com.Anisaga


import android.annotation.TargetApi
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.nio.ByteBuffer
import java.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

class AnisagaStream : Chillx() {
    override val name = "Anisaga"
    override val mainUrl = "https://plyrxcdn.site"
}

// Why are so mad at us Cracking it
open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Fetch the raw response from the URL
            val res = app.get(url).toString() ?: throw Exception("Failed to fetch URL")
            println("Response: $res")  // Debugging the raw HTML response

            // Extract the encoded string using regex
            val encodedString = Regex("const\\s+Matrix\\s*=\\s*'(.*?)'").find(res)?.groupValues?.get(1) ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }
            println("Encoded String: $encodedString")  // Debugging the extracted string

            // Decrypt the encoded string
            val password = "0-4_xSb3ikmo]&v%D,&7"
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            val decryptedData = decryptData(encodedString, password, userAgent)
            println("Decrypted Data: $decryptedData")  // Debugging decrypted data

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
                "user-agent" to userAgent
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun decryptData(encodedString: String, password: String, userAgent: String): String {
        // Decode the Base64 encoded string
        val decodedBytes = Base64.getDecoder().decode(encodedString)

        // Convert decoded bytes to 32-bit words
        val result = bytesTo32BitWords(decodedBytes)

        // Extract the first 4 words as the IV
        val iv = result.take(4)

        // Convert each word to bytes using Big Endian
        val ivBytes = iv.flatMap {
            ByteBuffer.allocate(4).putInt(it).array().toList()
        }.toByteArray()

        // Generate the dynamic password
        val dynamicPassword = "$password$userAgent"

        // Generate the key using SHA-256 hash of the dynamic password
        val key = generateKey(dynamicPassword)

        // Initialize the AES cipher in CBC mode
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

        // Extract the ciphertext (after the first 4 words)
        val cipherText = result.drop(4).flatMap { it.toByteArray().toList() }.toByteArray()

        // Decrypt and unpad the plaintext
        val decryptedDataBytes = cipher.doFinal(cipherText)
        return String(decryptedDataBytes) // Decoding as UTF-8 string
    }

    fun generateKey(password: String): SecretKeySpec {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(password.toByteArray())
        return SecretKeySpec(keyBytes, "AES")
    }

    fun bytesTo32BitWords(byteData: ByteArray): List<Int> {
        val words = mutableListOf<Int>()
        var i = 0
        while (i < byteData.size) {
            var word = 0
            for (j in 0 until 4) {
                if (i + j < byteData.size) {
                    word = word or (byteData[i + j].toInt() shl (24 - j * 8))
                }
            }
            words.add(word)
            i += 4
        }
        return words
    }

    fun Int.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24 and 0xFF).toByte(),
            (this shr 16 and 0xFF).toByte(),
            (this shr 8 and 0xFF).toByte(),
            (this and 0xFF).toByte()
        )
    }
}
