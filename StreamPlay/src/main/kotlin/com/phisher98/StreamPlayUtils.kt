package com.Phisher98

import android.annotation.SuppressLint
import app.cash.quickjs.QuickJs
import com.Phisher98.DumpUtils.queryApi
import com.Phisher98.StreamPlay.Companion.anilistAPI
import com.Phisher98.StreamPlay.Companion.crunchyrollAPI
import com.Phisher98.StreamPlay.Companion.filmxyAPI
import com.Phisher98.StreamPlay.Companion.fourthAPI
import com.Phisher98.StreamPlay.Companion.gdbot
import com.Phisher98.StreamPlay.Companion.hdmovies4uAPI
import com.Phisher98.StreamPlay.Companion.malsyncAPI
import com.Phisher98.StreamPlay.Companion.thrirdAPI
import com.Phisher98.StreamPlay.Companion.tvMoviesAPI
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.math.BigInteger
import java.net.*
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

var filmxyCookies: Map<String, String>? = null
var sfServer: String? = null

val encodedIndex = arrayOf(
    "GamMovies",
    "JSMovies",
    "BlackMovies",
    "CodexMovies",
    "RinzryMovies",
    "EdithxMovies",
    "XtremeMovies",
    "PapaonMovies[1]",
    "PapaonMovies[2]",
    "JmdkhMovies",
    "RubyMovies",
    "ShinobiMovies",
    "VitoenMovies",
)

val lockedIndex = arrayOf(
    "CodexMovies",
    "EdithxMovies",
)

val mkvIndex = arrayOf(
    "EdithxMovies",
    "JmdkhMovies",
)

val untrimmedIndex = arrayOf(
    "PapaonMovies[1]",
    "PapaonMovies[2]",
    "EdithxMovies",
)

val needRefererIndex = arrayOf(
    "ShinobiMovies",
)

val ddomainIndex = arrayOf(
    "RinzryMovies",
    "ShinobiMovies"
)

val mimeType = arrayOf(
    "video/x-matroska",
    "video/mp4",
    "video/x-msvideo"
)

fun Document.getMirrorLink(): String? {
    return this.select("div.mb-4 a").randomOrNull()
        ?.attr("href")
}

fun Document.getMirrorServer(server: Int): String {
    return this.select("div.text-center a:contains(Server $server)").attr("href")
}

suspend fun extractMirrorUHD(url: String, ref: String): String? {
    var baseDoc = app.get(fixUrl(url, ref)).document

    var downLink = baseDoc.getMirrorLink()
    run lit@{
        (1..2).forEach {
            if (downLink != null) return@lit
            val server = baseDoc.getMirrorServer(it.plus(1))
            baseDoc = app.get(fixUrl(server, ref)).document
            downLink = baseDoc.getMirrorLink()
        }
    }
    return if (downLink?.contains("workers.dev") == true) downLink else base64Decode(
        downLink?.substringAfter(
            "download?url="
        ) ?: return null
    )
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

fun replaceHubclouddomain(url: String): String {
    return try {
        val originalUrl = URL(url)
        val domainParts = originalUrl.host.split(".").toMutableList()
        if (domainParts.size > 1) {
            domainParts[domainParts.lastIndex] = "dad"
            val newDomain = domainParts.joinToString(".")

            // Construct the new URL with the updated domain
            URL(originalUrl.protocol, newDomain, originalUrl.port, originalUrl.file).toString()
        } else {
            throw IllegalArgumentException("Invalid domain structure in URL")
        }
    } catch (e: Exception) {
        "Invalid URL: ${e.message}"
    }
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

suspend fun extractPixeldrainUHD(url: String): String {
    app.get("https://driveleech.org$url").document.let {
        return it.selectFirst("a.btn.btn-outline-info:contains(pixel)")?.attr("href").toString()
    }
}

suspend fun extractResumeTop(url: String): String {
    app.get("https://driveleech.org$url").document.let {
        val link = it.selectFirst("a.btn.btn-success")?.attr("href").toString()
        return link
    }
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

suspend fun extractDirectDl(url: String): String? {
    val iframe = app.get(url).document.selectFirst("li.flex.flex-col.py-6 a:contains(Direct DL)")
        ?.attr("href")
    val request = app.get(iframe ?: return null)
    val driveDoc = request.document
    val token = driveDoc.select("section#generate_url").attr("data-token")
    val uid = driveDoc.select("section#generate_url").attr("data-uid")

    val ssid = request.cookies["PHPSESSID"]
    val body =
        """{"type":"DOWNLOAD_GENERATE","payload":{"uid":"$uid","access_token":"$token"}}""".toRequestBody(
            RequestBodyTypes.JSON.toMediaTypeOrNull()
        )

    val json = app.post(
        "https://rajbetmovies.com/action", requestBody = body, headers = mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Cookie" to "PHPSESSID=$ssid",
            "X-Requested-With" to "xmlhttprequest"
        ), referer = request.url
    ).text
    return tryParseJson<DirectDl>(json)?.download_url
}

suspend fun extractMovieAPIlinks(serverid: String,movieid: String,MOVIE_API: String): String {
    val link=app.get("$MOVIE_API/ajax/get_stream_link?id=$serverid&movie=$movieid").document.toString().substringAfter("link\":\"").substringBefore("\",")
    return link
}

suspend fun extractDrivebot(url: String): String? {
    val iframeDrivebot =
        app.get(url).document.selectFirst("li.flex.flex-col.py-6 a:contains(Drivebot)")
            ?.attr("href") ?: return null
    return getDrivebotLink(iframeDrivebot)
}

suspend fun extractGdflix(url: String): String? {
    val iframeGdflix =
        if (!url.contains("gdflix")) app.get(url).document.selectFirst("li.flex.flex-col.py-6 a:contains(GDFlix Direct)")
            ?.attr("href") ?: return null else url
    val base = getBaseUrl(iframeGdflix)

    val req = app.get(iframeGdflix).document.selectFirst("script:containsData(replace)")?.data()
        ?.substringAfter("replace(\"")
        ?.substringBefore("\")")?.let {
            app.get(fixUrl(it, base))
        } ?: return null

    val iframeDrivebot2 = req.document.selectFirst("a.btn.btn-outline-warning")?.attr("href")
    return getDrivebotLink(iframeDrivebot2)

//    val reqUrl = req.url
//    val ssid = req.cookies["PHPSESSID"]
//    val script = req.document.selectFirst("script:containsData(formData =)")?.data()
//    val key = Regex("append\\(\"key\", \"(\\S+?)\"\\);").find(script ?: return null)?.groupValues?.get(1)
//
//    val body = FormBody.Builder()
//        .addEncoded("action", "direct")
//        .addEncoded("key", "$key")
//        .addEncoded("action_token", "cf_token")
//        .build()
//
//    val gdriveUrl = app.post(
//        reqUrl, requestBody = body,
//        cookies = mapOf("PHPSESSID" to "$ssid"),
//        headers = mapOf(
//            "x-token" to URI(reqUrl).host
//        )
//    ).parsedSafe<Gdflix>()?.url
//
//    return getDirectGdrive(gdriveUrl ?: return null)

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

    val file = app.post(
        link,
        requestBody = body,
        headers = mapOf(
            "Accept" to "*/*",
            "Origin" to baseUrl,
            "Sec-Fetch-Site" to "same-origin"
        ),
        cookies = cookies,
        referer = url
    ).parsedSafe<DriveBotLink>()?.url ?: return null

    return if (file.startsWith("http")) file else app.get(
        fixUrl(
            file,
            baseUrl
        )
    ).document.selectFirst("script:containsData(window.open)")
        ?.data()?.substringAfter("window.open('")?.substringBefore("')")
}

suspend fun extractOiya(url: String): String? {
    return app.get(url).document.selectFirst("div.wp-block-button a")?.attr("href")
}

fun deobfstr(hash: String, index: String): String {
    var result = ""
    for (i in hash.indices step 2) {
        val j = hash.substring(i, i + 2)
        result += (j.toInt(16) xor index[(i / 2) % index.length].code).toChar()
    }
    return result
}

suspend fun extractCovyn(url: String?): Pair<String?, String?>? {
    val request = session.get(url ?: return null, referer = "${tvMoviesAPI}/")
    val filehosting = session.baseClient.cookieJar.loadForRequest(url.toHttpUrl())
        .find { it.name == "filehosting" }?.value
    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Connection" to "keep-alive",
        "Cookie" to "filehosting=$filehosting",
    )

    val iframe = request.document.findTvMoviesIframe()
    delay(10500)
    val request2 = session.get(
        iframe ?: return null, referer = url, headers = headers
    )

    val iframe2 = request2.document.findTvMoviesIframe()
    delay(10500)
    val request3 = session.get(
        iframe2 ?: return null, referer = iframe, headers = headers
    )

    val response = request3.document
    val videoLink = response.selectFirst("button.btn.btn--primary")?.attr("onclick")
        ?.substringAfter("location = '")?.substringBefore("';")?.let {
            app.get(
                it, referer = iframe2, headers = headers
            ).url
        }
    val size = response.selectFirst("ul.row--list li:contains(Filesize) span:last-child")
        ?.text()

    return Pair(videoLink, size)
}

//EmbedSu

fun EmbedSuitemparseJson(jsonString: String): List<EmbedsuItem> {
    val gson = Gson()
    return gson.fromJson(jsonString, Array<EmbedsuItem>::class.java).toList()
}

fun encodeQuery(query: String): String {
    return URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
}

fun fixUrlEncoding(url: String): String {
    // Extract query parameters from the URL (everything after the "?")
    val baseUrl = url.substringBefore("?")
    val query = url.substringAfter("?")

    // Encode the query string
    val encodedQuery = URLEncoder.encode(query, "UTF-8")

    // Ensure special characters in the query string are handled correctly (e.g., %20 for spaces, %26 for &)
    return "$baseUrl?$encodedQuery"
}

suspend fun getDirectGdrive(url: String): String {
    val fixUrl = if (url.contains("&export=download")) {
        url
    } else {
        "https://drive.google.com/uc?id=${
            Regex("(?:\\?id=|/d/)(\\S+)/").find("$url/")?.groupValues?.get(1)
        }&export=download"
    }

    val doc = app.get(fixUrl).document
    val form = doc.select("form#download-form").attr("action")
    val uc = doc.select("input#uc-download-link").attr("value")
    return app.post(
        form, data = mapOf(
            "uc-download-link" to uc
        )
    ).url

}

suspend fun invokeSmashyFfix(
    name: String,
    url: String,
    ref: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    val json = app.get(url, referer = ref, headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
        .parsedSafe<SmashySources>()
    json?.sourceUrls?.map {
        M3u8Helper.generateM3u8(
            "Smashy [$name]",
            it,
            ""
        ).forEach(callback)
    }

    json?.subtitleUrls?.split(",")?.map { sub ->
        val lang = "\\[(.*)]".toRegex().find(sub)?.groupValues?.get(1)
        val subUrl = sub.replace("[$lang]", "").trim()
        subtitleCallback.invoke(
            SubtitleFile(
                lang ?: return@map,
                subUrl
            )
        )
    }

}

suspend fun invokeSmashySu(
    name: String,
    url: String,
    ref: String,
    callback: (ExtractorLink) -> Unit,
) {
    val json = app.get(url, referer = ref, headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
        .parsedSafe<SmashySources>()
    json?.sourceUrls?.firstOrNull()?.removeSuffix(",")?.split(",")?.forEach { links ->
        val quality = Regex("\\[(\\S+)]").find(links)?.groupValues?.getOrNull(1) ?: return@forEach
        val trimmedLink = links.removePrefix("[$quality]").trim()
        callback.invoke(
            ExtractorLink(
                "Smashy [$name]",
                "Smashy [$name]",
                trimmedLink,
                "",
                getQualityFromName(quality),
                INFER_TYPE
            )
        )
    }
}

suspend fun getDumpIdAndType(title: String?, year: Int?, season: Int?): Pair<String?, Int?> {
    val res = tryParseJson<DumpQuickSearchData>(
        queryApi(
            "POST",
            "${BuildConfig.DUMP_API}/search/searchWithKeyWord",
            mapOf(
                "searchKeyWord" to "$title",
                "size" to "50",
            )
        )
    )?.searchResults

    val media = if (res?.size == 1) {
        res.firstOrNull()
    } else {
        res?.find {
            when (season) {
                null -> {
                    it.name.equals(
                        title,
                        true
                    ) && it.releaseTime == "$year" && it.domainType == 0
                }

                1 -> {
                    it.name?.contains(
                        "$title",
                        true
                    ) == true && (it.releaseTime == "$year" || it.name.contains(
                        "Season $season",
                        true
                    )) && it.domainType == 1
                }

                else -> {
                    it.name?.contains(Regex("(?i)$title\\s?($season|${season.toRomanNumeral()}|Season\\s$season)")) == true && it.releaseTime == "$year" && it.domainType == 1
                }
            }
        }
    }

    return media?.id to media?.domainType

}

suspend fun fetchDumpEpisodes(id: String, type: String, episode: Int?): EpisodeVo? {
    return tryParseJson<DumpMediaDetail>(
        queryApi(
            "GET",
            "${BuildConfig.DUMP_API}/movieDrama/get",
            mapOf(
                "category" to type,
                "id" to id,
            )
        )
    )?.episodeVo?.find {
        it.seriesNo == (episode ?: 0)
    }
}

suspend fun invokeDrivetot(
    url: String,
    tags: String? = null,
    size: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    val res = app.get(url)
    val data = res.document.select("form input").associate { it.attr("name") to it.attr("value") }
    app.post(res.url, data = data, cookies = res.cookies).document.select("div.card-body a")
        .apmap { ele ->
            val href = base64Decode(ele.attr("href").substringAfterLast("/")).let {
                if (it.contains("hubcloud.lol")) it.replace("hubcloud.lol", "hubcloud.in") else it
            }
            loadExtractor(href, "$hdmovies4uAPI/", subtitleCallback) { link ->
                callback.invoke(
                    ExtractorLink(
                        link.source,
                        "${link.name} $tags [$size]",
                        link.url,
                        link.referer,
                        link.quality,
                        link.type,
                        link.headers,
                        link.extractorData
                    )
                )
            }
        }
}

suspend fun bypassBqrecipes(url: String): String? {
    var res = app.get(url)
    var location = res.text.substringAfter(".replace('").substringBefore("');")
    var cookies = res.cookies
    res = app.get(location, cookies = cookies)
    cookies = cookies + res.cookies
    val document = res.document
    location = document.select("form#recaptcha").attr("action")
    val data =
        document.select("form#recaptcha input").associate { it.attr("name") to it.attr("value") }
    res = app.post(location, data = data, cookies = cookies)
    location = res.document.selectFirst("a#messagedown")?.attr("href") ?: return null
    cookies = (cookies + res.cookies).minus("var")
    return app.get(location, cookies = cookies, allowRedirects = false).headers["location"]
}

suspend fun bypassOuo(url: String?): String? {
    var res = session.get(url ?: return null)
    run lit@{
        (1..2).forEach { _ ->
            if (res.headers["location"] != null) return@lit
            val document = res.document
            val nextUrl = document.select("form").attr("action")
            val data = document.select("form input").mapNotNull {
                it.attr("name") to it.attr("value")
            }.toMap().toMutableMap()
            val captchaKey =
                document.select("script[src*=https://www.google.com/recaptcha/api.js?render=]")
                    .attr("src").substringAfter("render=")
            val token = getCaptchaToken(url, captchaKey)
            data["x-token"] = token ?: ""
            res = session.post(
                nextUrl,
                data = data,
                headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                allowRedirects = false
            )
        }
    }

    return res.headers["location"]
}

suspend fun bypassFdAds(url: String?): String? {
    val directUrl =
        app.get(url ?: return null, verify = false).document.select("a#link").attr("href")
            .substringAfter("/go/")
            .let { base64Decode(it) }
    val doc = app.get(directUrl, verify = false).document
    val lastDoc = app.post(
        doc.select("form#landing").attr("action"),
        data = mapOf("go" to doc.select("form#landing input").attr("value")),
        verify = false
    ).document
    val json = lastDoc.select("form#landing input[name=newwpsafelink]").attr("value")
        .let { base64Decode(it) }
    val finalJson =
        tryParseJson<FDAds>(json)?.linkr?.substringAfter("redirect=")?.let { base64Decode(it) }
    return tryParseJson<Safelink>(finalJson)?.safelink
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

suspend fun getTvMoviesServer(url: String, season: Int?, episode: Int?): Pair<String, String?>? {

    val req = app.get(url)
    if (!req.isSuccessful) return null
    val doc = req.document

    return if (season == null) {
        doc.select("table.wp-block-table tr:last-child td:first-child").text() to
                doc.selectFirst("table.wp-block-table tr a")?.attr("href").let { link ->
                    app.get(link ?: return null).document.select("div#text-url a")
                        .mapIndexed { index, element ->
                            element.attr("href") to element.parent()?.textNodes()?.getOrNull(index)
                                ?.text()
                        }.filter { it.second?.contains("Subtitles", true) == false }
                        .map { it.first }
                }.lastOrNull()
    } else {
        doc.select("div.vc_tta-panels div#Season-$season table.wp-block-table tr:last-child td:first-child")
            .text() to
                doc.select("div.vc_tta-panels div#Season-$season table.wp-block-table tr a")
                    .mapNotNull { ele ->
                        app.get(ele.attr("href")).document.select("div#text-url a")
                            .mapIndexed { index, element ->
                                element.attr("href") to element.parent()?.textNodes()
                                    ?.getOrNull(index)?.text()
                            }.find { it.second?.contains("Episode $episode", true) == true }?.first
                    }.lastOrNull()
    }
}

suspend fun getSfServer() = sfServer ?: fetchSfServer().also { sfServer = it }

suspend fun fetchSfServer(): String {
    return app.get("https://raw.githubusercontent.com/hexated/cloudstream-resources/main/sfmovies_server").text
}

suspend fun getFilmxyCookies(url: String) =
    filmxyCookies ?: fetchFilmxyCookies(url).also { filmxyCookies = it }

suspend fun fetchFilmxyCookies(url: String): Map<String, String> {

    val defaultCookies =
        mutableMapOf("G_ENABLED_IDPS" to "google", "true_checker" to "1", "XID" to "1")
    session.get(
        url,
        headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        ),
        cookies = defaultCookies,
    )
    val phpsessid = session.baseClient.cookieJar.loadForRequest(url.toHttpUrl())
        .first { it.name == "PHPSESSID" }.value
    defaultCookies["PHPSESSID"] = phpsessid

    val userNonce =
        app.get(
            "$filmxyAPI/login/?redirect_to=$filmxyAPI/",
            cookies = defaultCookies
        ).document.select("script")
            .find { it.data().contains("var userNonce") }?.data()?.let {
                Regex("var\\suserNonce.*?\"(\\S+?)\";").find(it)?.groupValues?.get(1)
            }

    val cookieUrl = "${filmxyAPI}/wp-admin/admin-ajax.php"

    session.post(
        cookieUrl,
        data = mapOf(
            "action" to "guest_login",
            "nonce" to "$userNonce",
        ),
        headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
        ),
        cookies = defaultCookies
    )
    val cookieJar = session.baseClient.cookieJar.loadForRequest(cookieUrl.toHttpUrl())
        .associate { it.name to it.value }.toMutableMap()

    return cookieJar.plus(defaultCookies)
}

fun Document.findTvMoviesIframe(): String? {
    return this.selectFirst("script:containsData(var seconds)")?.data()?.substringAfter("href='")
        ?.substringBefore("'>")
}

//modified code from https://github.com/jmir1/aniyomi-extensions/blob/master/src/all/kamyroll/src/eu/kanade/tachiyomi/animeextension/all/kamyroll/AccessTokenInterceptor.kt
suspend fun getCrunchyrollToken(): CrunchyrollAccessToken {
    val client = app.baseClient.newBuilder()
        .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("cr-unblocker.us.to", 1080)))
        .build()

    Authenticator.setDefault(object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication("crunblocker", "crunblocker".toCharArray())
        }
    })

    val request = requestCreator(
        method = "POST",
        url = "$crunchyrollAPI/auth/v1/token",
        headers = mapOf(
            "User-Agent" to "Crunchyroll/3.26.1 Android/11 okhttp/4.9.2",
            "Content-Type" to "application/x-www-form-urlencoded",
            "Authorization" to "Basic ${BuildConfig.CRUNCHYROLL_BASIC_TOKEN}"
        ),
        data = mapOf(
            "refresh_token" to app.get(BuildConfig.CRUNCHYROLL_REFRESH_TOKEN).text,
            "grant_type" to "refresh_token",
            "scope" to "offline_access"
        )
    )

    val token = tryParseJson<CrunchyrollToken>(client.newCall(request).execute().body.string())
    val headers = mapOf("Authorization" to "${token?.tokenType} ${token?.accessToken}")
    val cms =
        app.get("$crunchyrollAPI/index/v2", headers = headers).parsedSafe<CrunchyrollToken>()?.cms
    return CrunchyrollAccessToken(
        token?.accessToken,
        token?.tokenType,
        cms?.bucket,
        cms?.policy,
        cms?.signature,
        cms?.key_pair_id,
    )
}

suspend fun getCrunchyrollId(aniId: String?): String? {
    val query = """
        query media(${'$'}id: Int, ${'$'}type: MediaType, ${'$'}isAdult: Boolean) {
          Media(id: ${'$'}id, type: ${'$'}type, isAdult: ${'$'}isAdult) {
            id
            externalLinks {
              id
              site
              url
              type
            }
          }
        }
    """.trimIndent().trim()

    val variables = mapOf(
        "id" to aniId,
        "isAdult" to false,
        "type" to "ANIME",
    )

    val data = mapOf(
        "query" to query,
        "variables" to variables
    ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

    val externalLinks = app.post(anilistAPI, requestBody = data)
        .parsedSafe<AnilistResponses>()?.data?.Media?.externalLinks

    return (externalLinks?.find { it.site == "VRV" }
        ?: externalLinks?.find { it.site == "Crunchyroll" })?.url?.let {
        app.get(it).url.substringAfter("/series/").substringBefore("/")
    }
}

suspend fun getCrunchyrollIdFromMalSync(aniId: String?): String? {
    val res = app.get("$malsyncAPI/mal/anime/$aniId").parsedSafe<MalSyncRes>()?.Sites
    val vrv = res?.get("Vrv")?.map { it.value }?.firstOrNull()?.get("url")
    val crunchyroll = res?.get("Vrv")?.map { it.value }?.firstOrNull()?.get("url")
    val regex = Regex("series/(\\w+)/?")
    return regex.find("$vrv")?.groupValues?.getOrNull(1)
        ?: regex.find("$crunchyroll")?.groupValues?.getOrNull(1)
}

suspend fun String.haveDub(referer: String) : Boolean {
    return app.get(this,referer=referer).text.contains("TYPE=AUDIO")
}

suspend fun convertTmdbToAnimeId(
    title: String?,
    date: String?,
    airedDate: String?,
    type: TvType
): AniIds {
    val sDate = date?.split("-")
    val sAiredDate = airedDate?.split("-")

    val year = sDate?.firstOrNull()?.toIntOrNull()
    val airedYear = sAiredDate?.firstOrNull()?.toIntOrNull()
    val season = getSeason(sDate?.get(1)?.toIntOrNull())
    val airedSeason = getSeason(sAiredDate?.get(1)?.toIntOrNull())

    return if (type == TvType.AnimeMovie) {
        tmdbToAnimeId(title, airedYear, "", type)
    } else {
        val ids = tmdbToAnimeId(title, year, season, type)
        if (ids.id == null && ids.idMal == null) tmdbToAnimeId(
            title,
            airedYear,
            airedSeason,
            type
        ) else ids
    }
}

suspend fun tmdbToAnimeId(title: String?, year: Int?, season: String?, type: TvType): AniIds {
    val query = """
        query (
          ${'$'}page: Int = 1
          ${'$'}search: String
          ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]
          ${'$'}type: MediaType
          ${'$'}season: MediaSeason
          ${'$'}seasonYear: Int
          ${'$'}format: [MediaFormat]
        ) {
          Page(page: ${'$'}page, perPage: 20) {
            media(
              search: ${'$'}search
              sort: ${'$'}sort
              type: ${'$'}type
              season: ${'$'}season
              seasonYear: ${'$'}seasonYear
              format_in: ${'$'}format
            ) {
              id
              idMal
            }
          }
        }
    """.trimIndent().trim()

    val variables = mapOf(
        "search" to title,
        "sort" to "SEARCH_MATCH",
        "type" to "ANIME",
        "season" to season?.uppercase(),
        "seasonYear" to year,
        "format" to listOf(if (type == TvType.AnimeMovie) "MOVIE" else "TV", "ONA")
    ).filterValues { value -> value != null && value.toString().isNotEmpty() }
    val data = mapOf(
        "query" to query,
        "variables" to variables
    ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
    val res = app.post(anilistAPI, requestBody = data)
        .parsedSafe<AniSearch>()?.data?.Page?.media?.firstOrNull()
    Log.d("Phisher", res?.idMal.toString())
    return AniIds(res?.id, res?.idMal)

}

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = String(base64Decode(m.split("").reversed().joinToString("")).toCharArray())
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}



suspend fun loadCustomTagExtractor(
    tag: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        callback.invoke(
            ExtractorLink(
                link.source,
                "${link.name} $tag",
                link.url,
                link.referer,
                when (link.type) {
                    ExtractorLinkType.M3U8 -> link.quality
                    else -> quality ?: link.quality
                },
                link.type,
                link.headers,
                link.extractorData
            )
        )
    }
}

fun loadNameExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int,
) {
        callback.invoke(
            ExtractorLink(
                name ?: "",
                name ?: "",
                url,
                referer ?: "",
                quality,
                if (url.contains("m3u8"))ExtractorLinkType.M3U8 else INFER_TYPE,
            )
        )
}

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        callback.invoke(
            ExtractorLink(
                "$source[${link.source}]",
                "$source[${link.source}]",
                link.url,
                link.referer,
                link.quality,
                link.type,
                link.headers,
                link.extractorData
            )
        )
    }
}

suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        callback.invoke(
            ExtractorLink(
                name ?: link.source,
                name ?: link.name,
                link.url,
                link.referer,
                when {
                    link.name == "VidSrc" -> Qualities.P1080.value
                    link.type == ExtractorLinkType.M3U8 -> link.quality
                    else -> quality ?: link.quality
                },
                link.type,
                link.headers,
                link.extractorData
            )
        )
    }
}

fun getSeason(month: Int?): String? {
    val seasons = arrayOf(
        "Winter", "Winter", "Spring", "Spring", "Spring", "Summer",
        "Summer", "Summer", "Fall", "Fall", "Fall", "Winter"
    )
    if (month == null) return null
    return seasons[month - 1]
}

fun getEpisodeSlug(
    season: Int? = null,
    episode: Int? = null,
): Pair<String, String> {
    return if (season == null && episode == null) {
        "" to ""
    } else {
        (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
    }
}

fun getTitleSlug(title: String? = null): Pair<String?, String?> {
    val slug = title.createSlug()
    return slug?.replace("-", "\\W") to title?.replace(" ", "_")
}

fun getIndexQuery(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null
): String {
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    return (if (season == null) {
        "$title ${year ?: ""}"
    } else {
        "$title S${seasonSlug}E${episodeSlug}"
    }).trim()
}

fun searchIndex(
    title: String? = null,
    season: Int? = null,
    episode: Int? = null,
    year: Int? = null,
    response: String,
    isTrimmed: Boolean = true,
): List<IndexMedia>? {
    val files = tryParseJson<IndexSearch>(response)?.data?.files?.filter { media ->
        matchingIndex(
            media.name ?: return null,
            media.mimeType ?: return null,
            title ?: return null,
            year,
            season,
            episode
        )
    }?.distinctBy { it.name }?.sortedByDescending { it.size?.toLongOrNull() ?: 0 } ?: return null

    return if (isTrimmed) {
        files.let { file ->
            listOfNotNull(
                file.find { it.name?.contains("2160p", true) == true },
                file.find { it.name?.contains("1080p", true) == true }
            )
        }
    } else {
        files
    }
}

fun matchingIndex(
    mediaName: String?,
    mediaMimeType: String?,
    title: String?,
    year: Int?,
    season: Int?,
    episode: Int?,
    include720: Boolean = false
): Boolean {
    val (wSlug, dwSlug) = getTitleSlug(title)
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    return (if (season == null) {
        mediaName?.contains(Regex("(?i)(?:$wSlug|$dwSlug).*$year")) == true
    } else {
        mediaName?.contains(Regex("(?i)(?:$wSlug|$dwSlug).*S${seasonSlug}.?E${episodeSlug}")) == true
    }) && mediaName?.contains(
        if (include720) Regex("(?i)(2160p|1080p|720p)") else Regex("(?i)(2160p|1080p)")
    ) == true && ((mediaMimeType in mimeType) || mediaName.contains(Regex("\\.mkv|\\.mp4|\\.avi")))
}

fun decodeIndexJson(json: String): String {
    val slug = json.reversed().substring(24)
    return base64Decode(slug.substring(0, slug.length - 20))
}

fun String.xorDecrypt(key: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        var j = 0
        while (j < key.length && i < this.length) {
            sb.append((this[i].code xor key[j].code).toChar())
            j++
            i++
        }
    }
    return sb.toString()
}

//

fun VidsrcCCEncode(input: String): String {
    val key = "78B22E5E862BC"
    val encrypted = rc4(key, input)
    val base64Encoded = base64Encode(encrypted.encodeToByteArray())
    return urlEncode(base64Encoded)
}

private fun rc4(key: String, input: String): String {
    val s = IntArray(256) { it }
    val k = IntArray(256)

    for (i in key.indices) {
        k[i] = key[i].code
    }

    var j = 0
    for (i in 0 until 256) {
        j = (j + s[i] + k[i % key.length]) % 256
        s[i] = s[j].also { s[j] = s[i] }
    }

    val result = StringBuilder()
    var i = 0
    j = 0
    for (char in input) {
        i = (i + 1) % 256
        j = (j + s[i]) % 256
        s[i] = s[j].also { s[j] = s[i] }
        val k = s[(s[i] + s[j]) % 256]
        result.append((char.code xor k).toChar())
    }
    return result.toString()
}

private fun urlEncode(input: String): String {
    return URLEncoder.encode(input, Charsets.UTF_8.name())
}

fun encodeURIComponent(value: String): String {
    return URLEncoder.encode(value, Charsets.UTF_8.name())
}

//

fun vidsrctoDecrypt(text: String): String {
    val parse = base64DecodeArray(text)
    val cipher = Cipher.getInstance("RC4")
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec("78B22E5E862BC".toByteArray(), "RC4"),
        cipher.parameters
    )
    return decode(cipher.doFinal(parse).toString(Charsets.UTF_8))
}

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun getLanguage(str: String): String {
    return if (str.contains("(in_ID)")) "Indonesian" else str
}

fun bytesToGigaBytes(number: Double): Double = number / 1024000000

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}

fun String.getFileSize(): Float? {
    val size = Regex("(?i)(\\d+\\.?\\d+\\sGB|MB)").find(this)?.groupValues?.get(0)?.trim()
    val num = Regex("(\\d+\\.?\\d+)").find(size ?: return null)?.groupValues?.get(0)?.toFloat()
        ?: return null
    return when {
        size.contains("GB") -> num * 1000000
        else -> num * 1000
    }
}

fun getUhdTags(str: String?): String {
    return Regex("\\d{3,4}[Pp]\\.?(.*?)\\[").find(str ?: "")?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim()
        ?: str ?: ""
}

fun getIndexQualityTags(str: String?, fullTag: Boolean = false): String {
    return if (fullTag) Regex("(?i)(.*)\\.(?:mkv|mp4|avi)").find(str ?: "")?.groupValues?.get(1)
        ?.trim() ?: str ?: "" else Regex("(?i)\\d{3,4}[pP]\\.?(.*?)\\.(mkv|mp4|avi)").find(
        str ?: ""
    )?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim() ?: str ?: ""
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

fun getIndexSize(str: String?): String? {
    return Regex("(?i)([\\d.]+\\s*(?:gb|mb))").find(str ?: "")?.groupValues?.getOrNull(1)?.trim()
}

suspend fun extractMdrive(url: String): List<String> =
    app.get(url).document.select("a[href]").mapNotNull {
        it.takeIf { a -> a.attr("href").contains(Regex("hubcloud|gdflix", RegexOption.IGNORE_CASE)) }?.attr("href")
}



fun getQuality(str: String): Int {
    return when (str) {
        "360p" -> Qualities.P240.value
        "480p" -> Qualities.P360.value
        "720p" -> Qualities.P480.value
        "1080p" -> Qualities.P720.value
        "1080p Ultra" -> Qualities.P1080.value
        else -> getQualityFromName(str)
    }
}

fun getGMoviesQuality(str: String): Int {
    return when {
        str.contains("480P", true) -> Qualities.P480.value
        str.contains("720P", true) -> Qualities.P720.value
        str.contains("1080P", true) -> Qualities.P1080.value
        str.contains("4K", true) -> Qualities.P2160.value
        else -> Qualities.Unknown.value
    }
}

fun getFDoviesQuality(str: String): String {
    return when {
        str.contains("1080P", true) -> "1080P"
        str.contains("4K", true) -> "4K"
        else -> ""
    }
}

fun getVipLanguage(str: String): String {
    return when (str) {
        "in_ID" -> "Indonesian"
        "pt" -> "Portuguese"
        else -> str.split("_").first().let {
            SubtitleHelper.fromTwoLettersToLanguage(it).toString()
        }
    }
}

fun fixCrunchyrollLang(language: String?): String? {
    return SubtitleHelper.fromTwoLettersToLanguage(language ?: return null)
        ?: SubtitleHelper.fromTwoLettersToLanguage(language.substringBefore("-"))
}

fun getDeviceId(length: Int = 16): String {
    val allowedChars = ('a'..'f') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun String.encodeUrl(): String {
    val url = URL(this)
    val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
    return uri.toURL().toString()
}

fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}


fun String.getHost(): String {
    return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }
}

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calender = Calendar.getInstance()
    val today = formatter.format(calender.time)
    calender.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calender.time)
    return TmdbDate(today, nextWeek)
}

fun decode(input: String): String = URLDecoder.decode(input, "utf-8")

fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")

fun base64DecodeAPI(api: String): String {
    return api.chunked(4).map { base64Decode(it) }.reversed().joinToString("")
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

fun Int.toRomanNumeral(): String = Symbol.closestBelow(this)
    .let { symbol ->
        if (symbol != null) {
            "$symbol${(this - symbol.decimalValue).toRomanNumeral()}"
        } else {
            ""
        }
    }

private enum class Symbol(val decimalValue: Int) {
    I(1),
    IV(4),
    V(5),
    IX(9),
    X(10);

    companion object {
        fun closestBelow(value: Int) =
            entries.toTypedArray()
                .sortedByDescending { it.decimalValue }
                .firstOrNull { value >= it.decimalValue }
    }
}

object AniwaveUtils {

    fun vrfEncrypt(input: String): String {
        val rc4Key = SecretKeySpec("tGn6kIpVXBEUmqjD".toByteArray(), "RC4")
        val cipher = Cipher.getInstance("RC4")
        cipher.init(Cipher.DECRYPT_MODE, rc4Key, cipher.parameters)

        var vrf = cipher.doFinal(input.toByteArray())
        vrf = base64Encode(vrf).encodeToByteArray()
        vrf = vrfShift(vrf)
        // vrf = rot13(vrf)
        vrf = vrf.reversed().toByteArray()
        vrf = base64Encode(vrf).encodeToByteArray()
        val stringVrf = vrf.toString(Charsets.UTF_8)
        return "vrf=${stringVrf.encodeUri()}"
    }

    fun vrfDecrypt(input: String): String {
        var vrf = base64DecodeArray(input)

        val rc4Key = SecretKeySpec("LUyDrL4qIxtIxOGs".toByteArray(), "RC4")
        val cipher = Cipher.getInstance("RC4")
        cipher.init(Cipher.DECRYPT_MODE, rc4Key, cipher.parameters)
        vrf = cipher.doFinal(vrf)

        return URLDecoder.decode(vrf.toString(Charsets.UTF_8), "utf-8")
    }

    private fun rot13(vrf: ByteArray): ByteArray {
        for (i in vrf.indices) {
            val byte = vrf[i]
            if (byte in 'A'.code..'Z'.code) {
                vrf[i] = ((byte - 'A'.code + 13) % 26 + 'A'.code).toByte()
            } else if (byte in 'a'.code..'z'.code) {
                vrf[i] = ((byte - 'a'.code + 13) % 26 + 'a'.code).toByte()
            }
        }
        return vrf
    }

    private fun vrfShift(vrf: ByteArray): ByteArray {
        for (i in vrf.indices) {
            val shift = arrayOf(-2, -4, -5, 6, 2, -3, 3, 6)[i % 8]
            vrf[i] = vrf[i].plus(shift).toByte()
        }
        return vrf
    }
}

object DumpUtils {

    private val deviceId = getDeviceId()

    suspend fun queryApi(method: String, url: String, params: Map<String, String>): String {
        return app.custom(
            method,
            url,
            requestBody = if (method == "POST") params.toJson()
                .toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull()) else null,
            params = if (method == "GET") params else emptyMap(),
            headers = createHeaders(params)
        ).parsedSafe<HashMap<String, String>>()?.get("data").let {
            cryptoHandler(
                it.toString(),
                deviceId,
                false
            )
        }
    }

    private fun createHeaders(
        params: Map<String, String>,
        currentTime: String = System.currentTimeMillis().toString(),
    ): Map<String, String> {
        return mapOf(
            "lang" to "en",
            "currentTime" to currentTime,
            "sign" to getSign(currentTime, params).toString(),
            "aesKey" to getAesKey().toString(),
        )
    }

    private fun cryptoHandler(
        string: String,
        secretKeyString: String,
        encrypt: Boolean = true
    ): String {
        val secretKey = SecretKeySpec(secretKeyString.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
        return if (!encrypt) {
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            String(cipher.doFinal(base64DecodeArray(string)))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            base64Encode(cipher.doFinal(string.toByteArray()))
        }
    }

    private fun getAesKey(): String? {
        val publicKey =
            RSAEncryptionHelper.getPublicKeyFromString(BuildConfig.DUMP_KEY) ?: return null
        return RSAEncryptionHelper.encryptText(deviceId, publicKey)
    }

    private fun getSign(currentTime: String, params: Map<String, String>): String? {
        val chipper = listOf(
            currentTime,
            params.map { it.value }.reversed().joinToString("")
                .let { base64Encode(it.toByteArray()) }).joinToString("")
        val enc = cryptoHandler(chipper, deviceId)
        return md5(enc)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

}

object RSAEncryptionHelper {

    private const val RSA_ALGORITHM = "RSA"
    private const val CIPHER_TYPE_FOR_RSA = "RSA/ECB/PKCS1Padding"

    private val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
    private val cipher = Cipher.getInstance(CIPHER_TYPE_FOR_RSA)

    fun getPublicKeyFromString(publicKeyString: String): PublicKey? =
        try {
            val keySpec =
                X509EncodedKeySpec(base64DecodeArray(publicKeyString))
            keyFactory.generatePublic(keySpec)
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }

    fun getPrivateKeyFromString(privateKeyString: String): PrivateKey? =
        try {
            val keySpec =
                PKCS8EncodedKeySpec(base64DecodeArray(privateKeyString))
            keyFactory.generatePrivate(keySpec)
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }

    fun encryptText(plainText: String, publicKey: PublicKey): String? =
        try {
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            base64Encode(cipher.doFinal(plainText.toByteArray()))
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }

    fun decryptText(encryptedText: String, privateKey: PrivateKey): String? =
        try {
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            String(cipher.doFinal(base64DecodeArray(encryptedText)))
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }
}

// code found on https://stackoverflow.com/a/63701411

/**
 * Conforming with CryptoJS AES method
 */
// see https://gist.github.com/thackerronak/554c985c3001b16810af5fc0eb5c358f
@Suppress("unused", "FunctionName", "SameParameterValue")
object CryptoJS {

    private const val KEY_SIZE = 256
    private const val IV_SIZE = 128
    private const val HASH_CIPHER = "AES/CBC/PKCS7Padding"
    private const val AES = "AES"
    private const val KDF_DIGEST = "MD5"

    // Seriously crypto-js, what's wrong with you?
    private const val APPEND = "Salted__"

    /**
     * Encrypt
     * @param password passphrase
     * @param plainText plain string
     */
    fun encrypt(password: String, plainText: String): String {
        val saltBytes = generateSalt(8)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        EvpKDF(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)
        val keyS = SecretKeySpec(key, AES)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keyS, ivSpec)
        val cipherText = cipher.doFinal(plainText.toByteArray())
        // Thanks kientux for this: https://gist.github.com/kientux/bb48259c6f2133e628ad
        // Create CryptoJS-like encrypted!
        val sBytes = APPEND.toByteArray()
        val b = ByteArray(sBytes.size + saltBytes.size + cipherText.size)
        System.arraycopy(sBytes, 0, b, 0, sBytes.size)
        System.arraycopy(saltBytes, 0, b, sBytes.size, saltBytes.size)
        System.arraycopy(cipherText, 0, b, sBytes.size + saltBytes.size, cipherText.size)
        val bEncode = base64Encode(b).encodeToByteArray()
        return String(bEncode)
    }

    /**
     * Decrypt
     * Thanks Artjom B. for this: http://stackoverflow.com/a/29152379/4405051
     * @param password passphrase
     * @param cipherText encrypted string
     */
    fun decrypt(password: String, cipherText: String): String {
        val ctBytes = base64DecodeArray(cipherText)
        val saltBytes = Arrays.copyOfRange(ctBytes, 8, 16)
        val cipherTextBytes = Arrays.copyOfRange(ctBytes, 16, ctBytes.size)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        EvpKDF(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val keyS = SecretKeySpec(key, AES)
        cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(iv))
        val plainText = cipher.doFinal(cipherTextBytes)
        return String(plainText)
    }

    private fun EvpKDF(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        resultKey: ByteArray,
        resultIv: ByteArray
    ): ByteArray {
        return EvpKDF(password, keySize, ivSize, salt, 1, KDF_DIGEST, resultKey, resultIv)
    }

    @Suppress("NAME_SHADOWING")
    private fun EvpKDF(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        iterations: Int,
        hashAlgorithm: String,
        resultKey: ByteArray,
        resultIv: ByteArray
    ): ByteArray {
        val keySize = keySize / 32
        val ivSize = ivSize / 32
        val targetKeySize = keySize + ivSize
        val derivedBytes = ByteArray(targetKeySize * 4)
        var numberOfDerivedWords = 0
        var block: ByteArray? = null
        val hash = MessageDigest.getInstance(hashAlgorithm)
        while (numberOfDerivedWords < targetKeySize) {
            if (block != null) {
                hash.update(block)
            }
            hash.update(password)
            block = hash.digest(salt)
            hash.reset()
            // Iterations
            for (i in 1 until iterations) {
                block = hash.digest(block!!)
                hash.reset()
            }
            System.arraycopy(
                block!!, 0, derivedBytes, numberOfDerivedWords * 4,
                min(block.size, (targetKeySize - numberOfDerivedWords) * 4)
            )
            numberOfDerivedWords += block.size / 4
        }
        System.arraycopy(derivedBytes, 0, resultKey, 0, keySize * 4)
        System.arraycopy(derivedBytes, keySize * 4, resultIv, 0, ivSize * 4)
        return derivedBytes // key + iv
    }

    private fun generateSalt(length: Int): ByteArray {
        return ByteArray(length).apply {
            SecureRandom().nextBytes(this)
        }
    }
}

object AESGCM {
    fun ByteArray.decrypt(pass: String): String {
        val (key, iv) = generateKeyAndIv(pass)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(this), StandardCharsets.UTF_8)
    }

    private fun generateKeyAndIv(pass: String): Pair<ByteArray, ByteArray> {
        val datePart = getCurrentUTCDateString().take(16)
        val hexString = datePart + pass
        val byteArray = hexString.toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(byteArray)
        return digest.copyOfRange(0, digest.size / 2) to digest.copyOfRange(
            digest.size / 2,
            digest.size
        )
    }

    private fun getCurrentUTCDateString(): String {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        return dateFormat.format(Date())
    }
}

suspend fun extractbollytag(url:String): String {
    val tagdoc= app.get(url).text
    val tags ="""\b\d{3,4}p\b""".toRegex().find(tagdoc)?.value?.trim() ?:""
    return tags
}

suspend fun extractbollytag2(url:String): String {
    val tagdoc= app.get(url).text
    val tags ="""\b\d{3,4}p\b\s(.*?)\[""".toRegex().find(tagdoc)?.groupValues?.get(1)?.trim() ?:""
    return tags
}

suspend fun extracttopmoviestag(url:String): String? {
    val tagdoc= app.get(url).text
    val tags ="""\b\d{3,4}p\b""".toRegex().find(tagdoc)?.value?.trim() ?:""
    return tags
}
suspend fun extracttopmoviestag2(url:String): String? {
    val tagdoc= app.get(url).text
    val tags ="""\b\d{3,4}p\b\s(.*?)\[""".toRegex().find(tagdoc)?.groupValues?.get(1)?.trim() ?:""
    return tags
}

suspend fun decodesmashy(url:String): String {
    val doc= app.get(url, referer = "https://smashystream.xyz/").document
    val string=doc.toString().substringAfter("#2").substringBefore("\"").replace(Regex("//.{16}"), "").let { DecodeBase64(it) }
    return string
}

fun DecodeBase64(encodedString: String): String {
    // Decode the Base64 encoded string into a byte array
    val decodedBytes = base64DecodeArray(encodedString)
    // Convert the byte array into a string
    return String(decodedBytes)
}

//Catflix

fun CathexToBinary(hex: String): String {
    val binary = StringBuilder()
    for (i in hex.indices step 2) {
        val hexPair = hex.substring(i, i + 2)
        val charValue = hexPair.toInt(16).toChar()
        binary.append(charValue)
    }
    return binary.toString()
}

fun CatxorDecrypt(binary: String, key: String): String {
    val decrypted = StringBuilder()
    val keyLength = key.length

    for (i in binary.indices) {
        val decryptedChar = binary[i].code xor key[i % keyLength].code
        decrypted.append(decryptedChar.toChar())
    }

    return decrypted.toString()
}

fun CatdecryptHexWithKey(hex: String, key: String): String {
    val binary = CathexToBinary(hex)
    return CatxorDecrypt(binary, key)
}

fun getfullURL(url: String,mainUrl:String): String {
    return "$mainUrl$url"
}


fun getRiveSecretKey(e: Int?,c : List<String>): String {
    return e?.let { c[it % c.size] } ?: "rive"
}

fun decryptBase64BlowfishEbc(base64Encrypted: String, key: String): String {
    try {
        val encryptedBytes =  base64DecodeArray(base64Encrypted)
        val secretKeySpec = SecretKeySpec(key.toByteArray(), "Blowfish")
        val cipher = Cipher.getInstance("Blowfish/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    } catch (e: Exception) {
        e.printStackTrace()
        return "Decryption failed: ${e.message}"
    }
}

// Decrypt Links using Blowfish
fun decryptLinks(data: String): List<String> {
    val key = data.substring(data.length - 10)
    val ct = data.substring(0, data.length - 10)
    val pt = decryptBase64BlowfishEbc(ct, key)
    return pt.chunked(5)
}

suspend fun loadHindMoviezLinks(
    data: String,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val links = data.split("+")
    links.forEach { item->
        val res = app.get(item, timeout = 30, allowRedirects = true)
        val doc = res.document
        if(res.url.contains("hpage.site"))
        {
            val quality = getVideoQuality(doc.select(".container h2").text())
            val links = doc.select(".container a");
            links.forEach { item->
                callback.invoke(ExtractorLink(
                    "HindMoviez [H-Cloud]",
                    "HindMoviez [H-Cloud]",
                    url = item.attr("href"),
                    "",
                    quality = quality,
                ))
            }
        }
        else if (res.url.contains("hindshare.site"))
        {
            val quality = getVideoQuality(doc.select(".container p:nth-of-type(1) strong").text())
            val links = doc.select(".btn-group a");
            links.forEach { item->
                if(item.text().contains("HCloud"))
                {
                    callback.invoke(ExtractorLink(
                        "HindMoviez [H-Cloud]",
                        "HindMoviez [H-Cloud]",
                        url = item.attr("href"),
                        "",
                        quality = quality,
                    ))
                }
                else if (item.attr("href").contains("hindcdn.site"))
                {
                    val doc = app.get(item.attr("href"), timeout =  30, allowRedirects = true).document
                    val links = doc.select(".container a");
                    links.forEach{ item->
                        val host = if (item.text().lowercase().contains("google")) {item.text()} else {"HindCdn H-Cloud"}
                        callback.invoke(ExtractorLink(
                            "HindMoviez [$host]",
                            "HindMoviez [$host]",
                            url = item.attr("href"),
                            "",
                            quality = quality,
                        ))
                    }
                }
                else if (item.attr("href").contains("gdirect.cloud"))
                {
                    val doc = app.get(item.attr("href"), timeout = 30, allowRedirects = true, referer = "https://hindshare.site/").document
                    val link = doc.select("a")
                    callback.invoke(ExtractorLink(
                        "HindMoviez [GDirect]",
                        "HindMoviez [GDirect]",
                        url = link.attr("href"),
                        "",
                        quality = quality,
                    ))
                }
            }
        }


    }

    return true
}

private fun getVideoQuality(string: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(string ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

object Deobfuscator {
    suspend fun deobfuscateScript(source: String): String? {
        val originalScript = app.get("https://raw.githubusercontent.com/Kohi-den/extensions-source/9328d12fcfca686becfb3068e9d0be95552c536f/lib/synchrony/src/main/assets/synchrony-v2.4.5.1.js").text
        // Sadly needed until QuickJS properly supports module imports:
        // Regex for finding one and two in "export{one as Deobfuscator,two as Transformer};"
        val regex = """export\{(.*) as Deobfuscator,(.*) as Transformer\};""".toRegex()
        val synchronyScript = regex.find(originalScript)?.let { match ->
            val (deob, trans) = match.destructured
            val replacement = "const Deobfuscator = $deob, Transformer = $trans;"
            originalScript.replace(match.value, replacement)
        } ?: return null

        return QuickJs.create().use { engine ->
            engine.evaluate("globalThis.console = { log: () => {}, warn: () => {}, error: () => {}, trace: () => {} };")
            engine.evaluate(synchronyScript)

            engine.set("source", TestInterface::class.java, object : TestInterface { override fun getValue() = source })
            engine.evaluate("new Deobfuscator().deobfuscateSource(source.getValue())") as? String
        }
    }

    @Suppress("unused")
    private interface TestInterface {
        fun getValue(): String
    }
}


suspend fun invokeExternalSource(
    mediaId: Int? = null,
    type: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit,
    token:String? =null,
) {
    val thirdAPI = thrirdAPI
    val fourthAPI = fourthAPI
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    val headers = mapOf("Accept-Language" to "en")
    val shareKey = app.get("$fourthAPI/index/share_link?id=${mediaId}&type=$type", headers = headers)
        .parsedSafe<ER>()?.data?.link?.substringAfterLast("/") ?: return

    val shareRes = app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
        .parsedSafe<ExternalResponse>()?.data ?: return

    val fids = if (season == null) {
        shareRes.fileList
    } else {
        shareRes.fileList?.find { it.fileName.equals("season $season", true) }?.fid?.let { parentId ->
            app.get("$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1", headers = headers)
                .parsedSafe<ExternalResponse>()?.data?.fileList?.filterNotNull()?.filter {
                    it.fileName?.contains("s${seasonSlug}e${episodeSlug}", true) == true
                }
        }
    } ?: return

    fids.apmapIndexed { index, fileList ->
        val superToken = token ?: ""
        Log.d("Phisher", superToken)

        val player = app.get("$thirdAPI/console/video_quality_list?fid=${fileList.fid}&share_key=$shareKey", headers = mapOf("Cookie" to superToken)).text

        val json = try {
            JSONObject(player)
        } catch (e: Exception) {
            Log.e("Phisher", "Invalid JSON response $e")
            return@apmapIndexed
        }
        val htmlContent = json.optString("html", "")
        if (htmlContent.isEmpty()) return@apmapIndexed

        val document: Document = Jsoup.parse(htmlContent)
        val sourcesWithQualities = mutableListOf<Pair<String, String>>()

        document.select("div.file_quality").forEach {
            val url = it.attr("data-url").takeIf { it.isNotEmpty() }
            val quality = it.attr("data-quality").takeIf { it.isNotEmpty() }?.let { if (it == "ORG") "2160p" else it }
            if (url != null && quality != null) {
                sourcesWithQualities.add(url to quality)
            }
        }

        val sourcesJsonArray = JSONArray().apply {
            sourcesWithQualities.forEach { (url, quality) ->
                put(JSONObject().apply {
                    put("file", url)
                    put("label", quality)
                    put("type", "video/mp4")
                })
            }
        }
        val jsonObject = JSONObject().put("sources", sourcesJsonArray)
        listOf(jsonObject.toString()).forEach {
            val parsedSources = tryParseJson<ExternalSourcesWrapper>(it)?.sources ?: return@forEach
            parsedSources.forEach org@{ source ->
                val format = if (source.type == "video/mp4") ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                val label = if (format == ExtractorLinkType.M3U8) "Hls" else "Mp4"
                if (!(source.label == "AUTO" || format == ExtractorLinkType.VIDEO)) return@org

                callback.invoke(
                    ExtractorLink(
                        "SuperStream",
                        "SuperStream [Server ${index + 1}]",
                        source.file?.replace("\\/", "/") ?: return@org,
                        "",
                        getIndexQuality(if (format == ExtractorLinkType.M3U8) fileList.fileName else source.label),
                        type = format,
                    )
                )
            }
        }
    }
}

fun parseJsonToEpisodes(json: String): List<EpisoderesponseKAA> {
    val gson = Gson()
    data class Response(val result: List<EpisoderesponseKAA>)
    val response = gson.fromJson(json, Response::class.java)
    return response.result
}



fun getSignature(
    html: String,
    server: String,
    query: String,
    key: ByteArray
): Triple<String, String, String>? {
    // Define the order based on the server type
    val headers= mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
    val order = when (server) {
        "VidStreaming", "DuckStream" -> listOf("IP", "USERAGENT", "ROUTE", "MID", "TIMESTAMP", "KEY")
        "BirdStream" -> listOf("IP", "USERAGENT", "ROUTE", "MID", "KEY")
        else -> return null
    }

    // Parse the HTML using Jsoup
    val document = Jsoup.parse(html)
    val cidRaw = document.select("script:containsData(cid:)").firstOrNull()
        ?.html()?.substringAfter("cid: '")?.substringBefore("'")?.decodeHex()
        ?: return null
    val cid = String(cidRaw).split("|")

    // Generate timestamp
    val timeStamp = (System.currentTimeMillis() / 1000 + 60).toString()

    // Update route
    val route = cid[1].replace("player.php", "source.php")

    val signature = buildString {
        order.forEach {
            when (it) {
                "IP" -> append(cid[0])
                "USERAGENT" -> append(headers["User-Agent"] ?: "")
                "ROUTE" -> append(route)
                "MID" -> append(query)
                "TIMESTAMP" -> append(timeStamp)
                "KEY" -> append(String(key))
                "SIG" -> append(html.substringAfter("signature: '").substringBefore("'"))
                else -> {}
            }
        }
    }
    // Compute SHA-1 hash of the signature
    return Triple(sha1sum(signature), timeStamp, route)
}

// Helper function to decode a hexadecimal string

private fun sha1sum(value: String): String {
    return try {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(value.toByteArray())
        bytes.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        throw Exception("Attempt to create the signature failed miserably.")
    }
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}


object CryptoAES {

    private const val KEY_SIZE = 32 // 256 bits
    private const val IV_SIZE = 16 // 128 bits
    private const val SALT_SIZE = 8 // 64 bits
    private const val HASH_CIPHER = "AES/CBC/PKCS7PADDING"
    private const val HASH_CIPHER_FALLBACK = "AES/CBC/PKCS5PADDING"
    private const val AES = "AES"
    private const val KDF_DIGEST = "MD5"

    /**
     * Decrypt using CryptoJS defaults compatible method.
     * Uses KDF equivalent to OpenSSL's EVP_BytesToKey function
     *
     * http://stackoverflow.com/a/29152379/4405051
     * @param cipherText base64 encoded ciphertext
     * @param password passphrase
     */
    fun decrypt(cipherText: String, password: String): String {
        return try {
            val ctBytes = base64DecodeArray(cipherText)
            val saltBytes = Arrays.copyOfRange(ctBytes, SALT_SIZE, IV_SIZE)
            val cipherTextBytes = Arrays.copyOfRange(ctBytes, IV_SIZE, ctBytes.size)
            val md5 = MessageDigest.getInstance("MD5")
            val keyAndIV = generateKeyAndIV(KEY_SIZE, IV_SIZE, 1, saltBytes, password.toByteArray(Charsets.UTF_8), md5)
            decryptAES(
                cipherTextBytes,
                keyAndIV?.get(0) ?: ByteArray(KEY_SIZE),
                keyAndIV?.get(1) ?: ByteArray(IV_SIZE),
            )
        } catch (e: Exception) {
            ""
        }
    }

    fun decryptWithSalt(cipherText: String, salt: String, password: String): String {
        return try {
            val ctBytes = base64DecodeArray(cipherText)
            val md5: MessageDigest = MessageDigest.getInstance("MD5")
            val keyAndIV = generateKeyAndIV(
                KEY_SIZE,
                IV_SIZE,
                1,
                salt.decodeHex(),
                password.toByteArray(Charsets.UTF_8),
                md5,
            )
            decryptAES(
                ctBytes,
                keyAndIV?.get(0) ?: ByteArray(KEY_SIZE),
                keyAndIV?.get(1) ?: ByteArray(IV_SIZE),
            )
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Decrypt using CryptoJS defaults compatible method.
     *
     * @param cipherText base64 encoded ciphertext
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    fun decrypt(cipherText: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipherTextBytes = base64DecodeArray(cipherText)
            decryptAES(cipherTextBytes, keyBytes, ivBytes)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Encrypt using CryptoJS defaults compatible method.
     *
     * @param plainText plaintext
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    fun encrypt(plainText: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipherTextBytes = plainText.toByteArray()
            encryptAES(cipherTextBytes, keyBytes, ivBytes)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Decrypt using CryptoJS defaults compatible method.
     *
     * @param cipherTextBytes encrypted text as a bytearray
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    private fun decryptAES(cipherTextBytes: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) { Cipher.getInstance(HASH_CIPHER_FALLBACK) }
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            cipher.doFinal(cipherTextBytes).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Encrypt using CryptoJS defaults compatible method.
     *
     * @param plainTextBytes encrypted text as a bytearray
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    private fun encryptAES(plainTextBytes: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) { Cipher.getInstance(HASH_CIPHER_FALLBACK) }
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.ENCRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            base64Encode(cipher.doFinal(plainTextBytes))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generates a key and an initialization vector (IV) with the given salt and password.
     *
     * https://stackoverflow.com/a/41434590
     * This method is equivalent to OpenSSL's EVP_BytesToKey function
     * (see https://github.com/openssl/openssl/blob/master/crypto/evp/evp_key.c).
     * By default, OpenSSL uses a single iteration, MD5 as the algorithm and UTF-8 encoded password data.
     *
     * @param keyLength the length of the generated key (in bytes)
     * @param ivLength the length of the generated IV (in bytes)
     * @param iterations the number of digestion rounds
     * @param salt the salt data (8 bytes of data or `null`)
     * @param password the password data (optional)
     * @param md the message digest algorithm to use
     * @return an two-element array with the generated key and IV
     */
    private fun generateKeyAndIV(
        keyLength: Int,
        ivLength: Int,
        iterations: Int,
        salt: ByteArray,
        password: ByteArray,
        md: MessageDigest,
    ): Array<ByteArray?>? {
        val digestLength = md.digestLength
        val requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0
        return try {
            md.reset()

            // Repeat process until sufficient data has been generated
            while (generatedLength < keyLength + ivLength) {
                // Digest data (last digest if available, password data, salt if available)
                if (generatedLength > 0) md.update(generatedData, generatedLength - digestLength, digestLength)
                md.update(password)
                md.update(salt, 0, SALT_SIZE)
                md.digest(generatedData, generatedLength, digestLength)

                // additional rounds
                for (i in 1 until iterations) {
                    md.update(generatedData, generatedLength, digestLength)
                    md.digest(generatedData, generatedLength, digestLength)
                }
                generatedLength += digestLength
            }

            // Copy key and IV into separate byte arrays
            val result = arrayOfNulls<ByteArray>(2)
            result[0] = generatedData.copyOfRange(0, keyLength)
            if (ivLength > 0) result[1] = generatedData.copyOfRange(keyLength, keyLength + ivLength)
            result
        } catch (e: Exception) {
            throw e
        } finally {
            // Clean out temporary data
            Arrays.fill(generatedData, 0.toByte())
        }
    }

    // Stolen from AnimixPlay(EN) / GogoCdnExtractor
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}


val languageMap = mapOf(
    "Afrikaans" to Pair("af", "afr"),
    "Albanian" to Pair("sq", "sqi"),
    "Amharic" to Pair("am", "amh"),
    "Arabic" to Pair("ar", "ara"),
    "Armenian" to Pair("hy", "hye"),
    "Azerbaijani" to Pair("az", "aze"),
    "Basque" to Pair("eu", "eus"),
    "Belarusian" to Pair("be", "bel"),
    "Bengali" to Pair("bn", "ben"),
    "Bosnian" to Pair("bs", "bos"),
    "Bulgarian" to Pair("bg", "bul"),
    "Catalan" to Pair("ca", "cat"),
    "Chinese" to Pair("zh", "zho"),
    "Croatian" to Pair("hr", "hrv"),
    "Czech" to Pair("cs", "ces"),
    "Danish" to Pair("da", "dan"),
    "Dutch" to Pair("nl", "nld"),
    "English" to Pair("en", "eng"),
    "Estonian" to Pair("et", "est"),
    "Filipino" to Pair("tl", "tgl"),
    "Finnish" to Pair("fi", "fin"),
    "French" to Pair("fr", "fra"),
    "Galician" to Pair("gl", "glg"),
    "Georgian" to Pair("ka", "kat"),
    "German" to Pair("de", "deu"),
    "Greek" to Pair("el", "ell"),
    "Gujarati" to Pair("gu", "guj"),
    "Hebrew" to Pair("he", "heb"),
    "Hindi" to Pair("hi", "hin"),
    "Hungarian" to Pair("hu", "hun"),
    "Icelandic" to Pair("is", "isl"),
    "Indonesian" to Pair("id", "ind"),
    "Italian" to Pair("it", "ita"),
    "Japanese" to Pair("ja", "jpn"),
    "Kannada" to Pair("kn", "kan"),
    "Kazakh" to Pair("kk", "kaz"),
    "Korean" to Pair("ko", "kor"),
    "Latvian" to Pair("lv", "lav"),
    "Lithuanian" to Pair("lt", "lit"),
    "Macedonian" to Pair("mk", "mkd"),
    "Malay" to Pair("ms", "msa"),
    "Malayalam" to Pair("ml", "mal"),
    "Maltese" to Pair("mt", "mlt"),
    "Marathi" to Pair("mr", "mar"),
    "Mongolian" to Pair("mn", "mon"),
    "Nepali" to Pair("ne", "nep"),
    "Norwegian" to Pair("no", "nor"),
    "Persian" to Pair("fa", "fas"),
    "Polish" to Pair("pl", "pol"),
    "Portuguese" to Pair("pt", "por"),
    "Punjabi" to Pair("pa", "pan"),
    "Romanian" to Pair("ro", "ron"),
    "Russian" to Pair("ru", "rus"),
    "Serbian" to Pair("sr", "srp"),
    "Sinhala" to Pair("si", "sin"),
    "Slovak" to Pair("sk", "slk"),
    "Slovenian" to Pair("sl", "slv"),
    "Spanish" to Pair("es", "spa"),
    "Swahili" to Pair("sw", "swa"),
    "Swedish" to Pair("sv", "swe"),
    "Tamil" to Pair("ta", "tam"),
    "Telugu" to Pair("te", "tel"),
    "Thai" to Pair("th", "tha"),
    "Turkish" to Pair("tr", "tur"),
    "Ukrainian" to Pair("uk", "ukr"),
    "Urdu" to Pair("ur", "urd"),
    "Uzbek" to Pair("uz", "uzb"),
    "Vietnamese" to Pair("vi", "vie"),
    "Welsh" to Pair("cy", "cym"),
    "Yiddish" to Pair("yi", "yid")
)

fun getLanguage(language: String?): String? {
    language ?: return null
    val normalizedLang = language.substringBefore("-")
    return languageMap.entries.find { it.value.first == normalizedLang || it.value.second == normalizedLang }?.key
}




