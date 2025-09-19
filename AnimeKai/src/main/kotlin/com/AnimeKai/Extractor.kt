package com.AnimeKai

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.api.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.live"
    override val requiresReferer = true

    companion object {
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

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val foundM3u8 = mutableSetOf<String>()
        withContext(Dispatchers.Main) {
            val ctx = context ?: return@withContext
            suspendCancellableCoroutine { cont ->
                var finished = false

                val webView = WebView(ctx)

                webView.apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true

                    webViewClient = object : WebViewClient() {
                        private var lastUrlTime = System.currentTimeMillis()
                        private val finishDelay = 2000L

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url.toString()
                            Log.d("MegaUp", "Intercepted: $reqUrl")
                            when {
                                reqUrl.endsWith(".m3u8") -> {
                                    if (foundM3u8.add(reqUrl)) {
                                        lastUrlTime = System.currentTimeMillis()
                                    }
                                }
                                reqUrl.endsWith(".vtt") && !reqUrl.contains("thumbnails", true) -> {
                                    lastUrlTime = System.currentTimeMillis()
                                    subtitleCallback(SubtitleFile(extractLabelFromUrl(reqUrl), reqUrl))
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            val jsToClickPlay = """
                        (() => {
                            const btn = document.querySelector('button, .vjs-big-play-button');
                            if (btn) btn.click();
                        })();
                    """.trimIndent()
                            view?.evaluateJavascript(jsToClickPlay, null)

                            val handler = Handler(Looper.getMainLooper())
                            handler.post(object : Runnable {
                                override fun run() {
                                    if (finished) return
                                    if (System.currentTimeMillis() - lastUrlTime > finishDelay) {
                                        finished = true
                                        Log.d("MegaUp", "All links loaded, finishing")
                                        cont.resume(Unit)
                                        webView.destroy()
                                    } else {
                                        handler.postDelayed(this, 500)
                                    }
                                }
                            })
                        }
                    }
                }

                val html = """
            <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                </head>
                <body style="margin:0;padding:0;overflow:hidden;">
                    <iframe src="$url?autostart=true"
                        width="100%" height="100%" frameborder="0"
                        allow="autoplay; fullscreen">
                    </iframe>
                </body>
            </html>
        """.trimIndent()

                webView.loadDataWithBaseURL(
                    AnimeKaiPlugin.currentAnimeKaiServer,
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )

                cont.invokeOnCancellation {
                    finished = true
                    webView.destroy()
                }
            }
        }
        foundM3u8.forEach {
            M3u8Helper.generateM3u8(
                referer ?: name,
                it,
                mainUrl
            ).forEach(callback)
        }
    }

}
