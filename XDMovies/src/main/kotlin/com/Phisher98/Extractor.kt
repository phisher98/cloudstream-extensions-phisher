package com.phisher98

import android.annotation.SuppressLint
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.net.URL
import kotlin.text.Regex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup

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

class XdMoviesExtractor : ExtractorApi() {
    override val name = "XdMoviesExtractor"
    override val mainUrl = " https://link.xdmovies.site"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url, allowRedirects = false).headers["location"]
        if (href!=null) {
            if (href.contains("hubcloud")) {
                HubCloud().getUrl(href, "HubDrive", subtitleCallback, callback)
            } else loadExtractor(href, "HubDrive", subtitleCallback, callback)
        }
    }
}

class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.ink"
    override val requiresReferer = false

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .build()

    private suspend fun fetchUrl(
        url: String,
        headers: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true
    ): Response {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        return withContext(Dispatchers.IO) {
            val call = client.newCall(requestBuilder.build())
            val response = call.execute()
            if (!allowRedirects && response.isRedirect) {
                response
            } else if (!allowRedirects) {
                response
            } else response
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val realUrl = url.takeIf {
            try {
                URL(it); true
            } catch (e: Exception) {
                Log.e("HubCloud", "Invalid URL: ${e.message}"); false
            }
        } ?: return

        val baseUrl = getBaseUrl(realUrl)

        val href = try {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val response = fetchUrl(realUrl)
                val document = Jsoup.parse(response.body!!.string())
                val rawHref = document.select("#download").attr("href")
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

        val document = fetchUrl(href).use { Jsoup.parse(it.body!!.string()) }
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val headerDetails = cleanTitle(header)
        val labelExtras = buildString {
            if (headerDetails.isNotEmpty()) append("[$headerDetails]")
            if (size.isNotEmpty()) append("[$size]")
        }
        val quality = getIndexQuality(header)

        document.select("div.card-body h2 a.btn").forEach { element ->
            val link = element.attr("href")
            val text = element.text()
            val baseUrlLink = getBaseUrl(link)

            when {
                text.contains("FSL Server", ignoreCase = true) -> callback(
                    newExtractorLink(
                        "$referer [FSL Server]",
                        "$referer [FSL Server] $labelExtras",
                        link
                    ) { this.quality = quality }
                )

                text.contains("Download File", ignoreCase = true) -> callback(
                    newExtractorLink(
                        "$referer",
                        "$referer $labelExtras",
                        link
                    ) { this.quality = quality }
                )

                text.contains("BuzzServer", ignoreCase = true) -> {
                    val buzzResp = fetchUrl("$link/download", allowRedirects = false)
                    val dlink = buzzResp.header("hx-redirect").orEmpty()
                    if (dlink.isNotBlank()) {
                        callback(
                            newExtractorLink(
                                "$referer [BuzzServer]",
                                "$referer [BuzzServer] $labelExtras",
                                baseUrlLink + dlink
                            ) { this.quality = quality }
                        )
                    } else Log.w("HubCloud", "BuzzServer: No redirect")
                }

                text.contains("pixeldra", ignoreCase = true) || text.contains("pixel", ignoreCase = true) -> {
                    val finalURL = if (link.contains("download", true)) link
                    else "$baseUrlLink/api/file/${link.substringAfterLast("/")}?download"

                    callback(
                        newExtractorLink(
                            "Pixeldrain",
                            "Pixeldrain $labelExtras",
                            finalURL
                        ) { this.quality = quality }
                    )
                }

                text.contains("S3 Server", ignoreCase = true) -> callback(
                    newExtractorLink(
                        "$referer S3 Server",
                        "$referer S3 Server $labelExtras",
                        link
                    ) { this.quality = quality }
                )

                text.contains("10Gbps", ignoreCase = true) -> {
                    var currentLink = link
                    var redirectUrl: String?
                    while (true) {
                        val response = fetchUrl(currentLink, allowRedirects = false)
                        redirectUrl = response.header("location")
                        if (redirectUrl == null) {
                            Log.e("HubCloud", "10Gbps: No redirect")
                            return@forEach
                        }
                        if ("link=" in redirectUrl) break
                        currentLink = redirectUrl
                    }
                    val finalLink = redirectUrl.substringAfter("link=")
                    callback(
                        newExtractorLink(
                            "$referer 10Gbps [Download]",
                            "$referer 10Gbps [Download] $labelExtras",
                            finalLink
                        ) { this.quality = quality }
                    )
                }

                else -> loadExtractor(link, "", subtitleCallback, callback)
            }
        }
    }

    private fun getIndexQuality(str: String?): Int =
        Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P2160.value

    private fun getBaseUrl(url: String): String =
        try { URI(url).let { "${it.scheme}://${it.host}" } } catch (_: Exception) { "" }

    private fun cleanTitle(title: String): String {
        val parts = title.split(".", "-", "_")
        val qualityTags = listOf("WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV", "CAM", "TS", "R5", "DVDScr", "BRRip", "BDRip", "DVD", "PDTV", "HD")
        val audioTags = listOf("AAC", "AC3", "DTS", "MP3", "FLAC", "DD5", "EAC3", "Atmos")
        val subTags = listOf("ESub", "ESubs", "Subs", "MultiSub", "NoSub", "EnglishSub", "HindiSub")
        val codecTags = listOf("x264", "x265", "H264", "HEVC", "AVC")

        val startIndex = parts.indexOfFirst { part -> qualityTags.any { tag -> part.contains(tag, ignoreCase = true) } }
        val endIndex = parts.indexOfLast { part -> subTags.any { tag -> part.contains(tag, ignoreCase = true) } || audioTags.any { tag -> part.contains(tag, ignoreCase = true) } || codecTags.any { tag -> part.contains(tag, ignoreCase = true) } }

        return if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) parts.subList(startIndex, endIndex + 1).joinToString(".")
        else if (startIndex != -1) parts.subList(startIndex, parts.size).joinToString(".")
        else parts.takeLast(3).joinToString(".")
    }
}
