package com.Anisaga

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class AnisagaStream : Chillx() {
    override val name = "Anisaga"
    override val mainUrl = "https://plyrxcdn.site"
}

// WASM? Well tried!
// Sorry this is more easy than custom encryption methods
// Hahahah!!!

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
            val res = app.get(url, referer = mainUrl, headers = headers).toString()

            val encodedString =
                Regex("(?:const|let|var|\\bwindow\\.\\w+)\\s+\\w*\\s*=\\s*'([^']*)'").find(
                    res
                )?.groupValues?.get(1)?.trim() ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Decrypt the encoded string
            val payload = """
             {
                "input": "$encodedString",
                "key": "ojl,&[y^-{cH!ux1"
             }
            """.trimIndent().toRequestBody("application/json".toMediaTypeOrNull())
            val decryptedData = app.post("https://interesting-zebra-51.deno.dev", requestBody  = payload, headers= mapOf("Content-Type" to "application/json")).text
            // Extract the m3u8 URL from decrypted data
            val m3u8 = Regex("(https?://[^\\s\"'\\\\]*m3u8[^\\s\"'\\\\]*)").find(decryptedData)?.groupValues?.get(1)?.trim() ?: ""
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
        val regex = """\[(.*?)](https?://[^\s,"]+\.srt)""".toRegex()
        val matches = regex.findAll(subtitle)

        return matches.map { match ->
            val label = match.groupValues[1]
            val file = match.groupValues[2]
            label to file
        }.toList()
    }
    
    /** End **/
}
