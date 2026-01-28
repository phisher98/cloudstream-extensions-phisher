package com.dudefilms

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.PixelDrain
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI

class Hubstream : VidStack() {
    override var mainUrl = "https://hubstream.*"
}

class Hubcdnn : ExtractorApi() {
    override val name = "Hubcdn"
    override val mainUrl = "https://hubcdn.*"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url).documentLarge.toString().let {
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

class PixelDrainDev : PixelDrain(){
    override var mainUrl = "https://pixeldrain.dev"
}

class Hubdrive : ExtractorApi() {
    override val name = "Hubdrive"
    override val mainUrl = "https://hubdrive.space"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url, timeout = 2000).documentLarge.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud",ignoreCase = true)) HubCloud().getUrl(href,"HubDrive",subtitleCallback,callback)
        else loadExtractor(href,"HubDrive",subtitleCallback, callback)
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



class HUBCDN : ExtractorApi() {
    override val name = "HUBCDN"
    override val mainUrl = "https://hubcdn.*"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).documentLarge
        val scriptText = doc.selectFirst("script:containsData(var reurl)")?.data()

        val encodedUrl = Regex("reurl\\s*=\\s*\"([^\"]+)\"")
            .find(scriptText ?: "")
            ?.groupValues?.get(1)
            ?.substringAfter("?r=")

        val decodedUrl = encodedUrl?.let { base64Decode(it) }?.substringAfterLast("link=")


        if (decodedUrl != null) {
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    decodedUrl,
                    INFER_TYPE,
                )
                {
                    this.quality=Qualities.Unknown.value
                }
            )
        }
    }
}

class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://*.gdflix.*"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val source = ""
        val newUrl = try {
            app.get(url)
                .documentLarge
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")
        } catch (e: Exception) {
            Log.e("Error", "Failed to fetch redirect: ${e.localizedMessage}")
            return
        } ?: url

        val document = app.get(newUrl).documentLarge
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
                        app.get("https://new6.gdflix.dad$link").documentLarge
                            .select("a.btn.btn-outline-info").amap { btn ->
                                val serverUrl = "https://new6.gdflix.dad" + btn.attr("href")
                                app.get(serverUrl).documentLarge
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


                text.contains("GoFile",ignoreCase = true) -> {
                    try {
                        app.get(anchor.attr("href")).documentLarge
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

                text.contains("PixelDrain",ignoreCase = true) || text.contains("Pixel",ignoreCase = true)-> {
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
                    .documentLarge.select("a.btn-success").attr("href")

                if (source.isNotEmpty()) {
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