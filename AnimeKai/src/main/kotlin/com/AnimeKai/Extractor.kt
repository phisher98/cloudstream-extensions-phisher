package com.AnimeKai

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.cc"
    override val requiresReferer = true

    companion object {
        private val webViewMutex = Mutex()

        private val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-GPC" to "1",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "referer" to "https://megaup.cc"
        )

        private fun extractLabelFromUrl(url: String): String {
            val file = url.substringAfterLast("/")
            return when (val code = file.substringBefore("_").lowercase()) {
                "eng" -> "English"
                "ger", "deu" -> "German"
                "spa" -> "Spanish"
                "fre", "fra" -> "French"
                "ita" -> "Italian"
                "jpn" -> "Japanese"
                "chi", "zho" -> "Chinese"
                "kor" -> "Korean"
                "rus" -> "Russian"
                "ara" -> "Arabic"
                "hin" -> "Hindi"
                "por" -> "Portuguese"
                "vie" -> "Vietnamese"
                "pol" -> "Polish"
                "ukr" -> "Ukrainian"
                "swe" -> "Swedish"
                "ron", "rum" -> "Romanian"
                "ell", "gre" -> "Greek"
                "hun" -> "Hungarian"
                "fas", "per" -> "Persian"
                "tha" -> "Thai"
                else -> code.uppercase()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val jsToClickPlay = """
            (() => {
                const btn = document.querySelector('button, .vjs-big-play-button');
                if (btn) btn.click();
                return "clicked";
            })();
        """.trimIndent()

        // First: Get .m3u8
        val m3u8Resolver = WebViewResolver(
            interceptUrl = Regex("""\.m3u8"""),
            additionalUrls = listOf(Regex("""\.m3u8""")),
            script = jsToClickPlay,
            scriptCallback = { result -> Log.d("MegaUp", "JS Result: $result") },
            useOkhttp = false,
            timeout = 15_000L
        )

        val m3u8Response = webViewMutex.withLock {
            app.get(
                url = url,
                referer = mainUrl,
                headers = HEADERS,
                interceptor = m3u8Resolver
            )
        }

        val m3u8Url = m3u8Response.url

        if (m3u8Url.contains(".m3u8")) {
            val displayName = referer ?: this.name
            M3u8Helper.generateM3u8(displayName, m3u8Url, mainUrl, headers = HEADERS)
                .forEach(callback)
        } else {
            Log.e("MegaUp", "Failed to find .m3u8 for $url")
        }

        // Second: Get .vtt subtitles
        val subtitleUrls = mutableListOf<String>()

        webViewMutex.withLock {
            WebViewResolver(
                interceptUrl = Regex("""^$"""),
                additionalUrls = listOf(Regex(""".*\.vtt""")), // catch all .vtt files
                script = jsToClickPlay,
                scriptCallback = { result -> Log.d("MegaUp", "JS Result: $result") },
                useOkhttp = false,
                timeout = 15_000L,
                userAgent = null
            ).resolveUsingWebView(
                requestCreator("GET", url, headers = HEADERS)
            ) { request ->
                val interceptedUrl = request.url.toString()
                if (interceptedUrl.endsWith(".vtt") &&
                    !interceptedUrl.contains("thumbnails", ignoreCase = true)
                ) {
                    subtitleUrls.add(interceptedUrl)
                }
                false
            }
        }

        subtitleUrls.forEach { subUrl ->
            val label = extractLabelFromUrl(subUrl)
            subtitleCallback(SubtitleFile(label, subUrl))
        }

    }
}




