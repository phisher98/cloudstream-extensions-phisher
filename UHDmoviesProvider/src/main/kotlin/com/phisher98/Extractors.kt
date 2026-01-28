package com.phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import okhttp3.FormBody
import com.lagradost.api.Log

class Driveleech : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.org"
}

class DriveleechPro : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.pro"
}

class DriveleechNet : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.net"
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
            app.get("$url?type=1").documentLarge
                .select("a.btn-success")
                .mapNotNull { it.attr("href").takeIf { href -> href.startsWith("http") } }
        }.getOrElse {
            Log.e("Driveseed", "CFType1 error: ${it.message}")
            emptyList()
        }
    }

    private suspend fun resumeCloudLink(baseUrl: String, path: String): String? {
        return runCatching {
            app.get(baseUrl + path).documentLarge
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
            val docString = response.documentLarge.toString()
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

    private suspend fun instantLink(finalLink: String): String? {
        return runCatching {
            val response = app.get(finalLink)
            val resolvedUrl = response.url
            val extracted = resolvedUrl
                .substringAfter("url=", missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
            extracted
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
                val temp = app.get(url).documentLarge.selectFirst("script")
                    ?.data()
                    ?.substringAfter("replace(\"")
                    ?.substringBefore("\")")
                    .orEmpty()
                app.get(mainUrl + temp).documentLarge
            } else {
                app.get(url).documentLarge
            }
        } catch (e: Exception) {
            Log.e("Driveseed", "getUrl page load error: ${e.message}")
            return
        }

        val qualityText = document.selectFirst("li.list-group-item")?.text().orEmpty()
        val rawFileName = qualityText.replace("Name : ", "").trim()
        val cleaned = removeLeadingIndex(rawFileName)
        val fileName = cleanTitle(cleaned)
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
                                    "$name Instant(Download) (Use VLC)",
                                    "$name Instant(Download) (Use VLC) $labelExtras",
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
                                    "$name ResumeBot(VLC)",
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
                                    "$name CF Type1",
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
                                    "$name ResumeCloud",
                                    "$name ResumeCloud $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Cloud Download", ignoreCase = true) -> {
                        callback(
                            newExtractorLink(
                                "$name Cloud Download",
                                "$name Cloud Download $labelExtras",
                                url = href
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

private fun removeLeadingIndex(title: String): String {
    return title.replace(
        Regex("^[\\[(]?\\s*\\d+\\s*[])\\-_.]*\\s*"),
        ""
    )
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
    val hdrTags = setOf("SDR", "HDR", "HDR10", "HDR10+", "DV", "DOLBYVISION")

    val tags = mutableListOf<String>()
    val titleParts = mutableListOf<String>()

    for (part in parts) {
        val p = part.uppercase()

        when {
            sourceTags.contains(p) -> tags += p
            codecTags.contains(p) -> tags += p
            audioTags.any { p.startsWith(it) } -> tags += p
            audioExtras.contains(p) -> tags += p
            hdrTags.contains(p) -> tags += if (p == "DV" || p == "DOLBYVISION") "DOLBYVISION" else p
            p == "NF" || p == "CR" -> tags += p
            else -> titleParts += part
        }
    }

    val cleanTitle = titleParts
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

    val cleanTags = tags.distinct().joinToString(" ")

    return listOf(cleanTitle, cleanTags)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

