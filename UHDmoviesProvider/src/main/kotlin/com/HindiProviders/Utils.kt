package com.HindiProviders


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import org.json.JSONObject
import java.net.*
import org.jsoup.nodes.Document

suspend fun extractBackupUHD(url: String): String? {
    val resumeDoc = app.get(url)

    val script = resumeDoc.document.selectFirst("script:containsData(FormData.)")?.data()

    val ssid = resumeDoc.cookies["PHPSESSID"]
    val baseIframe = getBaseUrl(url)
    val fetchLink =
        script?.substringAfter("fetch('")?.substringBefore("',")?.let { fixUrl(it, baseIframe) }
    val token = script?.substringAfter("'token', '")?.substringBefore("');")

    val body = FormBody.Builder()
        .addEncoded("token", "$token")
        .build()
    val cookies = mapOf("PHPSESSID" to "$ssid")

    val result = app.post(
        fetchLink ?: return null,
        requestBody = body,
        headers = mapOf(
            "Accept" to "*/*",
            "Origin" to baseIframe,
            "Sec-Fetch-Site" to "same-origin"
        ),
        cookies = cookies,
        referer = url
    ).text
    return tryParseJson<UHDBackupUrl>(result)?.url
}

@Suppress("NAME_SHADOWING")
suspend fun extractResumeUHD(url: String): String {
    app.get("https://driveleech.org$url").document.let {
        val url = it.selectFirst("a.btn.btn-success")?.attr("href").toString()
        return url
    }
}

suspend fun extractPixeldrainUHD(url: String): String {
    app.get("https://driveleech.org$url").document.let {
        return it.selectFirst("a.btn.btn-outline-info:contains(pixel)")?.attr("href").toString()
    }
}

suspend fun extractCFUHD(url: String): MutableList<String> {
    val CFlinks= mutableListOf<String>()
    app.get("https://driveleech.org$url?type=1").document.let {
            CFlinks+=it.select("div.mb-4 a").attr("href")
        }
    app.get("https://driveleech.org$url?type=2").document.let {
        CFlinks+=it.select("div.mb-4 a").attr("href")
    }
        return CFlinks
    }


fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}

data class UHDBackupUrl(
    @JsonProperty("url") val url: String? = null,
)

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
    var res = app.get(url).document
    var formUrl = res.getFormUrl()
    var formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    formUrl = res.getFormUrl()
    formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")
        ?.substringBefore("\"") ?: return null
    val driveUrl = app.get(
        "$host?go=$skToken", cookies = mapOf(
            skToken to "${formData["_wp_http2"]}"
        )
    ).document.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
    val path = app.get(driveUrl ?: return null).text.substringAfter("replace(\"")
        .substringBefore("\")")
    if (path == "/404") return null
    return fixUrl(path, getBaseUrl(driveUrl))
}


fun getUhdTags(str: String?): String {
    return Regex("\\d{3,4}[Pp]\\.?(.*?)\\[").find(str ?: "")?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim()
        ?: str ?: ""
}


fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

fun getIndexSize(str: String?): String? {
    return Regex("(?i)([\\d.]+\\s*(?:gb|mb))").find(str ?: "")?.groupValues?.getOrNull(1)?.trim()
}

suspend fun extractInstantUHD(url: String): String? {
    val host = getBaseUrl(url)
    val body = FormBody.Builder()
        .addEncoded("keys", url.substringAfter("url="))
        .build()
    return app.post(
        "$host/api", requestBody = body, headers = mapOf(
            "x-token" to URI(url).host
        ), referer = "$host/"
    ).parsedSafe<Map<String, String>>()?.get("url")
}

suspend fun extractDirectUHD(url: String, niceResponse: NiceResponse): String? {
    val document = niceResponse.document
    val script = document.selectFirst("script:containsData(cf_token)")?.data() ?: return null
    val actionToken = script.substringAfter("\"key\", \"").substringBefore("\");")
    val cfToken = script.substringAfter("cf_token = \"").substringBefore("\";")
    val body = FormBody.Builder()
        .addEncoded("action", "direct")
        .addEncoded("key", actionToken)
        .addEncoded("action_token", cfToken)
        .build()
    val cookies = mapOf("PHPSESSID" to "${niceResponse.cookies["PHPSESSID"]}")
    val direct = app.post(
        url,
        requestBody = body,
        cookies = cookies,
        referer = url,
        headers = mapOf(
            "x-token" to "driveleech.org"
        )
    ).parsedSafe<Map<String, String>>()?.get("url")

    return app.get(
        direct ?: return null, cookies = cookies,
        referer = url
    ).text.substringAfter("worker_url = '").substringBefore("';")

}

open class UHDMovies : ExtractorApi() {
    override val name: String = "UHDMovies"
    override val mainUrl: String = "https://video-seed.xyz"
    override val requiresReferer = true

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
            ExtractorLink(
                name,
                name,
                url = link,
                "",
                getQualityFromName(quality)
            )
        )
    }
}

class Driveseed : ExtractorApi() {
    override val name: String = "Driveseed"
    override val mainUrl: String = "https://driveseed.org"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private suspend fun CFType1(url: String): List<String>? {
        val cfWorkersLink = url.replace("/file", "/wfile") + "?type=1"
        val document = app.get(cfWorkersLink).document
        val links = document.select("a.btn-success").mapNotNull { it.attr("href") }
        return links ?: null
    }

    private suspend fun resumeCloudLink(url: String): String? {
        val resumeCloudUrl = "https://driveseed.org$url"
        val document = app.get(resumeCloudUrl).document
        val link = document.selectFirst("a.btn-success")?.attr("href")
        return link ?: null
    }

    private suspend fun resumeBot(url : String): String? {
        val resumeBotResponse = app.get(url)
        val resumeBotDoc = resumeBotResponse.document.toString()
        val ssid = resumeBotResponse.cookies["PHPSESSID"]
        val resumeBotToken = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(resumeBotDoc)?.groups?.get(1)?.value
        val resumeBotPath = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/\\+]+)'").find(resumeBotDoc)?.groups?.get(1)?.value
        val resumeBotBaseUrl = url.split("/download")[0]
        val requestBody = FormBody.Builder()
            .addEncoded("token", "$resumeBotToken")
            .build()

        val jsonResponse = app.post(resumeBotBaseUrl + "/download?id=" + resumeBotPath,
            requestBody = requestBody,
            headers = mapOf(
                "Accept" to "*/*",
                "Origin" to resumeBotBaseUrl,
                "Sec-Fetch-Site" to "same-origin"
            ),
            cookies = mapOf("PHPSESSID" to "$ssid"),
            referer = url
        ).text
        val jsonObject = JSONObject(jsonResponse)
        val link = jsonObject.getString("url")
        return link ?: null
    }

    private suspend fun instantLink(finallink: String): String? {
        val url = if(finallink.contains("video-leech")) "video-leech.xyz" else "video-seed.xyz"
        val token = finallink.substringAfter("https://$url/?url=")
        val downloadlink = app.post(
            url = "https://$url/api",
            data = mapOf(
                "keys" to token
            ),
            referer = finallink,
            headers = mapOf(
                "x-token" to url,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            )
        )
        val finaldownloadlink =
            downloadlink.toString().substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
        val link = finaldownloadlink
        return link
    }


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        val quality = document.selectFirst("li.list-group-item:contains(Name)")?.text() ?:""

        val instantUrl = document.selectFirst("a.btn-danger")?.attr("href") ?:""
        val instant = instantLink(instantUrl)
        if (instant != null) {
            callback.invoke(
                ExtractorLink(
                    "Instant(Download)",
                    "Instant(Download)",
                    instant,
                    "",
                    getIndexQuality(quality)
                )
            )
        }

        val resumeBotUrl = document.selectFirst("a.btn.btn-light")?.attr("href")
        val resumeLink = resumeBot(resumeBotUrl ?:"")
        if (resumeLink != null) {
            callback.invoke(
                ExtractorLink(
                    "ResumeBot",
                    "ResumeBot(VLC)",
                    resumeLink,
                    "",
                    getIndexQuality(quality)
                )
            )
        }

        val cfType1 = CFType1(url)
        if (cfType1 != null) {
            cfType1.forEach {
                callback.invoke(
                    ExtractorLink(
                        "CF Type1",
                        "CF Type1",
                        it,
                        "",
                        getIndexQuality(quality)
                    )
                )
            }
        }

        val resumeCloudUrl = document.selectFirst("a.btn-warning")?.attr("href")
        val resumeCloud = resumeCloudLink(resumeCloudUrl ?:"")
        if (resumeCloud != null) {
            callback.invoke(
                ExtractorLink(
                    "ResumeCloud",
                    "ResumeCloud",
                    resumeCloud,
                    "",
                    getIndexQuality(quality)
                )
            )
        }

    }
}