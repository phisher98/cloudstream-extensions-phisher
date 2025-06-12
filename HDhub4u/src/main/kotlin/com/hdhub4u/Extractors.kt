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
        val realUrl = replaceHubclouddomain(url)
        val href = if (realUrl.contains("hubcloud.php")) {
            realUrl
        } else {
            val regex = "var url = '([^']*)'".toRegex()
            val regexdata=app.get(realUrl).document.selectFirst("script:containsData(url)")?.toString() ?: ""
            regex.find(regexdata)?.groupValues?.get(1).orEmpty()
        }
        if (href.isEmpty()) {
            Log.d("Error", "Not Found")
            return
        }

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val headerdetails = """\.\d{3,4}p\.(.*)-[^-]*$""".toRegex()
            .find(header)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val labelExtras = buildString {
            if (headerdetails.isNotEmpty()) append("[$headerdetails]")
            if (size.isNotEmpty()) append("[$size]")
        }

        document.select("h2 a.btn").amap { element ->
            val link = element.attr("href")
            val text = element.text()
            val baseUrl = getBaseUrl(link)
            val quality = getIndexQuality(header)

            when {
                text.contains("Download [FSL Server]") -> {
                    callback.invoke(
                        newExtractorLink(
                            "$name[FSL Server] $labelExtras",
                            "$name[FSL Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("Download File") -> {
                    callback.invoke(
                        newExtractorLink(
                            "$name $labelExtras",
                            "$name $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("BuzzServer") -> {
                    val dlink = app.get("$link/download", referer = link, allowRedirects = false)
                        .headers["hx-redirect"] ?: ""
                    if (dlink.isNotEmpty()) {
                        callback.invoke(
                            newExtractorLink(
                                "$name[BuzzServer] $labelExtras",
                                "$name[BuzzServer] $labelExtras",
                                baseUrl + dlink,
                            ) { this.quality = quality }
                        )
                    } else {
                        Log.w("Error:","Not Found")
                    }
                }

                link.contains("pixeldra") -> {
                    callback.invoke(
                        newExtractorLink(
                            "Pixeldrain $labelExtras",
                            "Pixeldrain $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("Download [Server : 10Gbps]") -> {
                    val dlink = app.get(link, allowRedirects = false).headers["location"]?.substringAfter("link=") ?: ""
                    callback.invoke(
                        newExtractorLink(
                            "$name[Download] $labelExtras",
                            "$name[Download] $labelExtras",
                            dlink,
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getIndexQuality(str: String?) =
        Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P2160.value

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}



