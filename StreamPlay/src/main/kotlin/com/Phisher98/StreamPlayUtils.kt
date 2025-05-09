package com.phisher98

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import app.cash.quickjs.QuickJs
import com.fasterxml.jackson.databind.ObjectMapper
import com.phisher98.DumpUtils.queryApi
import com.phisher98.StreamPlay.Companion.anilistAPI
import com.phisher98.StreamPlay.Companion.filmxyAPI
import com.phisher98.StreamPlay.Companion.fourthAPI
import com.phisher98.StreamPlay.Companion.gdbot
import com.phisher98.StreamPlay.Companion.hdmovies4uAPI
import com.phisher98.StreamPlay.Companion.malsyncAPI
import com.phisher98.StreamPlay.Companion.thrirdAPI
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
import javax.crypto.SecretKey
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
    val CFlinks = mutableListOf<String>()
    app.get("https://driveleech.org$url?type=1").document.let {
        CFlinks += it.select("div.mb-4 a").attr("href")
    }
    app.get("https://driveleech.org$url?type=2").document.let {
        CFlinks += it.select("div.mb-4 a").attr("href")
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

suspend fun extractMovieAPIlinks(serverid: String, movieid: String, MOVIE_API: String): String {
    val link =
        app.get("$MOVIE_API/ajax/get_stream_link?id=$serverid&movie=$movieid").document.toString()
            .substringAfter("link\":\"").substringBefore("\",")
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
            newExtractorLink(
                "Smashy [$name]",
                "Smashy [$name]",
                trimmedLink,
                INFER_TYPE
            )
            {
                this.quality=getQualityFromName(quality)
            }
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
        .amap { ele ->
            val href = base64Decode(ele.attr("href").substringAfterLast("/")).let {
                if (it.contains("hubcloud.lol")) it.replace("hubcloud.lol", "hubcloud.in") else it
            }
            loadExtractor(href, "$hdmovies4uAPI/", subtitleCallback) { link ->
                CoroutineScope(Dispatchers.IO).launch {
                    callback.invoke(
                        newExtractorLink(
                            link.source,
                            "${link.name} $tags [$size]",
                            link.url,
                        ) {
                            this.referer = link.referer
                            this.quality = link.quality
                            this.type = link.type
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                    )
                }
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
//suspend fun getCrunchyrollToken(): CrunchyrollAccessToken {
//    val client = app.baseClient.newBuilder()
//        .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("cr-unblocker.us.to", 1080)))
//        .build()
//
//    Authenticator.setDefault(object : Authenticator() {
//        override fun getPasswordAuthentication(): PasswordAuthentication {
//            return PasswordAuthentication("crunblocker", "crunblocker".toCharArray())
//        }
//    })
//
//    val request = requestCreator(
//        method = "POST",
//        url = "$crunchyrollAPI/auth/v1/token",
//        headers = mapOf(
//            "User-Agent" to "Crunchyroll/3.26.1 Android/11 okhttp/4.9.2",
//            "Content-Type" to "application/x-www-form-urlencoded",
//            "Authorization" to "Basic ${BuildConfig.CRUNCHYROLL_BASIC_TOKEN}"
//        ),
//        data = mapOf(
//            "refresh_token" to app.get(BuildConfig.CRUNCHYROLL_REFRESH_TOKEN).text,
//            "grant_type" to "refresh_token",
//            "scope" to "offline_access"
//        )
//    )
//
//    val token = tryParseJson<CrunchyrollToken>(client.newCall(request).execute().body.string())
//    val headers = mapOf("Authorization" to "${token?.tokenType} ${token?.accessToken}")
//    val cms =
//        app.get("$crunchyrollAPI/index/v2", headers = headers).parsedSafe<CrunchyrollToken>()?.cms
//    return CrunchyrollAccessToken(
//        token?.accessToken,
//        token?.tokenType,
//        cms?.bucket,
//        cms?.policy,
//        cms?.signature,
//        cms?.key_pair_id,
//    )
//}

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

suspend fun String.haveDub(referer: String): Boolean {
    return app.get(this, referer = referer).text.contains("TYPE=AUDIO")
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
        CoroutineScope(Dispatchers.IO).launch {
            val extractorLink = newExtractorLink(
                link.source,
                "${link.name} $tag",
                link.url,
            ) {
                this.quality = when (link.type) {
                    ExtractorLinkType.M3U8 -> link.quality
                    else -> quality ?: link.quality
                }
                this.type = link.type
                this.headers = link.headers
                this.referer = link.referer
                this.extractorData = link.extractorData
            }
            callback.invoke(extractorLink)
        }
    }
}

suspend fun loadNameExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int,
) {
    callback.invoke(
        newExtractorLink(
            name ?: "",
            name ?: "",
            url,
        )
        {
            this.referer=referer ?:""
            this.quality=quality
            this.type=if (url.contains("m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE!!
        }
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
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "$source[${link.source}]",
                    "$source[${link.source}]",
                    link.url,
                ) {
                    this.quality = link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
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
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
                    link.url,
                ) {
                    this.quality = when {
                        else -> quality ?: link.quality
                    }
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
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

fun String?.createPlayerSlug(): String? {
    return this?.trim()
        ?.lowercase()
        ?.replace(
            "[^a-z0-9\\s-]".toRegex(),
            ""
        ) // Remove special characters except spaces & hyphens
        ?.replace("\\s+".toRegex(), "-") // Replace spaces with hyphens
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

suspend fun extractMdrive(url: String): List<String> {
    val regex = Regex("hubcloud|gdflix|gdlink", RegexOption.IGNORE_CASE)

    return try {
        app.get(url).document
            .select("a[href]")
            .mapNotNull { element ->
                val href = element.attr("href")
                if (regex.containsMatchIn(href)) {
                    href
                } else {
                    null
                }
            }
    } catch (e: Exception) {
        Log.e("Error Mdrive", "Error extracting links: ${e.localizedMessage}")
        emptyList()
    }
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

suspend fun extractbollytag(url: String): String {
    val tagdoc = app.get(url).text
    val tags = """\b\d{3,4}p\b""".toRegex().find(tagdoc)?.value?.trim() ?: ""
    return tags
}

suspend fun extractbollytag2(url: String): String {
    val tagdoc = app.get(url).text
    val tags = """\b\d{3,4}p\b\s(.*?)\[""".toRegex().find(tagdoc)?.groupValues?.get(1)?.trim() ?: ""
    return tags
}

suspend fun extracttopmoviestag(url: String): String? {
    val tagdoc = app.get(url).text
    val tags = """\b\d{3,4}p\b""".toRegex().find(tagdoc)?.value?.trim() ?: ""
    return tags
}

suspend fun extracttopmoviestag2(url: String): String? {
    val tagdoc = app.get(url).text
    val tags = """\b\d{3,4}p\b\s(.*?)\[""".toRegex().find(tagdoc)?.groupValues?.get(1)?.trim() ?: ""
    return tags
}

suspend fun decodesmashy(url: String): String {
    val doc = app.get(url, referer = "https://smashystream.xyz/").document
    val string =
        doc.toString().substringAfter("#2").substringBefore("\"").replace(Regex("//.{16}"), "")
            .let { DecodeBase64(it) }
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

fun getfullURL(url: String, mainUrl: String): String {
    return "$mainUrl$url"
}


fun hashOuter(e: String): String {
    var t: Long = 0L // Use Long here
    for (ch in e) {
        t = (ch.code + (t shl 6) + (t shl 16) - t) and 0xFFFFFFFFL // Use Long type constant
    }
    return t.toString(16) // Convert to hexadecimal
}

fun hashInner(e: String): String {
    var t = e
    var n: Long = 0xDEADBEEFL // Use Long for n
    for (i in t.indices) {
        var r = t[i].code
        r = r xor (17 * i and 0xFF)
        n = (n shl 5 or (n ushr 27)) and 0xFFFFFFFFL // Use Long type constant
        n = n xor r.toLong()
        n = (n * 73244475) and 0xFFFFFFFFL // Use Long type constant
    }
    n = n xor (n ushr 16)
    n = (n * 295559667) and 0xFFFFFFFFL // Use Long type constant
    n = n xor (n ushr 13)
    n = (n * 877262033) and 0xFFFFFFFFL // Use Long type constant
    n = n xor (n ushr 16)
    return n.toString(16).padStart(8, '0') // Return 8-digit hexadecimal string
}

fun generateRiveSecret(e: String?, c: List<String>): String {
    if (e.isNullOrEmpty()) return "rive"

    return try {
        val r = e
        val hashed = hashInner(hashOuter(r)) // Apply the hash functions
        val i = hashed.encodeToByteArray().toBase64() // Base64 encode the result of hashed

        var t: String
        var n: Int

        if (e.toIntOrNull() == null) {  // If the string is not a number
            val sum = r.sumOf { it.code }
            t = c.getOrElse(sum % c.size) { r.encodeToByteArray().toBase64() }
            n = (sum % i.length) / 2
        } else {
            val num = e.toInt()
            t = c.getOrElse(num % c.size) { r.encodeToByteArray().toBase64() }
            n = (num % i.length) / 2
        }

        i.take(n) + t + i.drop(n)
    } catch (err: Exception) {
        "topSecret"
    }
}

// Extension function for Base64 encoding
fun ByteArray.toBase64(): String {
    return android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
}


fun decryptBase64BlowfishEbc(base64Encrypted: String, key: String): String {
    try {
        val encryptedBytes = base64DecodeArray(base64Encrypted)
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


fun loadHindMoviezLinks(
    data: String,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val links = data.split("+")
    val scope = CoroutineScope(Dispatchers.IO)  // Using IO dispatcher for network calls

    links.forEach { item ->
        scope.launch {
            try {
                val res = app.get(item, timeout = 30, allowRedirects = true)
                val doc = res.document
                val size = doc.selectFirst("body > div.container > p:nth-child(3)")?.ownText()
                Log.d("Phisher HH Size",size.toString())
                if (res.url.contains("hpage.site")) {
                    val quality = getVideoQuality(doc.select(".container h2").text())
                    val linkElements = doc.select(".container a")
                    linkElements.forEach { linkItem ->
                        callback.invoke(
                            newExtractorLink(
                                "HindMoviez [H-Cloud] [$size]",
                                "HindMoviez [H-Cloud] [$size]",
                                url = linkItem.attr("href")
                            ) {
                                this.quality = quality
                            }
                        )
                    }
                } else if (res.url.contains("hindshare.site")) {
                    val quality = getVideoQuality(doc.select(".container p:nth-of-type(1) strong").text())
                    val linkElements = doc.select(".btn-group a")
                    linkElements.forEach { linkItem ->
                        if (linkItem.text().contains("HCloud")) {
                            callback.invoke(
                                newExtractorLink(
                                    "HindMoviez [H-Cloud] [$size]",
                                    "HindMoviez [H-Cloud] [$size]",
                                    url = linkItem.attr("href")
                                ) {
                                    this.quality = quality
                                }
                            )
                        } else if (linkItem.attr("href").contains("hindcdn.site")) {
                            val doc = app.get(linkItem.attr("href"), timeout = 30, allowRedirects = true).document
                            val linkElements = doc.select(".container a")
                            linkElements.forEach { item ->
                                val host = if (item.text().lowercase().contains("google")) {
                                    item.text()
                                } else {
                                    "HindCdn H-Cloud"
                                }
                                callback.invoke(
                                    newExtractorLink(
                                        "HindMoviez [$host] [$size]",
                                        "HindMoviez [$host] [$size]",
                                        url = item.attr("href")
                                    ) {
                                        this.quality = quality
                                    }
                                )
                            }
                        } else if (linkItem.attr("href").contains("gdirect.cloud")) {
                            val doc = app.get(
                                linkItem.attr("href"),
                                timeout = 30,
                                allowRedirects = true,
                                referer = "https://hindshare.site/"
                            ).document
                            val link = doc.select("a")
                            callback.invoke(
                                newExtractorLink(
                                    "HindMoviez [GDirect] [$size]",
                                    "HindMoviez [GDirect] [$size]",
                                    url = link.attr("href")
                                ) {
                                    this.quality = quality
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        val originalScript =
            app.get("https://raw.githubusercontent.com/Kohi-den/extensions-source/9328d12fcfca686becfb3068e9d0be95552c536f/lib/synchrony/src/main/assets/synchrony-v2.4.5.1.js").text
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

            engine.set("source", TestInterface::class.java, object : TestInterface {
                override fun getValue() = source
            })
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
    token: String? = null,
) {
    val thirdAPI = thrirdAPI
    val fourthAPI = fourthAPI
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    val headers = mapOf("Accept-Language" to "en")
    val shareKey =
        app.get("$fourthAPI/index/share_link?id=${mediaId}&type=$type", headers = headers)
            .parsedSafe<ER>()?.data?.link?.substringAfterLast("/") ?: return

    val shareRes = app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
        .parsedSafe<ExternalResponse>()?.data ?: return

    val fids = if (season == null) {
        shareRes.fileList
    } else {
        shareRes.fileList?.find {
            it.fileName.equals(
                "season $season",
                true
            )
        }?.fid?.let { parentId ->
            app.get(
                "$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1",
                headers = headers
            )
                .parsedSafe<ExternalResponse>()?.data?.fileList?.filterNotNull()?.filter {
                    it.fileName?.contains("s${seasonSlug}e${episodeSlug}", true) == true
                }
        }
    } ?: return

    fids.amapIndexed { index, fileList ->
        val superToken = token ?: ""

        val player = app.get(
            "$thirdAPI/console/video_quality_list?fid=${fileList.fid}&share_key=$shareKey",
            headers = mapOf("Cookie" to superToken)
        ).text

        val json = try {
            JSONObject(player)
        } catch (e: Exception) {
            Log.e("Error:", "Invalid JSON response $e")
            return@amapIndexed
        }
        val htmlContent = json.optString("html", "")
        if (htmlContent.isEmpty()) return@amapIndexed

        val document: Document = Jsoup.parse(htmlContent)
        val sourcesWithQualities = mutableListOf<Triple<String, String, String>>() // url, quality, size
        document.select("div.file_quality").forEach { element ->
            val url = element.attr("data-url").takeIf { it.isNotEmpty() } ?: return@forEach
            val qualityAttr = element.attr("data-quality").takeIf { it.isNotEmpty() }
            val size = element.selectFirst(".size")?.text()?.takeIf { it.isNotEmpty() } ?: return@forEach

            val quality = if (qualityAttr.equals("ORG", ignoreCase = true)) {
                Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: "2160p"
            } else {
                qualityAttr ?: return@forEach
            }

            sourcesWithQualities.add(Triple(url, quality, size))
        }

        val sourcesJsonArray = JSONArray().apply {
            sourcesWithQualities.forEach { (url, quality, size) ->
                put(JSONObject().apply {
                    put("file", url)
                    put("label", quality)
                    put("type", "video/mp4")
                    put("size", size)
                })
            }
        }
        val jsonObject = JSONObject().put("sources", sourcesJsonArray)
        listOf(jsonObject.toString()).forEach {
            val parsedSources = tryParseJson<ExternalSourcesWrapper>(it)?.sources ?: return@forEach
            parsedSources.forEach org@{ source ->
                val format =
                    if (source.type == "video/mp4") ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                val label = if (format == ExtractorLinkType.M3U8) "Hls" else "Mp4"
                if (!(source.label == "AUTO" || format == ExtractorLinkType.VIDEO)) return@org

                callback.invoke(
                    ExtractorLink(
                        "⌜ SuperStream ⌟ ${source.size}",
                        "⌜ SuperStream ⌟ [Server ${index + 1}] ${source.size}",
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
    val headers =
        mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
    val order = when (server) {
        "VidStreaming", "DuckStream" -> listOf(
            "IP",
            "USERAGENT",
            "ROUTE",
            "MID",
            "TIMESTAMP",
            "KEY"
        )

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
            val keyAndIV = generateKeyAndIV(
                KEY_SIZE,
                IV_SIZE,
                1,
                saltBytes,
                password.toByteArray(Charsets.UTF_8),
                md5
            )
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
    private fun decryptAES(
        cipherTextBytes: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray
    ): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) {
                Cipher.getInstance(HASH_CIPHER_FALLBACK)
            }
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
    private fun encryptAES(
        plainTextBytes: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray
    ): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) {
                Cipher.getInstance(HASH_CIPHER_FALLBACK)
            }
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
                if (generatedLength > 0) md.update(
                    generatedData,
                    generatedLength - digestLength,
                    digestLength
                )
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

suspend fun getPlayer4uUrl(
    name: String,
    selectedQuality: Int,
    url: String,
    referer: String?,
    callback: (ExtractorLink) -> Unit
) {
    val response = app.get(url, referer = referer)
    var script = getAndUnpack(response.text).takeIf { it.isNotEmpty() }
        ?: response.document.selectFirst("script:containsData(sources:)")?.data()
    Log.d("Phisher",script.toString())
    if (script == null) {
        val iframeUrl =
            Regex("""<iframe src="(.*?)"""").find(response.text)?.groupValues?.getOrNull(1)
                ?: return
        val iframeResponse = app.get(
            iframeUrl,
            referer = null,
            headers = mapOf("Accept-Language" to "en-US,en;q=0.5")
        )
        script = getAndUnpack(iframeResponse.text).takeIf { it.isNotEmpty() } ?: return
    }

    val m3u8 = Regex("\"hls2\":\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1).orEmpty()
    callback(newExtractorLink(name, name, m3u8) {
        this.quality=selectedQuality
        this.type=ExtractorLinkType.M3U8
    })

}

fun getPlayer4UQuality(quality: String): Int {
    return when (quality) {
        "4K", "2160P" -> Qualities.P2160.value
        "FHD", "1080P" -> Qualities.P1080.value
        "HQ", "HD", "720P", "DVDRIP", "TVRIP", "HDTC", "PREDVD" -> Qualities.P720.value
        "480P" -> Qualities.P480.value
        "360P", "CAM" -> Qualities.P360.value
        "DS" -> Qualities.P144.value
        "SD" -> Qualities.P480.value
        "WEBRIP" -> Qualities.P720.value
        "BLURAY", "BRRIP" -> Qualities.P1080.value
        "HDRIP" -> Qualities.P1080.value
        "TS" -> Qualities.P480.value
        "R5" -> Qualities.P480.value
        "SCR" -> Qualities.P480.value
        "TC" -> Qualities.P480.value
        else -> Qualities.Unknown.value
    }
}

fun getAnidbEid(jsonString: String, episodeNumber: Int?): Int? {
    if (episodeNumber == null) return null

    return try {
        val jsonObject = JSONObject(jsonString)
        val episodes = jsonObject.optJSONObject("episodes") ?: return null

        episodes.optJSONObject(episodeNumber.toString())
            ?.optInt("anidbEid", -1)
            ?.takeIf { it != -1 }
    } catch (e: Exception) {
        e.printStackTrace() // Logs the error but prevents breaking the app
        null
    }
}


fun getImdbId(jsonString: String): String? {
    val jsonObject = JSONObject(jsonString)
    return jsonObject.optJSONObject("mappings")?.optString("imdb_id", "")
}


fun generateVidsrcVrf(n: Int?): String {
    val secret = "j8MDyaub7B"
    val sha256 = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
    val key: SecretKey = SecretKeySpec(sha256, "AES")
    val iv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
        init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
    }
    val inputBytes = (n?.toString() ?: "").toByteArray()
    val encrypted = cipher.doFinal(inputBytes)

    val ivHex = iv.joinToString("") { "%02x".format(it) }
    val encryptedHex = encrypted.joinToString("") { "%02x".format(it) }
    return "$ivHex:$encryptedHex"
}


internal class AnimekaiDecoder {
    private val keysChar = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-~!*().'"

    /*
    private val tcodex = listOf(
        intArrayOf(125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124),
        intArrayOf(249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248),
        intArrayOf(133, 132, 135, 134, 129, 128, 131, 130, 141, 140, 143, 142, 137, 136, 139, 138, 149, 148, 151, 150, 145, 144, 147, 146, 157, 156, 159, 158, 153, 152, 155, 154, 165, 164, 167, 166, 161, 160, 163, 162, 173, 172, 175, 174, 169, 168, 171, 170, 181, 180, 183, 182, 177, 176, 179, 178, 189, 188, 191, 190, 185, 184, 187, 186, 197, 196, 199, 198, 193, 192, 195, 194, 205, 204, 207, 206, 201, 200, 203, 202, 213, 212, 215, 214, 209, 208, 211, 210, 221, 220, 223, 222, 217, 216, 219, 218, 229, 228, 231, 230, 225, 224, 227, 226, 237, 236, 239, 238, 233, 232, 235, 234, 245, 244, 247, 246, 241, 240, 243, 242, 253, 252, 255, 254, 249, 248, 251, 250, 5, 4, 7, 6, 1, 0, 3, 2, 13, 12, 15, 14, 9, 8, 11, 10, 21, 20, 23, 22, 17, 16, 19, 18, 29, 28, 31, 30, 25, 24, 27, 26, 37, 36, 39, 38, 33, 32, 35, 34, 45, 44, 47, 46, 41, 40, 43, 42, 53, 52, 55, 54, 49, 48, 51, 50, 61, 60, 63, 62, 57, 56, 59, 58, 69, 68, 71, 70, 65, 64, 67, 66, 77, 76, 79, 78, 73, 72, 75, 74, 85, 84, 87, 86, 81, 80, 83, 82, 93, 92, 95, 94, 89, 88, 91, 90, 101, 100, 103, 102, 97, 96, 99, 98, 109, 108, 111, 110, 105, 104, 107, 106, 117, 116, 119, 118, 113, 112, 115, 114, 125, 124, 127, 126, 121, 120, 123, 122),
        intArrayOf(0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 1, 17, 33, 49, 65, 81, 97, 113, 129, 145, 161, 177, 193, 209, 225, 241, 2, 18, 34, 50, 66, 82, 98, 114, 130, 146, 162, 178, 194, 210, 226, 242, 3, 19, 35, 51, 67, 83, 99, 115, 131, 147, 163, 179, 195, 211, 227, 243, 4, 20, 36, 52, 68, 84, 100, 116, 132, 148, 164, 180, 196, 212, 228, 244, 5, 21, 37, 53, 69, 85, 101, 117, 133, 149, 165, 181, 197, 213, 229, 245, 6, 22, 38, 54, 70, 86, 102, 118, 134, 150, 166, 182, 198, 214, 230, 246, 7, 23, 39, 55, 71, 87, 103, 119, 135, 151, 167, 183, 199, 215, 231, 247, 8, 24, 40, 56, 72, 88, 104, 120, 136, 152, 168, 184, 200, 216, 232, 248, 9, 25, 41, 57, 73, 89, 105, 121, 137, 153, 169, 185, 201, 217, 233, 249, 10, 26, 42, 58, 74, 90, 106, 122, 138, 154, 170, 186, 202, 218, 234, 250, 11, 27, 43, 59, 75, 91, 107, 123, 139, 155, 171, 187, 203, 219, 235, 251, 12, 28, 44, 60, 76, 92, 108, 124, 140, 156, 172, 188, 204, 220, 236, 252, 13, 29, 45, 61, 77, 93, 109, 125, 141, 157, 173, 189, 205, 221, 237, 253, 14, 30, 46, 62, 78, 94, 110, 126, 142, 158, 174, 190, 206, 222, 238, 254, 15, 31, 47, 63, 79, 95, 111, 127, 143, 159, 175, 191, 207, 223, 239, 255),
        intArrayOf(0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 1, 17, 33, 49, 65, 81, 97, 113, 129, 145, 161, 177, 193, 209, 225, 241, 2, 18, 34, 50, 66, 82, 98, 114, 130, 146, 162, 178, 194, 210, 226, 242, 3, 19, 35, 51, 67, 83, 99, 115, 131, 147, 163, 179, 195, 211, 227, 243, 4, 20, 36, 52, 68, 84, 100, 116, 132, 148, 164, 180, 196, 212, 228, 244, 5, 21, 37, 53, 69, 85, 101, 117, 133, 149, 165, 181, 197, 213, 229, 245, 6, 22, 38, 54, 70, 86, 102, 118, 134, 150, 166, 182, 198, 214, 230, 246, 7, 23, 39, 55, 71, 87, 103, 119, 135, 151, 167, 183, 199, 215, 231, 247, 8, 24, 40, 56, 72, 88, 104, 120, 136, 152, 168, 184, 200, 216, 232, 248, 9, 25, 41, 57, 73, 89, 105, 121, 137, 153, 169, 185, 201, 217, 233, 249, 10, 26, 42, 58, 74, 90, 106, 122, 138, 154, 170, 186, 202, 218, 234, 250, 11, 27, 43, 59, 75, 91, 107, 123, 139, 155, 171, 187, 203, 219, 235, 251, 12, 28, 44, 60, 76, 92, 108, 124, 140, 156, 172, 188, 204, 220, 236, 252, 13, 29, 45, 61, 77, 93, 109, 125, 141, 157, 173, 189, 205, 221, 237, 253, 14, 30, 46, 62, 78, 94, 110, 126, 142, 158, 174, 190, 206, 222, 238, 254, 15, 31, 47, 63, 79, 95, 111, 127, 143, 159, 175, 191, 207, 223, 239, 255),
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 1, 33, 65, 97, 129, 161, 193, 225, 2, 34, 66, 98, 130, 162, 194, 226, 3, 35, 67, 99, 131, 163, 195, 227, 4, 36, 68, 100, 132, 164, 196, 228, 5, 37, 69, 101, 133, 165, 197, 229, 6, 38, 70, 102, 134, 166, 198, 230, 7, 39, 71, 103, 135, 167, 199, 231, 8, 40, 72, 104, 136, 168, 200, 232, 9, 41, 73, 105, 137, 169, 201, 233, 10, 42, 74, 106, 138, 170, 202, 234, 11, 43, 75, 107, 139, 171, 203, 235, 12, 44, 76, 108, 140, 172, 204, 236, 13, 45, 77, 109, 141, 173, 205, 237, 14, 46, 78, 110, 142, 174, 206, 238, 15, 47, 79, 111, 143, 175, 207, 239, 16, 48, 80, 112, 144, 176, 208, 240, 17, 49, 81, 113, 145, 177, 209, 241, 18, 50, 82, 114, 146, 178, 210, 242, 19, 51, 83, 115, 147, 179, 211, 243, 20, 52, 84, 116, 148, 180, 212, 244, 21, 53, 85, 117, 149, 181, 213, 245, 22, 54, 86, 118, 150, 182, 214, 246, 23, 55, 87, 119, 151, 183, 215, 247, 24, 56, 88, 120, 152, 184, 216, 248, 25, 57, 89, 121, 153, 185, 217, 249, 26, 58, 90, 122, 154, 186, 218, 250, 27, 59, 91, 123, 155, 187, 219, 251, 28, 60, 92, 124, 156, 188, 220, 252, 29, 61, 93, 125, 157, 189, 221, 253, 30, 62, 94, 126, 158, 190, 222, 254, 31, 63, 95, 127, 159, 191, 223, 255),
        intArrayOf(52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51),
        intArrayOf(0, 128, 1, 129, 2, 130, 3, 131, 4, 132, 5, 133, 6, 134, 7, 135, 8, 136, 9, 137, 10, 138, 11, 139, 12, 140, 13, 141, 14, 142, 15, 143, 16, 144, 17, 145, 18, 146, 19, 147, 20, 148, 21, 149, 22, 150, 23, 151, 24, 152, 25, 153, 26, 154, 27, 155, 28, 156, 29, 157, 30, 158, 31, 159, 32, 160, 33, 161, 34, 162, 35, 163, 36, 164, 37, 165, 38, 166, 39, 167, 40, 168, 41, 169, 42, 170, 43, 171, 44, 172, 45, 173, 46, 174, 47, 175, 48, 176, 49, 177, 50, 178, 51, 179, 52, 180, 53, 181, 54, 182, 55, 183, 56, 184, 57, 185, 58, 186, 59, 187, 60, 188, 61, 189, 62, 190, 63, 191, 64, 192, 65, 193, 66, 194, 67, 195, 68, 196, 69, 197, 70, 198, 71, 199, 72, 200, 73, 201, 74, 202, 75, 203, 76, 204, 77, 205, 78, 206, 79, 207, 80, 208, 81, 209, 82, 210, 83, 211, 84, 212, 85, 213, 86, 214, 87, 215, 88, 216, 89, 217, 90, 218, 91, 219, 92, 220, 93, 221, 94, 222, 95, 223, 96, 224, 97, 225, 98, 226, 99, 227, 100, 228, 101, 229, 102, 230, 103, 231, 104, 232, 105, 233, 106, 234, 107, 235, 108, 236, 109, 237, 110, 238, 111, 239, 112, 240, 113, 241, 114, 242, 115, 243, 116, 244, 117, 245, 118, 246, 119, 247, 120, 248, 121, 249, 122, 250, 123, 251, 124, 252, 125, 253, 126, 254, 127, 255),
        intArrayOf(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 68, 72, 76, 80, 84, 88, 92, 96, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 148, 152, 156, 160, 164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 208, 212, 216, 220, 224, 228, 232, 236, 240, 244, 248, 252, 1, 5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 65, 69, 73, 77, 81, 85, 89, 93, 97, 101, 105, 109, 113, 117, 121, 125, 129, 133, 137, 141, 145, 149, 153, 157, 161, 165, 169, 173, 177, 181, 185, 189, 193, 197, 201, 205, 209, 213, 217, 221, 225, 229, 233, 237, 241, 245, 249, 253, 2, 6, 10, 14, 18, 22, 26, 30, 34, 38, 42, 46, 50, 54, 58, 62, 66, 70, 74, 78, 82, 86, 90, 94, 98, 102, 106, 110, 114, 118, 122, 126, 130, 134, 138, 142, 146, 150, 154, 158, 162, 166, 170, 174, 178, 182, 186, 190, 194, 198, 202, 206, 210, 214, 218, 222, 226, 230, 234, 238, 242, 246, 250, 254, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83, 87, 91, 95, 99, 103, 107, 111, 115, 119, 123, 127, 131, 135, 139, 143, 147, 151, 155, 159, 163, 167, 171, 175, 179, 183, 187, 191, 195, 199, 203, 207, 211, 215, 219, 223, 227, 231, 235, 239, 243, 247, 251, 255),
        intArrayOf(230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229),
        intArrayOf(81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80),
        intArrayOf(255, 254, 253, 252, 251, 250, 249, 248, 247, 246, 245, 244, 243, 242, 241, 240, 239, 238, 237, 236, 235, 234, 233, 232, 231, 230, 229, 228, 227, 226, 225, 224, 223, 222, 221, 220, 219, 218, 217, 216, 215, 214, 213, 212, 211, 210, 209, 208, 207, 206, 205, 204, 203, 202, 201, 200, 199, 198, 197, 196, 195, 194, 193, 192, 191, 190, 189, 188, 187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174, 173, 172, 171, 170, 169, 168, 167, 166, 165, 164, 163, 162, 161, 160, 159, 158, 157, 156, 155, 154, 153, 152, 151, 150, 149, 148, 147, 146, 145, 144, 143, 142, 141, 140, 139, 138, 137, 136, 135, 134, 133, 132, 131, 130, 129, 128, 127, 126, 125, 124, 123, 122, 121, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81, 80, 79, 78, 77, 76, 75, 74, 73, 72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        intArrayOf(226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225),
        intArrayOf(0, 128, 1, 129, 2, 130, 3, 131, 4, 132, 5, 133, 6, 134, 7, 135, 8, 136, 9, 137, 10, 138, 11, 139, 12, 140, 13, 141, 14, 142, 15, 143, 16, 144, 17, 145, 18, 146, 19, 147, 20, 148, 21, 149, 22, 150, 23, 151, 24, 152, 25, 153, 26, 154, 27, 155, 28, 156, 29, 157, 30, 158, 31, 159, 32, 160, 33, 161, 34, 162, 35, 163, 36, 164, 37, 165, 38, 166, 39, 167, 40, 168, 41, 169, 42, 170, 43, 171, 44, 172, 45, 173, 46, 174, 47, 175, 48, 176, 49, 177, 50, 178, 51, 179, 52, 180, 53, 181, 54, 182, 55, 183, 56, 184, 57, 185, 58, 186, 59, 187, 60, 188, 61, 189, 62, 190, 63, 191, 64, 192, 65, 193, 66, 194, 67, 195, 68, 196, 69, 197, 70, 198, 71, 199, 72, 200, 73, 201, 74, 202, 75, 203, 76, 204, 77, 205, 78, 206, 79, 207, 80, 208, 81, 209, 82, 210, 83, 211, 84, 212, 85, 213, 86, 214, 87, 215, 88, 216, 89, 217, 90, 218, 91, 219, 92, 220, 93, 221, 94, 222, 95, 223, 96, 224, 97, 225, 98, 226, 99, 227, 100, 228, 101, 229, 102, 230, 103, 231, 104, 232, 105, 233, 106, 234, 107, 235, 108, 236, 109, 237, 110, 238, 111, 239, 112, 240, 113, 241, 114, 242, 115, 243, 116, 244, 117, 245, 118, 246, 119, 247, 120, 248, 121, 249, 122, 250, 123, 251, 124, 252, 125, 253, 126, 254, 127, 255),
        intArrayOf(166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165),
        intArrayOf(0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 1, 17, 33, 49, 65, 81, 97, 113, 129, 145, 161, 177, 193, 209, 225, 241, 2, 18, 34, 50, 66, 82, 98, 114, 130, 146, 162, 178, 194, 210, 226, 242, 3, 19, 35, 51, 67, 83, 99, 115, 131, 147, 163, 179, 195, 211, 227, 243, 4, 20, 36, 52, 68, 84, 100, 116, 132, 148, 164, 180, 196, 212, 228, 244, 5, 21, 37, 53, 69, 85, 101, 117, 133, 149, 165, 181, 197, 213, 229, 245, 6, 22, 38, 54, 70, 86, 102, 118, 134, 150, 166, 182, 198, 214, 230, 246, 7, 23, 39, 55, 71, 87, 103, 119, 135, 151, 167, 183, 199, 215, 231, 247, 8, 24, 40, 56, 72, 88, 104, 120, 136, 152, 168, 184, 200, 216, 232, 248, 9, 25, 41, 57, 73, 89, 105, 121, 137, 153, 169, 185, 201, 217, 233, 249, 10, 26, 42, 58, 74, 90, 106, 122, 138, 154, 170, 186, 202, 218, 234, 250, 11, 27, 43, 59, 75, 91, 107, 123, 139, 155, 171, 187, 203, 219, 235, 251, 12, 28, 44, 60, 76, 92, 108, 124, 140, 156, 172, 188, 204, 220, 236, 252, 13, 29, 45, 61, 77, 93, 109, 125, 141, 157, 173, 189, 205, 221, 237, 253, 14, 30, 46, 62, 78, 94, 110, 126, 142, 158, 174, 190, 206, 222, 238, 254, 15, 31, 47, 63, 79, 95, 111, 127, 143, 159, 175, 191, 207, 223, 239, 255),
        intArrayOf(79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78),
        intArrayOf(71, 70, 69, 68, 67, 66, 65, 64, 79, 78, 77, 76, 75, 74, 73, 72, 87, 86, 85, 84, 83, 82, 81, 80, 95, 94, 93, 92, 91, 90, 89, 88, 103, 102, 101, 100, 99, 98, 97, 96, 111, 110, 109, 108, 107, 106, 105, 104, 119, 118, 117, 116, 115, 114, 113, 112, 127, 126, 125, 124, 123, 122, 121, 120, 7, 6, 5, 4, 3, 2, 1, 0, 15, 14, 13, 12, 11, 10, 9, 8, 23, 22, 21, 20, 19, 18, 17, 16, 31, 30, 29, 28, 27, 26, 25, 24, 39, 38, 37, 36, 35, 34, 33, 32, 47, 46, 45, 44, 43, 42, 41, 40, 55, 54, 53, 52, 51, 50, 49, 48, 63, 62, 61, 60, 59, 58, 57, 56, 199, 198, 197, 196, 195, 194, 193, 192, 207, 206, 205, 204, 203, 202, 201, 200, 215, 214, 213, 212, 211, 210, 209, 208, 223, 222, 221, 220, 219, 218, 217, 216, 231, 230, 229, 228, 227, 226, 225, 224, 239, 238, 237, 236, 235, 234, 233, 232, 247, 246, 245, 244, 243, 242, 241, 240, 255, 254, 253, 252, 251, 250, 249, 248, 135, 134, 133, 132, 131, 130, 129, 128, 143, 142, 141, 140, 139, 138, 137, 136, 151, 150, 149, 148, 147, 146, 145, 144, 159, 158, 157, 156, 155, 154, 153, 152, 167, 166, 165, 164, 163, 162, 161, 160, 175, 174, 173, 172, 171, 170, 169, 168, 183, 182, 181, 180, 179, 178, 177, 176, 191, 190, 189, 188, 187, 186, 185, 184),
        intArrayOf(255, 254, 253, 252, 251, 250, 249, 248, 247, 246, 245, 244, 243, 242, 241, 240, 239, 238, 237, 236, 235, 234, 233, 232, 231, 230, 229, 228, 227, 226, 225, 224, 223, 222, 221, 220, 219, 218, 217, 216, 215, 214, 213, 212, 211, 210, 209, 208, 207, 206, 205, 204, 203, 202, 201, 200, 199, 198, 197, 196, 195, 194, 193, 192, 191, 190, 189, 188, 187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174, 173, 172, 171, 170, 169, 168, 167, 166, 165, 164, 163, 162, 161, 160, 159, 158, 157, 156, 155, 154, 153, 152, 151, 150, 149, 148, 147, 146, 145, 144, 143, 142, 141, 140, 139, 138, 137, 136, 135, 134, 133, 132, 131, 130, 129, 128, 127, 126, 125, 124, 123, 122, 121, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81, 80, 79, 78, 77, 76, 75, 74, 73, 72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        intArrayOf(28, 29, 30, 31, 24, 25, 26, 27, 20, 21, 22, 23, 16, 17, 18, 19, 12, 13, 14, 15, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3, 60, 61, 62, 63, 56, 57, 58, 59, 52, 53, 54, 55, 48, 49, 50, 51, 44, 45, 46, 47, 40, 41, 42, 43, 36, 37, 38, 39, 32, 33, 34, 35, 92, 93, 94, 95, 88, 89, 90, 91, 84, 85, 86, 87, 80, 81, 82, 83, 76, 77, 78, 79, 72, 73, 74, 75, 68, 69, 70, 71, 64, 65, 66, 67, 124, 125, 126, 127, 120, 121, 122, 123, 116, 117, 118, 119, 112, 113, 114, 115, 108, 109, 110, 111, 104, 105, 106, 107, 100, 101, 102, 103, 96, 97, 98, 99, 156, 157, 158, 159, 152, 153, 154, 155, 148, 149, 150, 151, 144, 145, 146, 147, 140, 141, 142, 143, 136, 137, 138, 139, 132, 133, 134, 135, 128, 129, 130, 131, 188, 189, 190, 191, 184, 185, 186, 187, 180, 181, 182, 183, 176, 177, 178, 179, 172, 173, 174, 175, 168, 169, 170, 171, 164, 165, 166, 167, 160, 161, 162, 163, 220, 221, 222, 223, 216, 217, 218, 219, 212, 213, 214, 215, 208, 209, 210, 211, 204, 205, 206, 207, 200, 201, 202, 203, 196, 197, 198, 199, 192, 193, 194, 195, 252, 253, 254, 255, 248, 249, 250, 251, 244, 245, 246, 247, 240, 241, 242, 243, 236, 237, 238, 239, 232, 233, 234, 235, 228, 229, 230, 231, 224, 225, 226, 227),
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96, 104, 112, 120, 128, 136, 144, 152, 160, 168, 176, 184, 192, 200, 208, 216, 224, 232, 240, 248, 1, 9, 17, 25, 33, 41, 49, 57, 65, 73, 81, 89, 97, 105, 113, 121, 129, 137, 145, 153, 161, 169, 177, 185, 193, 201, 209, 217, 225, 233, 241, 249, 2, 10, 18, 26, 34, 42, 50, 58, 66, 74, 82, 90, 98, 106, 114, 122, 130, 138, 146, 154, 162, 170, 178, 186, 194, 202, 210, 218, 226, 234, 242, 250, 3, 11, 19, 27, 35, 43, 51, 59, 67, 75, 83, 91, 99, 107, 115, 123, 131, 139, 147, 155, 163, 171, 179, 187, 195, 203, 211, 219, 227, 235, 243, 251, 4, 12, 20, 28, 36, 44, 52, 60, 68, 76, 84, 92, 100, 108, 116, 124, 132, 140, 148, 156, 164, 172, 180, 188, 196, 204, 212, 220, 228, 236, 244, 252, 5, 13, 21, 29, 37, 45, 53, 61, 69, 77, 85, 93, 101, 109, 117, 125, 133, 141, 149, 157, 165, 173, 181, 189, 197, 205, 213, 221, 229, 237, 245, 253, 6, 14, 22, 30, 38, 46, 54, 62, 70, 78, 86, 94, 102, 110, 118, 126, 134, 142, 150, 158, 166, 174, 182, 190, 198, 206, 214, 222, 230, 238, 246, 254, 7, 15, 23, 31, 39, 47, 55, 63, 71, 79, 87, 95, 103, 111, 119, 127, 135, 143, 151, 159, 167, 175, 183, 191, 199, 207, 215, 223, 231, 239, 247, 255),
        intArrayOf(255, 254, 253, 252, 251, 250, 249, 248, 247, 246, 245, 244, 243, 242, 241, 240, 239, 238, 237, 236, 235, 234, 233, 232, 231, 230, 229, 228, 227, 226, 225, 224, 223, 222, 221, 220, 219, 218, 217, 216, 215, 214, 213, 212, 211, 210, 209, 208, 207, 206, 205, 204, 203, 202, 201, 200, 199, 198, 197, 196, 195, 194, 193, 192, 191, 190, 189, 188, 187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174, 173, 172, 171, 170, 169, 168, 167, 166, 165, 164, 163, 162, 161, 160, 159, 158, 157, 156, 155, 154, 153, 152, 151, 150, 149, 148, 147, 146, 145, 144, 143, 142, 141, 140, 139, 138, 137, 136, 135, 134, 133, 132, 131, 130, 129, 128, 127, 126, 125, 124, 123, 122, 121, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81, 80, 79, 78, 77, 76, 75, 74, 73, 72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        intArrayOf(197, 196, 199, 198, 193, 192, 195, 194, 205, 204, 207, 206, 201, 200, 203, 202, 213, 212, 215, 214, 209, 208, 211, 210, 221, 220, 223, 222, 217, 216, 219, 218, 229, 228, 231, 230, 225, 224, 227, 226, 237, 236, 239, 238, 233, 232, 235, 234, 245, 244, 247, 246, 241, 240, 243, 242, 253, 252, 255, 254, 249, 248, 251, 250, 133, 132, 135, 134, 129, 128, 131, 130, 141, 140, 143, 142, 137, 136, 139, 138, 149, 148, 151, 150, 145, 144, 147, 146, 157, 156, 159, 158, 153, 152, 155, 154, 165, 164, 167, 166, 161, 160, 163, 162, 173, 172, 175, 174, 169, 168, 171, 170, 181, 180, 183, 182, 177, 176, 179, 178, 189, 188, 191, 190, 185, 184, 187, 186, 69, 68, 71, 70, 65, 64, 67, 66, 77, 76, 79, 78, 73, 72, 75, 74, 85, 84, 87, 86, 81, 80, 83, 82, 93, 92, 95, 94, 89, 88, 91, 90, 101, 100, 103, 102, 97, 96, 99, 98, 109, 108, 111, 110, 105, 104, 107, 106, 117, 116, 119, 118, 113, 112, 115, 114, 125, 124, 127, 126, 121, 120, 123, 122, 5, 4, 7, 6, 1, 0, 3, 2, 13, 12, 15, 14, 9, 8, 11, 10, 21, 20, 23, 22, 17, 16, 19, 18, 29, 28, 31, 30, 25, 24, 27, 26, 37, 36, 39, 38, 33, 32, 35, 34, 45, 44, 47, 46, 41, 40, 43, 42, 53, 52, 55, 54, 49, 48, 51, 50, 61, 60, 63, 62, 57, 56, 59, 58),
        intArrayOf(255, 254, 253, 252, 251, 250, 249, 248, 247, 246, 245, 244, 243, 242, 241, 240, 239, 238, 237, 236, 235, 234, 233, 232, 231, 230, 229, 228, 227, 226, 225, 224, 223, 222, 221, 220, 219, 218, 217, 216, 215, 214, 213, 212, 211, 210, 209, 208, 207, 206, 205, 204, 203, 202, 201, 200, 199, 198, 197, 196, 195, 194, 193, 192, 191, 190, 189, 188, 187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174, 173, 172, 171, 170, 169, 168, 167, 166, 165, 164, 163, 162, 161, 160, 159, 158, 157, 156, 155, 154, 153, 152, 151, 150, 149, 148, 147, 146, 145, 144, 143, 142, 141, 140, 139, 138, 137, 136, 135, 134, 133, 132, 131, 130, 129, 128, 127, 126, 125, 124, 123, 122, 121, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81, 80, 79, 78, 77, 76, 75, 74, 73, 72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        intArrayOf(0, 128, 1, 129, 2, 130, 3, 131, 4, 132, 5, 133, 6, 134, 7, 135, 8, 136, 9, 137, 10, 138, 11, 139, 12, 140, 13, 141, 14, 142, 15, 143, 16, 144, 17, 145, 18, 146, 19, 147, 20, 148, 21, 149, 22, 150, 23, 151, 24, 152, 25, 153, 26, 154, 27, 155, 28, 156, 29, 157, 30, 158, 31, 159, 32, 160, 33, 161, 34, 162, 35, 163, 36, 164, 37, 165, 38, 166, 39, 167, 40, 168, 41, 169, 42, 170, 43, 171, 44, 172, 45, 173, 46, 174, 47, 175, 48, 176, 49, 177, 50, 178, 51, 179, 52, 180, 53, 181, 54, 182, 55, 183, 56, 184, 57, 185, 58, 186, 59, 187, 60, 188, 61, 189, 62, 190, 63, 191, 64, 192, 65, 193, 66, 194, 67, 195, 68, 196, 69, 197, 70, 198, 71, 199, 72, 200, 73, 201, 74, 202, 75, 203, 76, 204, 77, 205, 78, 206, 79, 207, 80, 208, 81, 209, 82, 210, 83, 211, 84, 212, 85, 213, 86, 214, 87, 215, 88, 216, 89, 217, 90, 218, 91, 219, 92, 220, 93, 221, 94, 222, 95, 223, 96, 224, 97, 225, 98, 226, 99, 227, 100, 228, 101, 229, 102, 230, 103, 231, 104, 232, 105, 233, 106, 234, 107, 235, 108, 236, 109, 237, 110, 238, 111, 239, 112, 240, 113, 241, 114, 242, 115, 243, 116, 244, 117, 245, 118, 246, 119, 247, 120, 248, 121, 249, 122, 250, 123, 251, 124, 252, 125, 253, 126, 254, 127, 255),
        intArrayOf(0, 64, 128, 192, 1, 65, 129, 193, 2, 66, 130, 194, 3, 67, 131, 195, 4, 68, 132, 196, 5, 69, 133, 197, 6, 70, 134, 198, 7, 71, 135, 199, 8, 72, 136, 200, 9, 73, 137, 201, 10, 74, 138, 202, 11, 75, 139, 203, 12, 76, 140, 204, 13, 77, 141, 205, 14, 78, 142, 206, 15, 79, 143, 207, 16, 80, 144, 208, 17, 81, 145, 209, 18, 82, 146, 210, 19, 83, 147, 211, 20, 84, 148, 212, 21, 85, 149, 213, 22, 86, 150, 214, 23, 87, 151, 215, 24, 88, 152, 216, 25, 89, 153, 217, 26, 90, 154, 218, 27, 91, 155, 219, 28, 92, 156, 220, 29, 93, 157, 221, 30, 94, 158, 222, 31, 95, 159, 223, 32, 96, 160, 224, 33, 97, 161, 225, 34, 98, 162, 226, 35, 99, 163, 227, 36, 100, 164, 228, 37, 101, 165, 229, 38, 102, 166, 230, 39, 103, 167, 231, 40, 104, 168, 232, 41, 105, 169, 233, 42, 106, 170, 234, 43, 107, 171, 235, 44, 108, 172, 236, 45, 109, 173, 237, 46, 110, 174, 238, 47, 111, 175, 239, 48, 112, 176, 240, 49, 113, 177, 241, 50, 114, 178, 242, 51, 115, 179, 243, 52, 116, 180, 244, 53, 117, 181, 245, 54, 118, 182, 246, 55, 119, 183, 247, 56, 120, 184, 248, 57, 121, 185, 249, 58, 122, 186, 250, 59, 123, 187, 251, 60, 124, 188, 252, 61, 125, 189, 253, 62, 126, 190, 254, 63, 127, 191, 255),
        intArrayOf(255, 254, 253, 252, 251, 250, 249, 248, 247, 246, 245, 244, 243, 242, 241, 240, 239, 238, 237, 236, 235, 234, 233, 232, 231, 230, 229, 228, 227, 226, 225, 224, 223, 222, 221, 220, 219, 218, 217, 216, 215, 214, 213, 212, 211, 210, 209, 208, 207, 206, 205, 204, 203, 202, 201, 200, 199, 198, 197, 196, 195, 194, 193, 192, 191, 190, 189, 188, 187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174, 173, 172, 171, 170, 169, 168, 167, 166, 165, 164, 163, 162, 161, 160, 159, 158, 157, 156, 155, 154, 153, 152, 151, 150, 149, 148, 147, 146, 145, 144, 143, 142, 141, 140, 139, 138, 137, 136, 135, 134, 133, 132, 131, 130, 129, 128, 127, 126, 125, 124, 123, 122, 121, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81, 80, 79, 78, 77, 76, 75, 74, 73, 72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        intArrayOf(0, 64, 128, 192, 1, 65, 129, 193, 2, 66, 130, 194, 3, 67, 131, 195, 4, 68, 132, 196, 5, 69, 133, 197, 6, 70, 134, 198, 7, 71, 135, 199, 8, 72, 136, 200, 9, 73, 137, 201, 10, 74, 138, 202, 11, 75, 139, 203, 12, 76, 140, 204, 13, 77, 141, 205, 14, 78, 142, 206, 15, 79, 143, 207, 16, 80, 144, 208, 17, 81, 145, 209, 18, 82, 146, 210, 19, 83, 147, 211, 20, 84, 148, 212, 21, 85, 149, 213, 22, 86, 150, 214, 23, 87, 151, 215, 24, 88, 152, 216, 25, 89, 153, 217, 26, 90, 154, 218, 27, 91, 155, 219, 28, 92, 156, 220, 29, 93, 157, 221, 30, 94, 158, 222, 31, 95, 159, 223, 32, 96, 160, 224, 33, 97, 161, 225, 34, 98, 162, 226, 35, 99, 163, 227, 36, 100, 164, 228, 37, 101, 165, 229, 38, 102, 166, 230, 39, 103, 167, 231, 40, 104, 168, 232, 41, 105, 169, 233, 42, 106, 170, 234, 43, 107, 171, 235, 44, 108, 172, 236, 45, 109, 173, 237, 46, 110, 174, 238, 47, 111, 175, 239, 48, 112, 176, 240, 49, 113, 177, 241, 50, 114, 178, 242, 51, 115, 179, 243, 52, 116, 180, 244, 53, 117, 181, 245, 54, 118, 182, 246, 55, 119, 183, 247, 56, 120, 184, 248, 57, 121, 185, 249, 58, 122, 186, 250, 59, 123, 187, 251, 60, 124, 188, 252, 61, 125, 189, 253, 62, 126, 190, 254, 63, 127, 191, 255),
        intArrayOf(255, 254, 253, 252, 251, 250, 249, 248, 247, 246, 245, 244, 243, 242, 241, 240, 239, 238, 237, 236, 235, 234, 233, 232, 231, 230, 229, 228, 227, 226, 225, 224, 223, 222, 221, 220, 219, 218, 217, 216, 215, 214, 213, 212, 211, 210, 209, 208, 207, 206, 205, 204, 203, 202, 201, 200, 199, 198, 197, 196, 195, 194, 193, 192, 191, 190, 189, 188, 187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174, 173, 172, 171, 170, 169, 168, 167, 166, 165, 164, 163, 162, 161, 160, 159, 158, 157, 156, 155, 154, 153, 152, 151, 150, 149, 148, 147, 146, 145, 144, 143, 142, 141, 140, 139, 138, 137, 136, 135, 134, 133, 132, 131, 130, 129, 128, 127, 126, 125, 124, 123, 122, 121, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81, 80, 79, 78, 77, 76, 75, 74, 73, 72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        intArrayOf(26, 27, 24, 25, 30, 31, 28, 29, 18, 19, 16, 17, 22, 23, 20, 21, 10, 11, 8, 9, 14, 15, 12, 13, 2, 3, 0, 1, 6, 7, 4, 5, 58, 59, 56, 57, 62, 63, 60, 61, 50, 51, 48, 49, 54, 55, 52, 53, 42, 43, 40, 41, 46, 47, 44, 45, 34, 35, 32, 33, 38, 39, 36, 37, 90, 91, 88, 89, 94, 95, 92, 93, 82, 83, 80, 81, 86, 87, 84, 85, 74, 75, 72, 73, 78, 79, 76, 77, 66, 67, 64, 65, 70, 71, 68, 69, 122, 123, 120, 121, 126, 127, 124, 125, 114, 115, 112, 113, 118, 119, 116, 117, 106, 107, 104, 105, 110, 111, 108, 109, 98, 99, 96, 97, 102, 103, 100, 101, 154, 155, 152, 153, 158, 159, 156, 157, 146, 147, 144, 145, 150, 151, 148, 149, 138, 139, 136, 137, 142, 143, 140, 141, 130, 131, 128, 129, 134, 135, 132, 133, 186, 187, 184, 185, 190, 191, 188, 189, 178, 179, 176, 177, 182, 183, 180, 181, 170, 171, 168, 169, 174, 175, 172, 173, 162, 163, 160, 161, 166, 167, 164, 165, 218, 219, 216, 217, 222, 223, 220, 221, 210, 211, 208, 209, 214, 215, 212, 213, 202, 203, 200, 201, 206, 207, 204, 205, 194, 195, 192, 193, 198, 199, 196, 197, 250, 251, 248, 249, 254, 255, 252, 253, 242, 243, 240, 241, 246, 247, 244, 245, 234, 235, 232, 233, 238, 239, 236, 237, 226, 227, 224, 225, 230, 231, 228, 229),
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun generateToken(input: String): String {
        val encoded = URLEncoder.encode(input, "UTF-8")
        val output = mutableListOf<Int>()

        for (i in encoded.indices) {
            val c = encoded[i].code
            if (c in 0..255) {
                val mapped = tcodex[i % tcodex.size][c]
                output.add(mapped and 0xFF) // keep byte-safe
            } else {
                output.add(0) // For invalid characters, you can decide how to handle them
            }
        }

        val byteArray = output.map { it.toByte() }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteArray)
    }
    */


    @RequiresApi(Build.VERSION_CODES.O)
    fun generateToken(n: String, homeKeysSrc: List<String>): String {
        val homeKeys = mutableListOf<ByteArray>()
        for (i in homeKeysSrc.indices) {
            homeKeys.add(base64DecodeArray(homeKeysSrc[i]))
        }
        val encoded = URLEncoder.encode(n, "UTF-8")
        val o = mutableListOf<Byte>()
        for (i in encoded.indices) {
            val k = homeKeys[keysChar.indexOf(encoded[i])]
            o.add(k[ i % k.size ])
        }
        return Base64.getEncoder().encodeToString(o.toByteArray())
            .replace("/", "_")
            .replace("+", "-")
            .replace("=", "")
    }

    /*
        fun decodeIframeData(input: String, homeKeys: List<String>): String {
            // Replace characters and decode Base64
            val base64 = input.replace('_', '/').replace('-', '+')
            val decodedBytes = base64DecodeArray(base64)
            val decodedString = decodedBytes.toString(Charsets.ISO_8859_1) // match JS charCodeAt

            val result = StringBuilder()

            for (i in decodedString.indices) {
                val c = decodedString[i].code
                var cp: Char? = null

                for (j in homeKeys.indices) {
                    val ck = homeKeys[j][i % homeKeys[j].length].code
                    if (ck == c) {
                        cp = keysChar[j]
                        break
                    }
                }

                result.append(cp ?: '%')
            }

            return URLDecoder.decode(result.toString(), "UTF-8")
        }
        */

    @RequiresApi(Build.VERSION_CODES.O)
    fun decodeIframeData(n: String, homeKeysSrc: List<String>): String {
        val homeKeys = mutableListOf<ByteArray>()
        for (i in homeKeysSrc.indices) {
            val decodedKey = Base64.getDecoder().decode(homeKeysSrc[i])
            homeKeys.add(decodedKey)
        }

        val decoded = Base64.getDecoder().decode(n.replace('_', '/').replace('-', '+'))
        val o = StringBuilder()
        for (i in decoded.indices) {
            val c = decoded[i]
            var cp = '%'
            for (j in homeKeys.indices) {
                val k = homeKeys[j]
                val ck = k[i % k.size]
                if (c == ck) {
                    cp = keysChar[j]
                    break
                }
            }
            o.append(cp)
        }

        val url = URLDecoder.decode(o.toString())
        return url.replaceFirst("'", ".")
    }

    //Megaup

    @RequiresApi(Build.VERSION_CODES.O)
    fun decode(n: String, megaKeysSrc:List<String>,): String {
        val megaKeys = mutableListOf<ByteArray>()
        for (i in megaKeysSrc.indices) {
            megaKeys.add(Base64.getDecoder().decode(megaKeysSrc[i]))
        }

        // Base64-safe decode
        val decoded = Base64.getDecoder().decode(n.replace('_', '/').replace('-', '+'))
        val o = mutableListOf<Byte>()

        // Iterate over each character in the decoded string
        for (i in decoded.indices) {
            // get unsigned-byte char code
            val c = ((decoded[i].toInt() and 0xFF).toUByte()).toInt()
            // get megakey substitute key
            val k = megaKeys[c]
            // get replacement character
            o.add(k[ i % k.size ])
        }
        // Return the decoded string
        return URLDecoder.decode(String(o.toByteArray(), Charsets.ISO_8859_1))
    }


    private fun base64UrlEncode(str: String): String {
        return base64Encode(str.toByteArray(Charsets.ISO_8859_1))
            .replace("+", "-")
            .replace("/", "_")
            .replace(Regex("=+$"), "")
    }

    private fun base64UrlDecode(n: String): String {
        val padded = n.padEnd(n.length + ((4 - (n.length % 4)) % 4), '=')
            .replace('-', '+')
            .replace('_', '/')
        return base64Decode(padded)
    }


    private fun transform(n: String, t: String): String {
        val v = IntArray(256) { it }
        var c = 0
        val f = StringBuilder()
        for (w in 0 until 256) {
            c = (c + v[w] + n[w % n.length].code) % 256
            v[w] = v[c].also { v[c] = v[w] }
        }
        var a = 0
        var w = 0
        c = 0
        while (a < t.length) {
            w = (w + 1) % 256
            c = (c + v[w]) % 256
            v[w] = v[c].also { v[c] = v[w] }
            f.append((t[a].code xor v[(v[w] + v[c]) % 256]).toChar())
            a++
        }
        return f.toString()
    }

    private fun reverseIt(input: String) = input.reversed()

    private fun substitute(input: String, keys: String, values: String): String {
        val map = mutableMapOf<Char, Char>()
        for (i in keys.indices) {
            map[keys[i]] = values.getOrNull(i) ?: keys[i]
        }
        val result = StringBuilder()
        for (char in input) {
            result.append(map[char] ?: char)
        }
        return result.toString()
    }

    private fun encodeURIComponent(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun decodeURIComponent(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private fun decodeUri(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}

val decryptMethods: Map<String, (String) -> String> = mapOf(
    "TsA2KGDGux" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 7).toChar() }.joinToString("")
        }
    },
    "ux8qjPHC66" to { inputString ->
        val reversed = inputString.reversed()
        val hexPairs = reversed.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        val key = "X9a(O;FMV2-7VO5x;Ao\u0005:dN1NoFs?j,"
        hexPairs.mapIndexed { i, ch -> (ch.code xor key[i % key.length].code).toChar() }
            .joinToString("")
    },
    "xTyBxQyGTA" to { inputString ->
        val filtered = inputString.reversed().filterIndexed { i, _ -> i % 2 == 0 }
        String(android.util.Base64.decode(filtered, android.util.Base64.DEFAULT))
    },
    "IhWrImMIGL" to { inputString ->
        val reversed = inputString.reversed()
        val rot13 = reversed.map { ch ->
            when {
                ch in 'a'..'m' || ch in 'A'..'M' -> (ch.code + 13).toChar()
                ch in 'n'..'z' || ch in 'N'..'Z' -> (ch.code - 13).toChar()
                else -> ch
            }
        }.joinToString("")
        String(android.util.Base64.decode(rot13.reversed(), android.util.Base64.DEFAULT))
    },
    "o2VSUnjnZl" to { inputString ->
        val substitutionMap =
            ("xyzabcdefghijklmnopqrstuvwXYZABCDEFGHIJKLMNOPQRSTUVW" zip "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ").toMap()
        inputString.map { substitutionMap[it] ?: it }.joinToString("")
    },
    "eSfH1IRMyL" to { inputString ->
        val reversed = inputString.reversed()
        val shifted = reversed.map { (it.code - 1).toChar() }.joinToString("")
        shifted.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
    },
    "Oi3v1dAlaM" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 5).toChar() }.joinToString("")
        }
    },
    "sXnL9MQIry" to { inputString ->
        val xorKey = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val hexDecoded = inputString.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        val decrypted =
            hexDecoded.mapIndexed { i, ch -> (ch.code xor xorKey[i % xorKey.length].code).toChar() }
                .joinToString("")
        val shifted = decrypted.map { (it.code - 3).toChar() }.joinToString("")
        String(android.util.Base64.decode(shifted, android.util.Base64.DEFAULT))
    },
    "JoAHUMCLXV" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 3).toChar() }.joinToString("")
        }
    },
    "KJHidj7det" to { input ->
        val decoded = String(
            android.util.Base64.decode(
                input.drop(10).dropLast(16),
                android.util.Base64.DEFAULT
            )
        )
        val key = """3SAY~#%Y(V%>5d/Yg${'$'}G[Lh1rK4a;7ok"""
        decoded.mapIndexed { i, ch -> (ch.code xor key[i % key.length].code).toChar() }
            .joinToString("")
    },
    "playerjs" to { x ->
        try {
            var a = x.drop(2)
            val b1: (String) -> String = { str ->
                android.util.Base64.encodeToString(
                    str.toByteArray(),
                    android.util.Base64.NO_WRAP
                )
            }
            val b2: (String) -> String =
                { str -> String(android.util.Base64.decode(str, android.util.Base64.DEFAULT)) }
            val patterns = listOf(
                "*,4).(_)()", "33-*.4/9[6", ":]&*1@@1=&", "=(=:19705/", "%?6497.[:4"
            )
            patterns.forEach { k -> a = a.replace("/@#@/" + b1(k), "") }
            b2(a)
        } catch (e: Exception) {
            "Failed to decode: ${e.message}"
        }
    }
)

fun parseAnimeData(jsonString: String): AnimeData {
    val objectMapper = ObjectMapper()
    return objectMapper.readValue(jsonString, AnimeData::class.java)
}

class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .removeHeader("User-Agent") // Remove existing User-Agent headers
            .addHeader("User-Agent", "Go-http-client/2.0") // Add new User-Agent
            .build()
        return chain.proceed(newRequest)
    }
}

fun cleanTitle(title: String): String {
    val parts = title.split(".", "-", "_")

    val qualityTags = listOf(
        "WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV",
        "CAM", "TS", "R5", "DVDScr", "BRRip", "BDRip", "DVD", "PDTV",
        "HD"
    )

    val audioTags = listOf(
        "AAC", "AC3", "DTS", "MP3", "FLAC", "DD5", "EAC3", "Atmos"
    )

    val subTags = listOf(
        "ESub", "ESubs", "Subs", "MultiSub", "NoSub", "EnglishSub", "HindiSub"
    )

    val codecTags = listOf(
        "x264", "x265", "H264", "HEVC", "AVC"
    )

    val startIndex = parts.indexOfFirst { part ->
        qualityTags.any { tag -> part.contains(tag, ignoreCase = true) }
    }

    val endIndex = parts.indexOfLast { part ->
        subTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                audioTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                codecTags.any { tag -> part.contains(tag, ignoreCase = true) }
    }

    return if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
        parts.subList(startIndex, endIndex + 1).joinToString(".")
    } else if (startIndex != -1) {
        parts.subList(startIndex, parts.size).joinToString(".")
    } else {
        parts.takeLast(3).joinToString(".")
    }
}

//Anichi

fun String.fixUrlPath(): String {
    return if (this.contains(".json?")) "https://allanime.day" + this
    else "https://allanime.day" + URI(this).path + ".json?" + URI(this).query
}

fun fixSourceUrls(url: String, source: String?): String? {
    return if (source == "Ak" || url.contains("/player/vitemb")) {
        tryParseJson<AkIframe>(base64Decode(url.substringAfter("=")))?.idUrl
    } else {
        url.replace(" ", "%20")
    }
}


fun decrypthex(inputStr: String): String {
    val hexString = if (inputStr.startsWith("-")) {
        inputStr.substringAfterLast("-")
    } else {
        inputStr
    }

    val bytes = ByteArray(hexString.length / 2) { i ->
        val hexByte = hexString.substring(i * 2, i * 2 + 2)
        (hexByte.toInt(16) and 0xFF).toByte()
    }

    return bytes.joinToString("") { (it.toInt() xor 56).toChar().toString() }
}

suspend fun getM3u8Qualities(
    m3u8Link: String,
    referer: String,
    qualityName: String,
): List<ExtractorLink> {
    return M3u8Helper.generateM3u8(
        qualityName,
        m3u8Link,
        referer
    )
}


suspend fun <T> retryIO(
    times: Int = 3,
    delayTime: Long = 1000,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(delayTime)
        }
    }
    return block() // last attempt, let it throw
}
