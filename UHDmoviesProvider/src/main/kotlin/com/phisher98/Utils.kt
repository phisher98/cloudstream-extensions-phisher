package com.phisher98


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.*
import org.jsoup.nodes.Document

fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
}

suspend fun bypassHrefli(url: String): String? {
    fun Document.getFormUrl(): String {
        return this.select("form#landing").attr("action")
    }

    fun Document.getFormData(): Map<String, String> {
        return this.select("form#landing input").associate { it.attr("name") to it.attr("value") }
    }

    val host = getBaseUrl(url)
    var res = app.get(url).documentLarge
    var formUrl = res.getFormUrl()
    var formData = res.getFormData()

    res = app.post(formUrl, data = formData).documentLarge
    formUrl = res.getFormUrl()
    formData = res.getFormData()

    res = app.post(formUrl, data = formData).documentLarge
    val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")
        ?.substringBefore("\"") ?: return null
    val driveUrl = app.get(
        "$host?go=$skToken", cookies = mapOf(
            skToken to "${formData["_wp_http2"]}"
        )
    ).documentLarge.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
    val path = app.get(driveUrl ?: return null).text.substringAfter("replace(\"")
        .substringBefore("\")")
    if (path == "/404") return null
    return fixUrl(path, getBaseUrl(driveUrl))
}

open class UHDMovies : ExtractorApi() {
    override val name: String = "UHDMovies"
    override val mainUrl: String = "https://video-seed.xyz"
    override val requiresReferer = true

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun getUrl(
        finallink: String,
        quality: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val token = finallink.substringAfter("https://video-seed.xyz/?url=")
        val downloadlink = app.post(
            url = "https://video-seed.xyz/api",
            data = mapOf(
                "keys" to token
            ),
            referer = finallink,
            headers = mapOf(
                "x-token" to "video-seed.xyz",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            )
        )
        val finaldownloadlink =
            downloadlink.toString().substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
        val link = finaldownloadlink
        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = link
            ) {
                this.referer = ""
                this.quality = getQualityFromName(quality)
            }
        )
    }
}

fun getSearchQuality(check: String?): SearchQuality? {
    val lowercaseCheck = check?.lowercase()
    if (lowercaseCheck != null) {
        return when {
            lowercaseCheck.contains("4k") || lowercaseCheck.contains("uhd") || lowercaseCheck.contains("2160p") -> SearchQuality.FourK
            lowercaseCheck.contains("1440p") || lowercaseCheck.contains("qhd") -> SearchQuality.BlueRay
            lowercaseCheck.contains("1080p") || lowercaseCheck.contains("fullhd") -> SearchQuality.HD
            lowercaseCheck.contains("720p") -> SearchQuality.SD
            lowercaseCheck.contains("webrip") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
            lowercaseCheck.contains("bluray") -> SearchQuality.BlueRay
            lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains("hdtc") -> SearchQuality.HdCam
            lowercaseCheck.contains("dvd") -> SearchQuality.DVD
            lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
            lowercaseCheck.contains("cam") -> SearchQuality.Cam
            lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hdtv") -> SearchQuality.HD
            lowercaseCheck.contains("hq") -> SearchQuality.HQ
            else -> null
        }
    }
    return null
}