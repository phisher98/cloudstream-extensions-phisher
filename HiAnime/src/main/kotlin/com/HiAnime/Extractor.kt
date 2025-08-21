package com.HiAnime

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.M3u8Helper
import java.net.URLEncoder

class Megacloud : ExtractorApi() {
    override val name = "Megacloud"
    override val mainUrl = "https://megacloud.blog"
    override val requiresReferer = false

    @SuppressLint("NewApi")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // --- Primary API Method ---
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )

            val id = url.substringAfterLast("/").substringBefore("?")
            val responsenonce = app.get(url, headers = headers).text

            val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(responsenonce)
            val match2 = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""").find(responsenonce)
            val nonce = match1?.value ?: match2?.let { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }

            val apiUrl = "$mainUrl/embed-2/v3/e-1/getSources?id=$id&_k=$nonce"
            val gson = Gson()
            val response = try {
                val json = app.get(apiUrl, headers).text
                gson.fromJson(json, MegacloudResponse::class.java)
            } catch (e: Exception) {
                null
            }

            val encoded = response?.sources?.firstOrNull()?.file
                ?: throw Exception("No sources found")
            val key = try {
                val keyJson = app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json").text
                gson.fromJson(keyJson, Megakey::class.java)?.mega
            } catch (e: Exception) { null }

            val m3u8: String = if (".m3u8" in encoded) {
                encoded
            } else {
                val decodeUrl = "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"
                val fullUrl = "$decodeUrl?encrypted_data=${URLEncoder.encode(encoded, "UTF-8")}" +
                        "&nonce=${URLEncoder.encode(nonce, "UTF-8")}" +
                        "&secret=${URLEncoder.encode(key, "UTF-8")}"

                val decryptedResponse = app.get(fullUrl).text
                Regex("\"file\":\"(.*?)\"").find(decryptedResponse)?.groupValues?.get(1)
                    ?: throw Exception("Video URL not found in decrypted response")
            }

            val m3u8headers = mapOf("Referer" to "https://megacloud.club/", "Origin" to "https://megacloud.club/")
            M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = m3u8headers).forEach(callback)

            response.tracks.forEach { track ->
                if (track.kind == "captions" || track.kind == "subtitles") {
                    subtitleCallback(SubtitleFile(track.label, track.file))
                }
            }

        } catch (e: Exception) {
            // --- Fallback using WebViewResolver ---
            Log.e("Megacloud", "Primary method failed, using fallback: ${e.message}")

            val jsToClickPlay = """
                (() => {
                    const btn = document.querySelector('.jw-icon-display.jw-button-color.jw-reset');
                    if (btn) { btn.click(); return "clicked"; }
                    return "button not found";
                })();
            """.trimIndent()

            val m3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""\.m3u8"""),
                additionalUrls = listOf(Regex("""\.m3u8""")),
                script = jsToClickPlay,
                scriptCallback = { result -> Log.d("Megacloud", "JS Result: $result") },
                useOkhttp = false,
                timeout = 15_000L
            )

            val vttResolver = WebViewResolver(
                interceptUrl = Regex("""\.vtt"""),
                additionalUrls = listOf(Regex("""\.vtt""")),
                script = jsToClickPlay,
                scriptCallback = { result -> Log.d("Megacloud", "Subtitle JS Result: $result") },
                useOkhttp = false,
                timeout = 15_000L
            )

            try {
                val vttResponse = app.get(url = url, referer = mainUrl, interceptor = vttResolver)
                val subtitleUrls = listOf(vttResponse.url)
                    .filter { it.endsWith(".vtt") && !it.contains("thumbnails", ignoreCase = true) }
                subtitleUrls.forEachIndexed { _, subUrl ->
                    subtitleCallback(SubtitleFile("English", subUrl))
                }

                val fallbackM3u8 = app.get(url = url, referer = mainUrl, interceptor = m3u8Resolver).url
                val fallbackHeaders = mapOf("Referer" to "https://megacloud.club/", "Origin" to "https://megacloud.club/")
                M3u8Helper.generateM3u8(name, fallbackM3u8, mainUrl, headers = fallbackHeaders).forEach(callback)

            } catch (ex: Exception) {
                Log.e("Megacloud", "Fallback also failed: ${ex.message}")
            }
        }
    }

    data class MegacloudResponse(
        val sources: List<Source>,
        val tracks: List<Track>,
        val encrypted: Boolean,
        val intro: Intro,
        val outro: Outro,
        val server: Long
    )

    data class Source(
        val file: String,
        val type: String
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val default: Boolean? = null
    )

    data class Intro(val start: Long, val end: Long)
    data class Outro(val start: Long, val end: Long)
    data class Megakey(val rabbit: String, val mega: String)
}
