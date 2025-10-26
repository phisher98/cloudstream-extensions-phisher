package com.HiAnime

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class Megacloud : ExtractorApi() {
    override val name = "Megacloud"
    override val mainUrl = "https://megacloud.blog"
    override val requiresReferer = false

    private val client = OkHttpClient()
    private val gson = Gson()

    private fun fetchUrl(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        } catch (e: Exception) {
            Log.e("Megacloud", "Network request failed: ${e.localizedMessage}")
            null
        }
    }

    @SuppressLint("NewApi")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "Connection" to "keep-alive",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )

        try {
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )

            val id = url.substringAfterLast("/").substringBefore("?")
            val responseText = fetchUrl(url, headers) ?: throw Exception("Failed to fetch page")

            val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(responseText)
            val match2 = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""").find(responseText)
            val nonce = match1?.value ?: match2?.let { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            ?: throw Exception("Nonce not found")

            val apiUrl = "$mainUrl/embed-2/v3/e-1/getSources?id=$id&_k=$nonce"
            val responseJson = fetchUrl(apiUrl, headers) ?: throw Exception("Failed to fetch sources")
            val response = gson.fromJson(responseJson, MegacloudResponse::class.java)

            val encoded = response.sources.firstOrNull()?.file ?: throw Exception("No sources found")

            val keyJson = fetchUrl("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json")
            val key = keyJson?.let { gson.fromJson(it, Megakey::class.java)?.mega }

            val m3u8: String = if (encoded.contains(".m3u8")) {
                encoded
            } else {
                val decodeUrl =
                    "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"
                val fullUrl =
                    "$decodeUrl?encrypted_data=${URLEncoder.encode(encoded, "UTF-8")}" +
                            "&nonce=${URLEncoder.encode(nonce, "UTF-8")}" +
                            "&secret=${URLEncoder.encode(key ?: "", "UTF-8")}"

                val decryptedResponse = fetchUrl(fullUrl) ?: throw Exception("Failed to decrypt URL")
                Regex("\"file\":\"(.*?)\"").find(decryptedResponse)?.groupValues?.get(1)
                    ?: throw Exception("Video URL not found in decrypted response")
            }

            M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = mainHeaders).forEach(callback)

            response.tracks.forEach { track ->
                if (track.kind == "captions" || track.kind == "subtitles") {
                    subtitleCallback(newSubtitleFile(track.label, track.file))
                }
            }

        } catch (e: Exception) {
            Log.e("Megacloud", "Primary method failed: ${e.message}")
            // Optionally: fallback with WebViewResolver as in original code
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

    data class Source(val file: String, val type: String)
    data class Track(val file: String, val label: String, val kind: String, val default: Boolean? = null)
    data class Intro(val start: Long, val end: Long)
    data class Outro(val start: Long, val end: Long)
    data class Megakey(val rabbit: String, val mega: String)
}