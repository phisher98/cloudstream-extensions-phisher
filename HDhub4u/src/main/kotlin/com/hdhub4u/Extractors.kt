package com.hdhub4u

import android.annotation.SuppressLint
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.net.URL

class HdStream4u : VidHidePro() {
    override var mainUrl = "https://hdstream4u.com"
}

class Hubstream : VidStack() {
    override var mainUrl = "https://hubstream.art"
}

class Hblinks : ExtractorApi() {
    override val name = "Hblinks"
    override val mainUrl = "https://hblinks.pro"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url).document.select("h3 a,div.entry-content p a").map {
            val href=it.attr("href")
            loadExtractor(href,"HDHUB4U",subtitleCallback, callback)
        }

    }
}

class Hubcdn : ExtractorApi() {
    override val name = "Hubcdn"
    override val mainUrl = "https://hubcdn.cloud"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url).document.toString().let {
            val encoded = Regex("r=([A-Za-z0-9+/=]+)").find(it)?.groups?.get(1)?.value
            if (!encoded.isNullOrEmpty()) {
                val m3u8 = base64Decode(encoded).substringAfterLast("link=")
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = m3u8,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            } else {
                Log.e("Error", "Encoded URL not found")
            }


        }
    }
}

class Hubdrive : ExtractorApi() {
    override val name = "Hubdrive"
    override val mainUrl = "https://hubdrive.fit"
    override val requiresReferer = false

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url).document.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud"))
        {
            HubCloud().getUrl(href,"HubDrive",subtitleCallback, callback)
        }
        else
        loadExtractor(href,"HubDrive",subtitleCallback, callback)
    }
}


@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.ink"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val realUrl = try {
            val originalUrl = URL(url)
            val parts = originalUrl.host.split(".").toMutableList()
            if (parts.size > 1) {
                parts[parts.lastIndex] = "dad"
                URL(originalUrl.protocol, parts.joinToString("."), originalUrl.port, originalUrl.file).toString()
            } else url
        } catch (e: Exception) {
            Log.e("HubCloud", "Invalid URL: ${e.message}")
            return
        }

        val baseUrl=getBaseUrl(realUrl)

        val href = try {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val rawHref = app.get(realUrl).document.select("#download").attr("href")
                if (rawHref.startsWith("http", ignoreCase = true)) {
                    rawHref
                } else {
                    baseUrl.trimEnd('/') + "/" + rawHref.trimStart('/')
                }
            }
        } catch (e: Exception) {
            Log.e("HubCloud", "Failed to extract href: ${e.message}")
            ""
        }

        if (href.isBlank()) {
            Log.w("HubCloud", "No valid href found")
            return
        }

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()

        val headerDetails = cleanTitle(header)

        val labelExtras = buildString {
            if (headerDetails.isNotEmpty()) append("[$headerDetails]")
            if (size.isNotEmpty()) append("[$size]")
        }
        val quality = getIndexQuality(header)

        document.select("div.card-body h2 a.btn").amap { element ->
            val link = element.attr("href")
            val text = element.text()

            when {
                text.contains("FSL Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source [FSL Server] $labelExtras",
                            "$source [FSL Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("Download File", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source $labelExtras",
                            "$source $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("BuzzServer", ignoreCase = true) -> {
                    val buzzResp = app.get("$link/download", referer = link, allowRedirects = false)
                    val dlink = buzzResp.headers["hx-redirect"].orEmpty()
                    if (dlink.isNotBlank()) {
                        callback.invoke(
                            newExtractorLink(
                                "$source [BuzzServer] $labelExtras",
                                "$source [BuzzServer] $labelExtras",
                                dlink,
                            ) { this.quality = quality }
                        )
                    } else {
                        Log.w("HubCloud", "BuzzServer: No redirect")
                    }
                }

                "pixeldra" in link -> {
                    callback.invoke(
                        newExtractorLink(
                            "Pixeldrain $labelExtras",
                            "Pixeldrain $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("S3 Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source S3 Server $labelExtras",
                            "$source S3 Server $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("10Gbps", ignoreCase = true) -> {
                    var currentLink = link
                    var finalUrl: String? = null

                    while (true) {
                        val response = app.get(currentLink, allowRedirects = false)
                        val redirect = response.headers["location"]
                        if (redirect == null) {
                            Log.i("Error:", "No more redirects. Final URL: $currentLink")
                            break
                        }

                        if ("link=" in redirect) {
                            finalUrl = redirect.substringAfter("link=")
                            break
                        }

                        currentLink = redirect
                    }

                    finalUrl?.let { finalLink ->
                        callback.invoke(
                            newExtractorLink(
                                "[Download] $labelExtras",
                                "[Download] $labelExtras",
                                finalLink,
                            ) { this.quality = quality }
                        )
                    }
                }

                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P2160.value
    }

    private fun getBaseUrl(url: String): String {
        return try {
            URI(url).let { "${it.scheme}://${it.host}" }
        } catch (e: Exception) {
            ""
        }
    }
}



