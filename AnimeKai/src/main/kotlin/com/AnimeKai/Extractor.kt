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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        val urlsToProcess = listOf(url)
        val ctx = context ?: return

        for (currentUrl in urlsToProcess) {
            val foundM3u8 = mutableListOf<String>()

            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->

                    val webView = WebView(ctx)
                    webView.apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.allowFileAccess = true
                    }

                    webView.webViewClient = object : WebViewClient() {
                        private var lastUrlTime = System.currentTimeMillis()
                        private val finishDelay = 10000L
                        private var finished = false

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            request?.url?.toString()?.let {
                                handleRequest(it, subtitleCallback, foundM3u8)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        private fun handleRequest(url: String, subtitleCallback: (SubtitleFile) -> Unit, foundM3u8: MutableList<String>) {
                            Log.d("MegaUp", "Intercepted: $url")
                            when {
                                url.endsWith(".m3u8") && !foundM3u8.contains(url) -> {
                                    lastUrlTime = System.currentTimeMillis()
                                    runBlocking {
                                        M3u8Helper.generateM3u8(referer ?: name, url, mainUrl).forEach { callback(it) }
                                    }
                                }
                                url.endsWith(".vtt") && !url.contains("thumbnails", true) -> {
                                    lastUrlTime = System.currentTimeMillis()
                                    subtitleCallback(SubtitleFile(extractLabelFromUrl(url), url))
                                }
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.loadUrl(
                                "javascript:(function(){" +
                                        "var btn=document.querySelector('button, .vjs-big-play-button');" +
                                        "if(btn)btn.click();" +
                                        "})()"
                            )

                            val handler = Handler(Looper.getMainLooper())
                            handler.post(object : Runnable {
                                override fun run() {
                                    if (finished) return

                                    val idleTime = System.currentTimeMillis() - lastUrlTime

                                    if (foundM3u8.isNotEmpty() && idleTime > 1000) {
                                        finished = true
                                        Log.d("MegaUp", "Links found, finishing early for $currentUrl")
                                        cont.resume(Unit)
                                        webView.destroy()
                                        return
                                    }

                                    if (idleTime > finishDelay) {
                                        finished = true
                                        Log.d("MegaUp", "Timeout reached, finishing for $currentUrl")
                                        cont.resume(Unit)
                                        webView.destroy()
                                        return
                                    }

                                    handler.postDelayed(this, 500)
                                }
                            })
                        }

                    }

                    val html = """
                        <html>
                            <head><meta name="viewport" content="width=device-width, initial-scale=1.0"/></head>
                            <body style="margin:0;padding:0;overflow:hidden;">
                                <iframe src="$currentUrl?autostart=true"
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
                        webView.destroy()
                    }
                }
            }
        }
    }
}


