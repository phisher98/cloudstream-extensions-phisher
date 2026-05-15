// Adapted for CloudStream - taken from https://github.com/vargalex/ResolveURL/blob/fix/videa-resolver-add-cookie/script.module.resolveurl/lib/resolveurl/plugins/videa.py
package com.kayifamilytv

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.base64DecodeArray

/**
 * Extractor for Videa.hu video hosting service
 * Handles encrypted XML responses and redirect chains
 */
class Videa : ExtractorApi() {
    override val name = "Videa"
    override val mainUrl = "https://videa.hu"
    override val requiresReferer = false

    private val videaSecret = "xHb0ZvME5q8CBcoQi6AngerDu3FGO9fkUlwPmLVY_RTzj2hJIS4NasXWKy1td7p"

    // --- DEBUG FLAG ---
    // Set to true to enable detailed logging, false to disable
    private val DEBUG_MODE = false
    private val TAG = "VideaExtractor"

    private fun dLog(message: String) {
        if (DEBUG_MODE) {
            Log.d(TAG, message)
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        dLog("Starting getUrl() with url: $url")
        var currentUrl = url

        // Handle redirect loop until we get valid XML
        while (true) {
            dLog("Loop iteration with currentUrl: $currentUrl")

            // FIX: Expect a Pair back from getXmlUrl
            val xmlInfo = getXmlUrl(currentUrl) { _ -> /* no-op */ }

            if (xmlInfo == null) {
                dLog("getXmlUrl() returned null, aborting extraction.")
                return
            }

            val webUrl = xmlInfo.first
            val baseKey = xmlInfo.second // <-- This is the key we were previously losing

            dLog("Generated webUrl: $webUrl")

            val response = app.get(webUrl)
            val rawBytes = response.body.bytes()

            val isXml = rawBytes.size >= 5 &&
                    rawBytes[0] == 0x3C.toByte() &&
                    rawBytes[1] == 0x3F.toByte() &&
                    rawBytes[2] == 0x78.toByte() &&
                    rawBytes[3] == 0x6D.toByte() &&
                    rawBytes[4] == 0x6C.toByte()

            val videaXml = if (isXml) {
                String(rawBytes, Charsets.UTF_8)
            } else {
                val xsHeader = response.headers["X-Videa-Xs"]

                if (xsHeader == null) {
                    dLog("CRITICAL: X-Videa-Xs header missing from response. Aborting.")
                    return
                }

                // FIX: Combine the generated base key with the header
                val finalKey = baseKey + xsHeader
                dLog("Decrypting with final key length: ${finalKey.length} (Base: ${baseKey.length} + Header: ${xsHeader.length})")

                val decrypted = rc4DecryptBytes(rawBytes, finalKey)
                dLog("Decrypted XML (first 100 chars): ${decrypted.take(100)}...")
                decrypted
            }

            // ... (keep the rest of the redirect checking and parseVideoSources the same) ...
            val redirectMatch = """<error.*?"noembed".*>(.*)</error>""".toRegex().find(videaXml)

            if (redirectMatch != null && redirectMatch.groupValues[1] != currentUrl) {
                dLog("Found redirect in XML error. Redirecting to: ${redirectMatch.groupValues[1]}")
                currentUrl = redirectMatch.groupValues[1]
            } else {
                dLog("No redirect found, proceeding to parse video sources.")
                parseVideoSources(videaXml, callback)
                break
            }
        }
    }

    private suspend fun getXmlUrl(url: String, cookieCallback: (String) -> Unit = {}): Pair<String, String>? {
        dLog("getXmlUrl() called with url: $url")
        val response = app.get(url)
        val html = response.text

        dLog("Fetched initial HTML, length: ${html.length}")

        // Extract sl cookie if present
        response.headers["Set-Cookie"]?.let { cookieHeader ->
            """sl=([^;]+)""".toRegex().find(cookieHeader)?.let {
                dLog("Found 'sl' cookie in initial response: ${it.value}")
                cookieCallback(it.value)
            }
        }

        // Determine if this is a player URL or needs iframe extraction
        val playerUrl = if ("/player" in url) {
            dLog("URL already contains '/player'")
            url
        } else {
            val iframeMatch = """<iframe.*?src="(/player\?[^"]+)""".toRegex().find(html)
            iframeMatch?.let {
                val parsedIframe = "$mainUrl${it.groupValues[1]}"
                dLog("Extracted iframe src: $parsedIframe")
                parsedIframe
            } ?: run {
                dLog("Failed to find iframe src in HTML using regex.")
                return null
            }
        }

        // Get player page to extract tokens
        dLog("Fetching player HTML from: $playerUrl")
        val playerResponse = app.get(playerUrl)
        val playerHtml = playerResponse.text
        dLog("Fetched player HTML, length: ${playerHtml.length}")

        // Update cookie from player response
        playerResponse.headers["Set-Cookie"]?.let { cookieHeader ->
            """sl=([^;]+)""".toRegex().find(cookieHeader)?.let {
                dLog("Found 'sl' cookie in player response: ${it.value}")
                cookieCallback(it.value)
            }
        }

        // Extract nonce and generate tokens
        val nonceMatch = """_xt\s*=\s*"([^"]+)""".toRegex().find(playerHtml)
        if (nonceMatch == null) {
            dLog("Failed to find _xt nonce in player HTML!")
            return null
        }

        val nonce = nonceMatch.groupValues[1]
        dLog("Found nonce (_xt): $nonce")
        // FIX: Capture the generatedKey instead of discarding it with '_'
        val (s, t, generatedKey) = generateTokens(nonce)
        dLog("Generated tokens -> _s: $s, _t: $t, base_key_length: ${generatedKey.length}")

        val videoParam = when {
            "f=" in playerUrl -> "f=" + playerUrl.substringAfter("f=").substringBefore("&")
            "v=" in playerUrl -> "v=" + playerUrl.substringAfter("v=").substringBefore("&")
            else -> return null
        }

        val finalXmlUrl = "$mainUrl/player/xml?platform=desktop&$videoParam&_s=$s&_t=$t"
        dLog("Final XML URL constructed: $finalXmlUrl")
        return Pair(finalXmlUrl, generatedKey)
    }

    private fun generateTokens(nonce: String): Triple<String, String, String> {
        val lo = nonce.take(32)
        val s = nonce.substring(32)
        var result = ""

        for (i in 0 until 32) {
            val index = videaSecret.indexOf(lo[i]) - 31
            // Adding a safety check in case the character isn't found
            if (index + 31 == -1) {
                dLog("WARNING: Character '${lo[i]}' not found in videaSecret!")
            }
            result += s[i - index]
        }

        // Generate random seed
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomSeed = (1..8).map { chars.random() }.joinToString("")

        val key = result.substring(16) + randomSeed
        return Triple(randomSeed, result.take(16), key)
    }

    private suspend fun parseVideoSources(xml: String, callback: (ExtractorLink) -> Unit) {
        dLog("parseVideoSources() called, XML length: ${xml.length}")
        val sourceRegex = """video_source\s*name="([^"]+)".*exp="([^"]+)"[^>]*>([^<]+)""".toRegex()
        val sources = sourceRegex.findAll(xml).toList()

        dLog("Found ${sources.size} video sources in XML.")

        for (sourceMatch in sources) {
            val sourceName = sourceMatch.groupValues[1]
            val exp = sourceMatch.groupValues[2]
            var sourceUrl = sourceMatch.groupValues[3]

            dLog("Processing source: $sourceName, Exp: $exp, URL: $sourceUrl")

            // Add https if needed
            if (sourceUrl.startsWith("//")) {
                sourceUrl = "https:$sourceUrl"
            }

            // Extract hash for this source
            val hashMatch = """<hash_value_$sourceName>([^<]+)<""".toRegex().find(xml)

            if (hashMatch != null) {
                val hash = hashMatch.groupValues[1]
                val finalUrl = "$sourceUrl?md5=$hash&expires=$exp".replace("&amp;", "&")

                dLog("Successfully constructed final URL for $sourceName: $finalUrl")

                callback(
                    newExtractorLink(
                        name,
                        "$sourceName - $name",
                        finalUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.referer = mainUrl
                    }
                )
            } else {
                dLog("Failed to find hash_value for source: $sourceName")
            }
        }
    }

    private fun rc4DecryptBytes(encryptedBytes: ByteArray, key: String): String {
        dLog("rc4DecryptBytes() called. Key length: ${key.length}")

        // Check if data is Base64 encoded
        val isBase64 = encryptedBytes.all { byte ->
            val char = byte.toInt() and 0xFF
            char in 32..126 || char == 10 || char == 13
        }

        dLog("Is data Base64 encoded? $isBase64")

        val actualEncryptedBytes = if (isBase64) {
            val base64String = String(encryptedBytes, Charsets.UTF_8)
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .trim()
            base64DecodeArray(base64String)
        } else {
            encryptedBytes
        }

        val keyBytes = key.toByteArray(Charsets.UTF_8)

        // RC4 key-scheduling algorithm (KSA)
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0..255) {
            j = (j + s[i] + (keyBytes[i % keyBytes.size].toInt() and 0xFF)) % 256
            s[i] = s[j].also { s[j] = s[i] }
        }

        // RC4 pseudo-random generation algorithm (PRGA)
        var i = 0
        j = 0
        val result = ByteArray(actualEncryptedBytes.size)
        for (k in actualEncryptedBytes.indices) {
            i = (i + 1) % 256
            j = (j + s[i]) % 256
            s[i] = s[j].also { s[j] = s[i] }
            val keyStreamByte = s[(s[i] + s[j]) % 256]
            result[k] = ((actualEncryptedBytes[k].toInt() and 0xFF) xor keyStreamByte).toByte()
        }

        return String(result, Charsets.UTF_8)
    }
}
