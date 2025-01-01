package com.Toonstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.security.MessageDigest
import java.util.Base64

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
        val m3u8 = Regex("sources:\\s*\\[\\s*\\{\\s*\"file\"\\s*:\\s*\"([^\"]+)").find(decoded)?.groupValues?.get(1)
            ?.trim()
            ?:""
        Log.d("Phisher","$decoded $m3u8")

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

    private fun logSha256Checksum(input: String): List<Int> {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val sha256Hash = messageDigest.digest(input.toByteArray())
        val unsignedIntArray = sha256Hash.map { it.toInt() and 0xFF }
        return unsignedIntArray
    }

    private fun decodeBase64WithPadding(xIdJ2lG: String): ByteArray {
        // Ensure padding for Base64 encoding (if necessary)
        var paddedString = xIdJ2lG
        while (paddedString.length % 4 != 0) {
            paddedString += '=' // Add necessary padding
        }

        // Decode using standard Base64 (RFC4648)
        return Base64.getDecoder().decode(paddedString)
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        // Regex to match the language and associated .srt URL properly, and stop at the next [Language] section
        val regex = """\[([^]]+)](https?://[^\s,]+\.srt)""".toRegex()

        // Process each match and return language-URL pairs
        return regex.findAll(subtitle).map { match ->
            val (language, url) = match.destructured
            language.trim() to url.trim()
        }.toList()
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