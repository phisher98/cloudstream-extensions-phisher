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


fun cleanTitle(title: String): String {
    val parts = title.split(" ", "-", "_")

    val qualityTags = listOf(
        "480p", "720p", "1080p", "2160p", "4K",
        "WEBRip", "WEB-DL", "BluRay", "HDRip", "DVDRip",
        "HDTV", "CAM", "TS", "BRRip", "BDRip"
    )

    val audioTags = listOf(
        "AAC", "AC3", "DTS", "MP3", "FLAC", "DD", "DDP", "EAC3", "Atmos"
    )

    val subTags = listOf(
        "ESub", "ESubs", "Subs", "MultiSub", "NoSub", "EnglishSub", "HindiSub"
    )

    val codecTags = listOf(
        "x264", "x265", "H264", "HEVC", "AVC"
    )

    // start from first quality token
    val startIndex = parts.indexOfFirst { part ->
        qualityTags.any { tag -> part.equals(tag, ignoreCase = true) }
    }

    if (startIndex == -1) return ""

    // actively USE audio / sub / codec tags
    val filtered = parts
        .drop(startIndex)
        .filter { part ->
            qualityTags.any { part.equals(it, true) } ||
                    audioTags.any { part.contains(it, true) } ||
                    subTags.any { part.contains(it, true) } ||
                    codecTags.any { part.contains(it, true) } ||
                    part.any { it.isDigit() } // keeps 5.1 / 2.0 / 1080p
        }

    return filtered
        .joinToString(" ")
        .replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")
        .replace("[", "")
        .replace("]", "")
        .replace("(", "")
        .replace(")", "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
