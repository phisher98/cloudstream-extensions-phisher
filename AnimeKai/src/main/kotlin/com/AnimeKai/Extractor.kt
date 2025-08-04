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
            "Accept" to "text/html, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-GPC" to "1",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Priority" to "u=0",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "referer" to "https://animekai.to/"
        )

        private fun extractLabelFromUrl(url: String): String {
            val file = url.substringAfterLast("/")
            return when (val code = file.substringBefore("_").lowercase()) {
                "eng" -> "English"
                "ger" -> "German"
                "spa" -> "Spanish"
                "fre" -> "French"
                "ita" -> "Italian"
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
            Log.d("MegaUp", "Found m3u8: $m3u8Url")
            val displayName = referer ?: this.name
            M3u8Helper.generateM3u8(displayName, m3u8Url, mainUrl, headers = HEADERS)
                .forEach(callback)
        } else {
            Log.e("MegaUp", "Failed to find .m3u8 for $url")
        }

        // Second: Get .vtt subtitles
        val vttResolver = WebViewResolver(
            interceptUrl = Regex("""eng_\d+\.vtt"""),
            additionalUrls = listOf(Regex("""eng_\d+\.vtt""")),
            script = jsToClickPlay,
            scriptCallback = { result -> Log.d("MegaUp", "JS Result: $result") },
            useOkhttp = false,
            timeout = 15_000L
        )

        val vttResponse = webViewMutex.withLock {
            app.get(
                url = url,
                referer = mainUrl,
                headers = HEADERS,
                interceptor = vttResolver
            )
        }

        val subtitleUrls = buildList {
            if (vttResponse.url.endsWith(".vtt") && !vttResponse.url.contains("thumbnails", ignoreCase = true)) {
                add(vttResponse.url)
            }
        }.distinct()

        subtitleUrls.forEach { subUrl ->
            val label = extractLabelFromUrl(subUrl)
            subtitleCallback(SubtitleFile(label, subUrl))
        }
    }
}




