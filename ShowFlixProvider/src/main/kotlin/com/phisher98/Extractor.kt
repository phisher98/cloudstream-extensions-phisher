package com.phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI

open class StreamRuby : VidhideExtractor() {
    override var name = "StreamRuby"
    override var mainUrl = "https://streamruby.com"
    override val requiresReferer = false
}


class Showflixupnshare : VidStack() {
    override var name: String = "VidStack"
    override var mainUrl: String = "https://showflix.upns.one"
}


class Rubyvidhub : VidhideExtractor() {
    override var mainUrl = "https://rubyvidhub.com"
}

class Smoothpre : VidhideExtractor() {
    override var mainUrl = "https://smoothpre.com"
    override var requiresReferer = true
}


class Showflixarchives : ExtractorApi() {
    override val name = "Showflix Archives"
    override val mainUrl = "https://showflix.sbs"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Phisher1 ","I'm here")

        val doc = app.get(url).document
        val links = doc.select("a[href]")
        Log.d("Phisher1 ","$links")

        for (link in links) {
            val href = link.attr("href")
            Log.d("Phisher1 ",href)

            if ("gdflix" in href || "appdrive" in href || "gdlink" in href) {
                if (href.contains("gdflix", ignoreCase = true) || href.contains("gdlink", ignoreCase = true) ) {
                    GDFlix().getUrl(href, referer, subtitleCallback, callback)
                } else {
                    Driveseed().getUrl(href, referer, subtitleCallback, callback)
                }
            }
        }
    }
}




@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new6.gdflix.dad"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = try {
            app.get(url)
                .document
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")
        } catch (e: Exception) {
            Log.e("Error", "Failed to fetch redirect: ${e.localizedMessage}")
            return
        } ?: url

        val document = app.get(newUrl).document
        val fileName = document.select("ul > li.list-group-item:contains(Name)").text()
            .substringAfter("Name : ")
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text()
            .substringAfter("Size : ")

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.select("a").text()

            when {
                text.contains("DIRECT DL",ignoreCase = true) -> {
                    val link = anchor.attr("href")
                    callback.invoke(
                        newExtractorLink("$source GDFlix[Direct]", "$source GDFlix[Direct] [$fileSize]", link) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("Index Links",ignoreCase = true) -> {
                    try {
                        val link = anchor.attr("href")
                        app.get("https://new6.gdflix.dad$link").document
                            .select("a.btn.btn-outline-info").amap { btn ->
                                val serverUrl = "https://new6.gdflix.dad" + btn.attr("href")
                                app.get(serverUrl).document
                                    .select("div.mb-4 > a").amap { sourceAnchor ->
                                        val sourceurl = sourceAnchor.attr("href")
                                        callback.invoke(
                                            newExtractorLink("$source GDFlix[Index]", "$source GDFlix[Index] [$fileSize]", sourceurl) {
                                                this.quality = getIndexQuality(fileName)
                                            }
                                        )
                                    }
                            }
                    } catch (e: Exception) {
                        Log.d("Index Links", e.toString())
                    }
                }

                text.contains("DRIVEBOT",ignoreCase = true) -> {
                    try {
                        val driveLink = anchor.attr("href")
                        val id = driveLink.substringAfter("id=").substringBefore("&")
                        val doId = driveLink.substringAfter("do=").substringBefore("==")
                        val baseUrls = listOf("https://drivebot.sbs", "https://drivebot.cfd")

                        baseUrls.amap { baseUrl ->
                            val indexbotLink = "$baseUrl/download?id=$id&do=$doId"
                            val indexbotResponse = app.get(indexbotLink, timeout = 100L)

                            if (indexbotResponse.isSuccessful) {
                                val cookiesSSID = indexbotResponse.cookies["PHPSESSID"]
                                val indexbotDoc = indexbotResponse.document

                                val token = Regex("""formData\.append\('token', '([a-f0-9]+)'\)""")
                                    .find(indexbotDoc.toString())?.groupValues?.get(1).orEmpty()

                                val postId = Regex("""fetch\('/download\?id=([a-zA-Z0-9/+]+)'""")
                                    .find(indexbotDoc.toString())?.groupValues?.get(1).orEmpty()

                                val requestBody = FormBody.Builder()
                                    .add("token", token)
                                    .build()

                                val headers = mapOf("Referer" to indexbotLink)
                                val cookies = mapOf("PHPSESSID" to "$cookiesSSID")

                                val downloadLink = app.post(
                                    "$baseUrl/download?id=$postId",
                                    requestBody = requestBody,
                                    headers = headers,
                                    cookies = cookies,
                                    timeout = 100L
                                ).text.let {
                                    Regex("url\":\"(.*?)\"").find(it)?.groupValues?.get(1)?.replace("\\", "").orEmpty()
                                }

                                callback.invoke(
                                    newExtractorLink("$source GDFlix[DriveBot]", "$source GDFlix[DriveBot] [$fileSize]", downloadLink) {
                                        this.referer = baseUrl
                                        this.quality = getIndexQuality(fileName)
                                    }
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("DriveBot", e.toString())
                    }
                }

                text.contains("Instant DL",ignoreCase = true) -> {
                    try {
                        val instantLink = anchor.attr("href")
                        val link = app.get(instantLink, allowRedirects = false)
                            .headers["location"]?.substringAfter("url=").orEmpty()

                        callback.invoke(
                            newExtractorLink("$source GDFlix[Instant Download]", "$source GDFlix[Instant Download] [$fileSize]", link) {
                                this.quality = getIndexQuality(fileName)
                            }
                        )
                    } catch (e: Exception) {
                        Log.d("Instant DL", e.toString())
                    }
                }

                text.contains("CLOUD DOWNLOAD",ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink("$source GDFlix[CLOUD]", "$source GDFlix[CLOUD] [$fileSize]", anchor.attr("href")) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("GoFile",ignoreCase = true) -> {
                    try {
                        app.get(anchor.attr("href")).document
                            .select(".row .row a").amap { gofileAnchor ->
                                val link = gofileAnchor.attr("href")
                                if (link.contains("gofile")) {
                                    Gofile().getUrl(link, "", subtitleCallback, callback)
                                }
                            }
                    } catch (e: Exception) {
                        Log.d("Gofile", e.toString())
                    }
                }

                text.contains("PixelDrain",ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source GDFlix[Pixeldrain]",
                            "$source GDFlix[Pixeldrain] [$fileSize]",
                            anchor.attr("href"),
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    Log.d("Error", "No Server matched")
                }
            }
        }

        // Cloudflare backup links
        try {
            val types = listOf("type=1", "type=2")
            types.map { type ->
                val sourceurl = app.get("${newUrl.replace("file", "wfile")}?$type")
                    .document.select("a.btn-success").attr("href")

                if (source?.isNotEmpty() == true) {
                    callback.invoke(
                        newExtractorLink("$source GDFlix[CF]", "$source GDFlix[CF] [$fileSize]", sourceurl) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("CF", e.toString())
        }
    }
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        try {
            val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return
            val responseText = app.post("$mainApi/accounts").text
            val json = JSONObject(responseText)
            val token = json.getJSONObject("data").getString("token")

            val globalJs = app.get("$mainUrl/dist/js/global.js").text
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""")
                .find(globalJs)?.groupValues?.getOrNull(1) ?: return

            val responseTextfile = app.get(
                "$mainApi/contents/$id?wt=$wt",
                headers = mapOf("Authorization" to "Bearer $token")
            ).text

            val fileDataJson = JSONObject(responseTextfile)

            val data = fileDataJson.getJSONObject("data")
            val children = data.getJSONObject("children")
            val firstFileId = children.keys().asSequence().first()
            val fileObj = children.getJSONObject(firstFileId)

            val link = fileObj.getString("link")
            val fileName = fileObj.getString("name")
            val fileSize = fileObj.getLong("size")

            val sizeFormatted = if (fileSize < 1024L * 1024 * 1024) {
                "%.2f MB".format(fileSize / 1024.0 / 1024)
            } else {
                "%.2f GB".format(fileSize / 1024.0 / 1024 / 1024)
            }

            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "Gofile [$sizeFormatted]",
                    link
                ) {
                    this.quality = getQuality(fileName)
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        } catch (e: Exception) {
            Log.e("Gofile", "Error occurred: ${e.message}")
        }
    }

    private fun getQuality(fileName: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(fileName ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}

open class Driveseed : ExtractorApi() {
    override val name: String = "Driveseed"
    override val mainUrl: String = "https://driveseed.org"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private suspend fun CFType1(url: String): List<String> {
        return runCatching {
            app.get("$url?type=1").document
                .select("a.btn-success")
                .mapNotNull { it.attr("href").takeIf { href -> href.startsWith("http") } }
        }.getOrElse {
            Log.e("Driveseed", "CFType1 error: ${it.message}")
            emptyList()
        }
    }

    private suspend fun resumeCloudLink(baseUrl: String, path: String): String? {
        return runCatching {
            app.get(baseUrl + path).document
                .selectFirst("a.btn-success")?.attr("href")
                ?.takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "ResumeCloud error: ${it.message}")
            null
        }
    }

    private suspend fun resumeBot(url: String): String? {
        return runCatching {
            val response = app.get(url)
            val docString = response.document.toString()
            val ssid = response.cookies["PHPSESSID"].orEmpty()
            val token = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val path = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/+]+)'").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val baseUrl = url.substringBefore("/download")

            if (token.isEmpty() || path.isEmpty()) return@runCatching null

            val json = app.post(
                "$baseUrl/download?id=$path",
                requestBody = FormBody.Builder().addEncoded("token", token).build(),
                headers = mapOf("Accept" to "*/*", "Origin" to baseUrl, "Sec-Fetch-Site" to "same-origin"),
                cookies = mapOf("PHPSESSID" to ssid),
                referer = url
            ).text

            JSONObject(json).getString("url").takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "ResumeBot error: ${it.message}")
            null
        }
    }

    private suspend fun instantLink(finallink: String): String? {
        return runCatching {
            val uri = URI(finallink)
            val host = uri.host ?: if (finallink.contains("video-leech")) "video-leech.pro" else "video-seed.pro"
            val token = finallink.substringAfter("url=")
            val response = app.post(
                "https://$host/api",
                data = mapOf("keys" to token),
                referer = finallink,
                headers = mapOf("x-token" to host)
            ).text

            response.substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
                .takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "InstantLink error: ${it.message}")
            null
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val Basedomain = getBaseUrl(url)

        val document = try {
            if (url.contains("r?key=")) {
                val temp = app.get(url).document.selectFirst("script")
                    ?.data()
                    ?.substringAfter("replace(\"")
                    ?.substringBefore("\")")
                    .orEmpty()
                app.get(mainUrl + temp).document
            } else {
                app.get(url).document
            }
        } catch (e: Exception) {
            Log.e("Driveseed", "getUrl page load error: ${e.message}")
            return
        }

        val qualityText = document.selectFirst("li.list-group-item")?.text().orEmpty()
        val rawFileName = qualityText.replace("Name : ", "").trim()
        val fileName = cleanTitle(rawFileName)
        val size = document.selectFirst("li:nth-child(3)")?.text().orEmpty().replace("Size : ", "").trim()

        val labelExtras = buildString {
            if (fileName.isNotEmpty()) append("[$fileName]")
            if (size.isNotEmpty()) append("[$size]")
        }

        document.select("div.text-center > a").forEach { element ->
            val text = element.text()
            val href = element.attr("href")
            Log.d("Driveseed", "Link: $href")

            if (href.isNotBlank()) {
                when {
                    text.contains("Instant Download", ignoreCase = true) -> {
                        instantLink(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name Instant(Download) $labelExtras",
                                    "$name Instant(Download) $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Resume Worker Bot", ignoreCase = true) -> {
                        resumeBot(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeBot(VLC) $labelExtras",
                                    "$name ResumeBot(VLC) $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Direct Links", ignoreCase = true) -> {
                        CFType1(Basedomain + href).forEach { link ->
                            callback(
                                newExtractorLink(
                                    "$name CF Type1 $labelExtras",
                                    "$name CF Type1 $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Resume Cloud", ignoreCase = true) -> {
                        resumeCloudLink(Basedomain, href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeCloud $labelExtras",
                                    "$name ResumeCloud $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
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
