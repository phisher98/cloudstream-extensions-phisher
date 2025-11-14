package com.allwish

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class MegaPlay : ExtractorApi() {
        override val name = "MegaPlay"
        override val mainUrl = "https://megaplay.buzz"
        override val requiresReferer = false

        @SuppressLint("NewApi")
        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            val mainheaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Accept-Encoding" to "gzip, deflate, br, zstd",
                "Origin" to "https://megaplay.buzz",
                "Referer" to "https://megaplay.buzz/",
                "Connection" to "keep-alive",
                "Pragma" to "no-cache",
                "Cache-Control" to "no-cache"
            )

            try {
                // --- Primary API Method ---
                val headers = mapOf(
                    "Accept" to "*/*",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to mainUrl
                )

                val id = app.get(url, headers = headers).documentLarge.selectFirst("#megaplay-player")?.attr("data-id")

                val apiUrl = "$mainUrl/stream/getSources?id=$id&id=$id"
                val gson = Gson()
                val response = try {
                    val json = app.get(apiUrl, headers).text
                    gson.fromJson(json, MegaPlay::class.java)
                } catch (_: Exception) {
                    null
                }

                val encoded = response?.sources?.file
                    ?: throw Exception("No sources found")

                val m3u8: String = encoded

                M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = mainheaders).forEach(callback)

                response.tracks.forEach { track ->
                    if (track.kind == "captions" || track.kind == "subtitles") {
                        subtitleCallback(newSubtitleFile(track.label, track.file))
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
                        subtitleCallback(newSubtitleFile("English", subUrl))
                    }

                    val fallbackM3u8 = app.get(url = url, referer = mainUrl, interceptor = m3u8Resolver).url
                    M3u8Helper.generateM3u8(name, fallbackM3u8, mainUrl, headers = mainheaders).forEach(callback)

                } catch (ex: Exception) {
                    Log.e("Megacloud", "Fallback also failed: ${ex.message}")
                }
            }
        }

        data class MegaPlay(
            val sources: Sources,
            val tracks: List<Track>,
            val t: Long,
            val intro: Intro,
            val outro: Outro,
            val server: Long,
        )

        data class Sources(
            val file: String,
        )

        data class Track(
            val file: String,
            val label: String,
            val kind: String,
            val default: Boolean?,
        )

        data class Intro(
            val start: Long,
            val end: Long,
        )

        data class Outro(
            val start: Long,
            val end: Long,
        )
    }

    class Zen : ExtractorApi() {
        override val name = "Zen"
        override val mainUrl = "https://player.sgsgsgsr.site"
        override val requiresReferer = false

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            val res = app.get(url).documentLarge
            val script = res.selectFirst("script[type=module]")?.data() ?: return

            val videoB64 = Regex("video_b64:\\s*\"([^\"]+)\"").find(script)?.groupValues?.get(1) ?: return
            val keyB64 = Regex("enc_key_b64:\\s*\"([^\"]+)\"").find(script)?.groupValues?.get(1) ?: return
            val ivB64 = Regex("iv_b64:\\s*\"([^\"]+)\"").find(script)?.groupValues?.get(1) ?: return

            val decryptedUrl = decryptVideoUrl(videoB64, keyB64, ivB64)
            if (decryptedUrl.isNullOrBlank()) return

            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    decryptedUrl.trim(),
                    ExtractorLinkType.M3U8
                )
                {
                    this.quality = Qualities.P1080.value
                }
            )

            val regex = Regex("subtitles:\\s\"(.*)\"")
            val match = regex.find(script)
            match?.groupValues?.get(1)?.let { rawSubs ->
                val jsonString = rawSubs.replace("\\\"", "\"")
                    .replace("\\\\/", "/")

                val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                val subsNode = mapper.readTree(jsonString)

                subsNode.forEach { sub ->
                    val subUrl = sub["url"]?.asText() ?: return@forEach
                    val lang = sub["language"]?.asText() ?: "Unknown"
                    val format = sub["format"]?.asText() ?: "srt"

                    if (format.equals("ass", ignoreCase = true)) {
                        subtitleCallback(newSubtitleFile(lang, subUrl))
                    }
                }
            }

        }

        private fun decryptVideoUrl(videoB64: String, keyB64: String, ivB64: String): String? {
            return try {
                val encryptedData = base64DecodeArray(videoB64)
                val keyBytes = base64DecodeArray(keyB64)
                val ivBytes = base64DecodeArray(ivB64)

                val secretKey = SecretKeySpec(keyBytes, "AES")
                val ivSpec = IvParameterSpec(ivBytes)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
                val decryptedBytes = cipher.doFinal(encryptedData)
                String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
