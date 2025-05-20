package com.Anisaga

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AnisagaStream : Chillx() {
    override val name = "Anisaga"
    override val mainUrl = "https://plyrxcdn.site"
}

// Original Code: https://github.com/yogesh-hacker/MediaVanced/blob/main/sites/vidstream.py
// @PlayerX, Yes, I will never give up!
// 27th attempt, I love you for trying though :) 


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
        val baseurl=getBaseUrl(url)
        val headers = mapOf(
            "Origin" to baseurl,
            "Referer" to baseurl,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"
        )

        try {
            val res = app.get(url, referer = referer ?: mainUrl, headers = headers).toString()

            // Extract encoded string from response
            val encodedString = Regex("(?:const|let|var|window\\.\\w+)\\s+\\w*\\s*=\\s*'([^']{30,})'").find(res)
                ?.groupValues?.get(1)?.trim() ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Get Password from pastebin(Shareable, Auto-Update)
            val keyUrl = "https://pastebin.com/dl/DCmJyUSi"
            val passwordHex = app.get(keyUrl, headers = mapOf("Referer" to "https://pastebin.com/")).text
            val password = passwordHex.chunked(2).map { it.toInt(16).toChar() }.joinToString("")

            // Decrypt using password String
            val decryptedData = decryptAESGCM(encodedString, password)
                ?: throw Exception("Decryption failed")

            // Extract m3u8 URL
            val m3u8 = Regex("(https?://[^\\s\"'\\\\]*m3u8[^\\s\"'\\\\]*)").find(decryptedData)
                ?.groupValues?.get(1)?.trim() ?: ""
            if (m3u8.isEmpty()) {
                throw Exception("m3u8 URL not found")
            }

            // Prepare headers for callback
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
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                    this.headers = header
                }
            )

            // Extract and return subtitles
            val subtitles = extractSrtSubtitles(decryptedData)
            subtitles.forEachIndexed { _, (language, url) ->
                subtitleCallback.invoke(SubtitleFile(language, url))
            }

        } catch (e: Exception) {
            Log.e("Anisaga Stream", "Error: ${e.message}")
        }
    }

    @SuppressLint("NewApi")
    fun decryptAESGCM(encryptedData: String, password: String): String? {
        try {
            // Base64 decode the encrypted data
            val decodedBytes = Base64.getDecoder().decode(encryptedData)

            // Extract IV (first 12 bytes) and Encrypted Bytes (remaining bytes)
            val ivBytes = decodedBytes.copyOfRange(0, 12)
            val encryptedBytes = decodedBytes.copyOfRange(12, decodedBytes.size)
            
            // Prepare key
            val keyBytes = password.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, ivBytes)
            
            // Decrypt using AES-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            // Add AAD(Additional Data)
            cipher.updateAAD("NeverGiveUp".toByteArray(Charsets.UTF_8))

            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)

        } catch (e: BadPaddingException) {
            println("Decryption failed: Bad padding or incorrect password.")
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """\[(.*?)](https?://[^\s,"]+\.srt)""".toRegex()
        return regex.findAll(subtitle).map {
            it.groupValues[1] to it.groupValues[2]
        }.toList()
    }
    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

}
