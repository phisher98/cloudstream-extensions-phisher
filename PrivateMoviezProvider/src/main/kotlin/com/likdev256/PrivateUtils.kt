package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.delay
import com.lagradost.nicehttp.Session
import okhttp3.FormBody
import java.net.URI

val gdbot = "https://gdbot.xyz"

data class Bypassed (
    @JsonProperty("status"  ) val status  : String?,
    @JsonProperty("message" ) val message : Any?,
    @JsonProperty("url"     ) val url     : String
)

data class DriveBotLink(
    @JsonProperty("url") val url: String? = null,
)

suspend fun gtlinksBypass(url: String): String {
    val token = url.substringAfterLast("/")

    val domain = "https://go.theforyou.in/"
    val session = Session(app.baseClient)
    val response = session.get(domain + token, referer = domain + token)

    val data = FormBody.Builder()
    response.document.select("#go-link input").forEach {
        data.add(it.attr("name"), it.attr("value"))
    }
    val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
    delay(5000)
    return parseJson<Bypassed>(
        session.post(
        domain + "links/go",
        requestBody = data.build(),
        headers = headers
        ).toString()
    ).url
}

suspend fun extractGdbot(url: String): String? {
    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    )
    val res = app.get(
        "$gdbot/", headers = headers
    )
    val token = res.document.selectFirst("input[name=_token]")?.attr("value")
    val cookiesSet = res.headers.filter { it.first == "set-cookie" }
    val xsrf =
        cookiesSet.find { it.second.contains("XSRF-TOKEN") }?.second?.substringAfter("XSRF-TOKEN=")
            ?.substringBefore(";")
    val session =
        cookiesSet.find { it.second.contains("gdtot_proxy_session") }?.second?.substringAfter("gdtot_proxy_session=")
            ?.substringBefore(";")

    val cookies = mapOf(
        "gdtot_proxy_session" to "$session",
        "XSRF-TOKEN" to "$xsrf"
    )
    val requestFile = app.post(
        "$gdbot/file", data = mapOf(
            "link" to url,
            "_token" to "$token"
        ), headers = headers, referer = "$gdbot/", cookies = cookies
    ).document

    return requestFile.selectFirst("div.mt-8 a.float-right")?.attr("href")
}

suspend fun extractDrivebot(url: String): String? {
    val iframeDrivebot = app.get(url).document.selectFirst("li.flex.flex-col.py-6 a:contains(Drivebot)")
        ?.attr("href") ?: return null
    return getDrivebotLink(iframeDrivebot)
}

suspend fun extractGdflix(url: String): String? {
    val iframeGdflix = app.get(url).document.selectFirst("li.flex.flex-col.py-6 a:contains(GDFlix Direct)")
        ?.attr("href") ?: return null
    val base = getBaseUrl(iframeGdflix)

    val gdfDoc = app.get(iframeGdflix).document.selectFirst("script:containsData(replace)")?.data()?.substringAfter("replace(\"")
        ?.substringBefore("\")")?.let {
            app.get(fixUrl(it, base)).document
        }
    val iframeDrivebot2 = gdfDoc?.selectFirst("a.btn.btn-outline-warning")?.attr("href")

    return getDrivebotLink(iframeDrivebot2)
}

suspend fun getDrivebotLink(url: String?): String? {
    val driveDoc = app.get(url ?: return null)

    val ssid = driveDoc.cookies["PHPSESSID"]
    val script = driveDoc.document.selectFirst("script:containsData(var formData)")?.data()

    val baseUrl = getBaseUrl(url)
    val token = script?.substringAfter("'token', '")?.substringBefore("');")
    val link =
        script?.substringAfter("fetch('")?.substringBefore("',").let { "$baseUrl$it" }

    val body = FormBody.Builder()
        .addEncoded("token", "$token")
        .build()
    val cookies = mapOf("PHPSESSID" to "$ssid")

    val result = app.post(
        link,
        requestBody = body,
        headers = mapOf(
            "Accept" to "*/*",
            "Origin" to baseUrl,
            "Sec-Fetch-Site" to "same-origin"
        ),
        cookies = cookies,
        referer = url
    ).text
    return tryParseJson<DriveBotLink>(result)?.url
}

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

