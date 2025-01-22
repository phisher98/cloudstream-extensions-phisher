package com.Anisaga


import android.annotation.SuppressLint
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import java.util.zip.Inflater

class AnisagaStream : Chillx() {
    override val name = "Anisaga"
    override val mainUrl = "https://plyrxcdn.site"
}

open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true
    private var key: String? = null

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).toString()
        val encodedString =
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1) ?:""
        Log.d("Phisher",encodedString)
        val decoded = decodeEncryptedData(encodedString)
        val m3u8 =Regex("file:\\s*\"(.*?)\"").find(decoded)?.groupValues?.get(1) ?:""
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

        val subtitles = extractSrtSubtitles(decoded)
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

    fun decodeEncryptedData(encryptedString: String): String? {
     try {
        // Base64 decode
        val decodedBytes = Base64.getDecoder().decode(encryptedString)

        // Reverse binary and decode characters
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
        val finalDecodedData = Base64.getDecoder().decode(processedData).toString(Charsets.UTF_8)
        return finalDecodedData
     } catch (e: Exception) {
        println("Error decoding string: ${e.message}")
        return null
     }
    }

}