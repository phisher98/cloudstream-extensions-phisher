package com.Filmyfiy


import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets


class Filesdl : ExtractorApi() {
    override val name            = "Filesdl"
    override val mainUrl         = "https://new6.filesdl.site"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {
        val res = app.get(url).documentLarge
        val titleText = res.select("div.title").text()
        val qualityRegex = Regex("(\\d{3,4}p)", RegexOption.IGNORE_CASE)
        val quality = qualityRegex.find(titleText)?.value ?: "Unknown"
        res.select("div.container a").map {
            val source = it.text()
            val href = it.attr("href")
            when {

                source.contains("Hubcloud", ignoreCase = true) -> HubCloud().getUrl(href,"Filmyfiy",subtitleCallback,callback)

                source.contains("GDFLIX", ignoreCase = true) -> GDFlix().getUrl(href,"Filmyfiy",subtitleCallback,callback)

                source.contains("Gofile", ignoreCase = true) -> Gofile().getUrl(href,"Filmyfiy",subtitleCallback,callback)

                source.contains("Direct Download", ignoreCase = true) || source.contains("Ultra FastDL", ignoreCase = true) || source.contains("Fast Cloud-02", ignoreCase = true) -> {
                    val response = app.get(href, allowRedirects = false)
                    val redirectUrl = response.headers["location"] ?: href
                    callback(
                        newExtractorLink(
                            "Direct Download",
                            "Filmyfiy [Direct Download]",
                            redirectUrl,
                            INFER_TYPE
                        ) {
                            this.quality = getQualityFromName(quality)
                        }
                    )
                }

                source.contains("Fast Cloud", ignoreCase = true) -> {
                    callback(
                        newExtractorLink(
                            "Fast Cloud",
                            "Filmyfiy [Fast Cloud]",
                            href,
                            INFER_TYPE
                        ) {
                            this.quality = getQualityFromName(quality)
                        }
                    )
                }
            }
        }
    }
}

class HubCloud : ExtractorApi() {

    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.foo"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val tag = "HubCloud"
        val ref = referer.orEmpty()

        val uri = runCatching { URI(url) }.getOrElse {
            Log.e(tag, "Invalid URL: ${it.message}")
            return
        }

        val realUrl = uri.toString()
        val baseUrl = "${uri.scheme}://${uri.host}"

        val href = runCatching {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val raw = app.get(realUrl).document
                    .selectFirst("#download")
                    ?.attr("href")
                    .orEmpty()

                if (raw.startsWith("http", true)) raw
                else baseUrl.trimEnd('/') + "/" + raw.trimStart('/')
            }
        }.getOrElse {
            Log.e(tag, "Failed to extract href: ${it.message}")
            ""
        }

        if (href.isBlank()) return

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()

        val headerDetails = cleanTitle(header)
        val quality = getIndexQuality(header)

        val labelExtras = buildString {
            if (headerDetails.isNotEmpty()) append("[$headerDetails]")
            if (size.isNotEmpty()) append("[$size]")
        }

        document.select("a.btn").forEach { element ->
            val link = element.attr("href")
            val text = element.ownText()
            val label = text.lowercase()
            Log.d("Phisher",label)
            when {
                "fsl server" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [FSL Server]",
                            "$ref [FSL Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "download file" in label -> {
                    callback(
                        newExtractorLink(
                            ref,
                            "$ref $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "buzzserver" in label -> {
                    val resp = app.get("$link/download", referer = link, allowRedirects = false)
                    val dlink = resp.headers["hx-redirect"]
                        ?: resp.headers["HX-Redirect"].orEmpty()

                    if (dlink.isNotBlank()) {
                        callback(
                            newExtractorLink(
                                "$ref [BuzzServer]",
                                "$ref [BuzzServer] $labelExtras",
                                dlink
                            ) { this.quality = quality }
                        )
                    } else {
                        Log.w(tag, "BuzzServer: No redirect")
                    }
                }

                "pixeldra" in label || "pixelserver" in label || "pixel server" in label || "pixeldrain" in label -> {
                    val base = getBaseUrl(link)
                    val finalUrl =
                        if ("download" in link) link
                        else "$base/api/file/${link.substringAfterLast("/")}?download"

                    callback(
                        newExtractorLink(
                            "$ref Pixeldrain",
                            "$ref Pixeldrain $labelExtras",
                            finalUrl
                        ) { this.quality = quality }
                    )
                }

                "s3 server" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [S3 Server]",
                            "$ref [S3 Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "fslv2" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [FSLv2]",
                            "$ref [FSLv2] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "mega server" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [Mega Server]",
                            "$ref [Mega Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                /*
                "10gbps" in label -> {
                    var current = link

                    repeat(3) {
                        val resp = app.get(current, allowRedirects = false)
                        val loc = resp.headers["location"] ?: return@repeat

                        if ("link=" in loc) {
                            callback(
                                newExtractorLink(
                                    "$ref 10Gbps [Download]",
                                    "$ref 10Gbps [Download] $labelExtras",
                                    loc.substringAfter("link=")
                                ) { this.quality = quality }
                            )
                        }
                        current = loc
                    }

                    Log.e(tag, "10Gbps: Redirect limit reached")
                }

                 */
                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]")
            .find(str.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Qualities.P2160.value
    }

    private fun getBaseUrl(url: String): String {
        return runCatching {
            URI(url).let { "${it.scheme}://${it.host}" }
        }.getOrDefault("")
    }

    private fun cleanTitle(title: String): String {

        val name = title.replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")

        val normalized = name
            .replace(Regex("WEB[-_. ]?DL", RegexOption.IGNORE_CASE), "WEB-DL")
            .replace(Regex("WEB[-_. ]?RIP", RegexOption.IGNORE_CASE), "WEBRIP")
            .replace(Regex("H[ .]?265", RegexOption.IGNORE_CASE), "H265")
            .replace(Regex("H[ .]?264", RegexOption.IGNORE_CASE), "H264")
            .replace(Regex("DDP[ .]?([0-9]\\.[0-9])", RegexOption.IGNORE_CASE), "DDP$1")

        val parts = normalized.split(" ", "_", ".")

        val sourceTags = setOf(
            "WEB-DL", "WEBRIP", "BLURAY", "HDRIP",
            "DVDRIP", "HDTV", "CAM", "TS", "BRRIP", "BDRIP"
        )

        val codecTags = setOf("H264", "H265", "X264", "X265", "HEVC", "AVC")

        val audioTags = setOf("AAC", "AC3", "DTS", "MP3", "FLAC", "DD", "DDP", "EAC3")

        val audioExtras = setOf("ATMOS")

        val hdrTags = setOf("SDR","HDR", "HDR10", "HDR10+", "DV", "DOLBYVISION")

        val filtered = parts.mapNotNull { part ->
            val p = part.uppercase()

            when {
                sourceTags.contains(p) -> p
                codecTags.contains(p) -> p
                audioTags.any { p.startsWith(it) } -> p
                audioExtras.contains(p) -> p
                hdrTags.contains(p) -> {
                    when (p) {
                        "DV", "DOLBYVISION" -> "DOLBYVISION"
                        else -> p
                    }
                }
                p == "NF" || p == "CR" -> p
                else -> null
            }
        }

        return filtered.distinct().joinToString(" ")
    }
}

class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://*.gdflix.*"
    override val requiresReferer = false

    private suspend fun getLatestUrl(): String {
        val url = JSONObject(
            app.get("https://raw.githubusercontent.com/SaurabhKaperwan/Utils/refs/heads/main/urls.json").text
        ).optString("gdflix")
        if(url.isNullOrEmpty()) {
            return mainUrl
        }
        return url
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val latestUrl = getLatestUrl()
        val newUrl = url.replace(mainUrl, latestUrl)
        val document = app.get(newUrl).documentLarge
        val fileName = document.select("ul > li.list-group-item:contains(Name)").text()
            .substringAfter("Name : ")
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text()
            .substringAfter("Size : ")
        val quality = getIndexQuality(fileName)

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.select("a").text()
            val link = anchor.attr("href")

            when {
                text.contains("DIRECT DL") -> {
                    callback.invoke(
                        newExtractorLink("GDFlix[Direct]", "GDFlix[Direct] $fileName[$fileSize]", link) {
                            this.quality = quality
                        }
                    )
                }

                text.contains("CLOUD DOWNLOAD [R2]") -> {
                    val link = URLDecoder.decode(link.substringAfter("url="), StandardCharsets.UTF_8.toString())
                    callback.invoke(
                        newExtractorLink("GDFlix[Cloud]", "GDFlix[Cloud] $fileName[$fileSize]", link) {
                            this.quality = quality
                        }
                    )
                }

                text.contains("pixeldra", ignoreCase = true) || text.contains("pixel", ignoreCase = true) -> {
                    val baseUrlLink = getBaseUrl(link)
                    val finalURL = if (link.contains("download", true)) link
                    else "$baseUrlLink/api/file/${link.substringAfterLast("/")}?download"

                    callback(
                        newExtractorLink(
                            "GDFlix Pixeldrain",
                            "GDFlix Pixeldrain $fileName[$fileSize]",
                            finalURL
                        ) { this.quality = quality }
                    )
                }

                text.contains("Index Links") -> {
                    try {
                        app.get("$latestUrl$link").documentLarge
                            .select("a.btn.btn-outline-info").amap { btn ->
                                val serverUrl = latestUrl + btn.attr("href")
                                app.get(serverUrl).documentLarge
                                    .select("div.mb-4 > a").amap { sourceAnchor ->
                                        val source = sourceAnchor.attr("href")
                                        callback.invoke(
                                            newExtractorLink("GDFlix[Index]", "GDFlix[Index] $fileName[$fileSize]", source) {
                                                this.quality = quality
                                            }
                                        )
                                    }
                            }
                    } catch (e: Exception) {
                        Log.d("Index Links", e.toString())
                    }
                }

                text.contains("DRIVEBOT") -> {
                    try {
                        val driveLink = link
                        val id = driveLink.substringAfter("id=").substringBefore("&")
                        val doId = driveLink.substringAfter("do=").substringBefore("==")
                        val baseUrls = listOf("https://drivebot.sbs", "https://indexbot.site")

                        baseUrls.amap { baseUrl ->
                            val indexbotLink = "$baseUrl/download?id=$id&do=$doId"
                            val indexbotResponse = app.get(indexbotLink, timeout = 100L)

                            if (indexbotResponse.isSuccessful) {
                                val cookiesSSID = indexbotResponse.cookies["PHPSESSID"]
                                val indexbotDoc = indexbotResponse.documentLarge

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
                                    newExtractorLink("GDFlix[DriveBot]", "GDFlix[DriveBot] $fileName[$fileSize]", downloadLink) {
                                        this.referer = baseUrl
                                        this.quality = quality
                                    }
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("DriveBot", e.toString())
                    }
                }

                text.contains("Instant DL") -> {
                    try {
                        val instantLink = link
                        val link = app.get(instantLink, allowRedirects = false)
                            .headers["location"]?.substringAfter("url=").orEmpty()

                        callback.invoke(
                            newExtractorLink("GDFlix[Instant Download]", "GDFlix[Instant Download] $fileName[$fileSize]", link) {
                                this.quality = quality
                            }
                        )
                    } catch (e: Exception) {
                        Log.d("Instant DL", e.toString())
                    }
                }
                text.contains("GoFile") -> {
                    try {
                        app.get(link).documentLarge
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

                else -> {
                    Log.d("Error", "No Server matched")
                }
            }
        }
    }
    fun getBaseUrl(url: String): String {
        return try {
            URI(url).let { "${it.scheme}://${it.host}" }
        } catch (_: Exception) {
            ""
        }
    }
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

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}