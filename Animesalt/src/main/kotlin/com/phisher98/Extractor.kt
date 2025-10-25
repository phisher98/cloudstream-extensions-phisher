package com.phisher98

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink

class Pixdrive : Filesim() {
    override var mainUrl = "https://pixdrive.cfd"
}

class Ghbrisk : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://ghbrisk.com"
    override val requiresReferer = true
}

class Zephyrflick : AWSStream() {
    override val name = "Zephyrflick"
    override val mainUrl = "https://play.zephyrflick.top"
    override val requiresReferer = true
}

class betaAwstream : AWSStream() {
    override val name = "AWSStream"
    override val mainUrl = "https://beta.awstream.net"
    override val requiresReferer = true
}

class Rapid : MegaPlay() {
    override val name = "Rapid"
    override val mainUrl = "https://rapid-cloud.co"
    override val requiresReferer = true
}

open class AWSStream : ExtractorApi() {
    override val name = "AWSStream"
    override val mainUrl = "https://z.awstream.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extractedHash = url.substringAfterLast("/")
        val doc = app.get(url).document
        val m3u8Url = "$mainUrl/player/index.php?data=$extractedHash&do=getVideo"
        val header = mapOf("x-requested-with" to "XMLHttpRequest")
        val formdata = mapOf("hash" to extractedHash, "r" to mainUrl)
        val response = app.post(m3u8Url, headers = header, data = formdata).parsedSafe<Response>()
        response?.videoSource?.let { m3u8 ->
            callback(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
            val extractedPack = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().orEmpty()

            JsUnpacker(extractedPack).unpack()?.let { unpacked ->
                Regex(""""kind":\s*"captions"\s*,\s*"file":\s*"(https.*?\.srt)""")
                    .find(unpacked)
                    ?.groupValues
                    ?.get(1)
                    ?.let { subtitleUrl ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                "English",
                                subtitleUrl
                            )
                        )
                    }
            }
        }
    }

    data class Response(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<Any?>,
        val attachmentLinks: List<Any?>,
        val ck: String,
    )
}

open class MegaPlay : ExtractorApi() {
    override val name = "MegaPlay"
    override val mainUrl = "https://megaplay.buzz"
    override val requiresReferer = false

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
            "Origin" to "https://rapid-cloud.co",
            "Referer" to "https://rapid-cloud.co/",
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

            val id = app.get(url, headers = headers).document.selectFirst("#megaplay-player")?.attr("data-id")

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
            Log.d("Phisher",encoded)
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
                interceptUrl = Regex("""master\.m3u8"""),
                additionalUrls = listOf(Regex("""master\.m3u8""")),
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

