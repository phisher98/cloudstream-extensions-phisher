package com.Toonstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.security.MessageDigest

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
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1)?.replace("_", "/")
                ?.replace("-", "+")
                ?: ""
        val fetchkey = fetchKey() ?: throw ErrorLoadingException("Unable to get key")
        val decoded = decodeWithKey(encodedString, fetchkey)
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

    fun reverseString(input: String): String {
        return input.reversed()
    }

    fun decodeWithKey(input: String, key: String): String {
        // Hashing the key using SHA1 and encoding it in hex
        val sha1 = MessageDigest.getInstance("SHA-1")
        val keyHash = sha1.digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        println(keyHash)

        val inputLength = input.length
        val keyHashLength = keyHash.length
        var keyIndex = 0
        var decodedString = ""

        for (index in 0 until inputLength step 2) {
            // Extracting two characters from input, applying reverseString, converting to base 36, then back to hex
            val reversedPair = reverseString(input.substring(index, index + 2))
            val base36Value = Integer.parseInt(reversedPair, 36)
            val hexValue = base36Value.toString(16)

            // Reset keyIndex when it exceeds keyHashLength
            if (keyIndex == keyHashLength) {
                keyIndex = 0
            }

            // Get the char code of the current character in keyHash
            val keyCharCode = keyHash[keyIndex].code
            keyIndex++

            // Subtracting keyCharCode from the hex value and appending it to the result string
            decodedString += (Integer.parseInt(hexValue, 16) - keyCharCode).toChar()
        }

        // Return the decoded string
        return String(decodedString.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
    }

    private suspend fun fetchKey(): String? {
        return app.get("https://raw.githubusercontent.com/Rowdy-Avocado/multi-keys/refs/heads/keys/index.html")
            .parsedSafe<Keys>()?.key?.get(0)?.also { key = it }
    }
    data class Keys(
        @JsonProperty("chillx") val key: List<String>)
}