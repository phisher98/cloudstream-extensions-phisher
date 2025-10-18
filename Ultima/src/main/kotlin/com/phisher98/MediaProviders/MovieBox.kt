package com.phisher98

import androidx.core.net.toUri
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MovieBoxMediaProvider : MediaProvider() {
    override val name = "MovieBox"
    override val domain = "https://api.inmoviebox.com"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
        url: String,
        data: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val title=data.title
        try {
            if (title.isNullOrBlank()) return

            val url = "$domain/wefeed-mobile-bff/subject-api/search/v2"
            val jsonBody = """{"page":1,"perPage":10,"keyword":"$title"}"""
            val xClientToken = generateXClientToken()
            val xTrSignature = generateXTrSignature(
                "POST", "application/json", "application/json; charset=utf-8", url, jsonBody
            )
            val headers = mapOf(
                "user-agent" to "com.community.mbox.in/50020042 (Linux; Android 16)",
                "accept" to "application/json",
                "content-type" to "application/json",
                "x-client-token" to xClientToken,
                "x-tr-signature" to xTrSignature,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03"}""",
                "x-client-status" to "0"
            )

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val response = app.post(url, headers = headers, requestBody = requestBody)
            if (response.code != 200) return

            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(response.body.string())
            val results = root["data"]?.get("results") ?: return

            val matchingIds = mutableListOf<String>()
            for (result in results) {
                val subjects = result["subjects"] ?: continue
                for (subject in subjects) {
                    val name = subject["title"]?.asText() ?: continue
                    val id = subject["subjectId"]?.asText() ?: continue
                    val type = subject["subjectType"]?.asInt() ?: 0
                    if (name.contains(title, ignoreCase = true) && (type == 1 || type == 2)) {
                        matchingIds.add(id)
                    }
                }
            }
            if (matchingIds.isEmpty()) return

            for (id in matchingIds) {
                try {
                    val subjectUrl = "$domain/wefeed-mobile-bff/subject-api/get?subjectId=$id"
                    val subjectXToken = generateXClientToken()
                    val subjectXSign = generateXTrSignature("GET", "application/json", "application/json", subjectUrl)
                    val subjectHeaders = headers + mapOf(
                        "x-client-token" to subjectXToken,
                        "x-tr-signature" to subjectXSign
                    )
                    val subjectRes = app.get(subjectUrl, headers = subjectHeaders)
                    if (subjectRes.code != 200) continue

                    val subjectJson = mapper.readTree(subjectRes.body.string())
                    val subjectData = subjectJson["data"]
                    val subjectIds = mutableListOf<Pair<String, String>>()
                    var originalLanguageName = "Original"

                    // handle dubs
                    val dubs = subjectData?.get("dubs")
                    if (dubs != null && dubs.isArray) {
                        for (dub in dubs) {
                            val dubId = dub["subjectId"]?.asText()
                            val lanName = dub["lanName"]?.asText()
                            if (dubId != null && lanName != null) {
                                if (dubId == id) {
                                    originalLanguageName = lanName
                                } else {
                                    subjectIds.add(Pair(dubId, lanName))
                                }
                            }
                        }
                    }
                    subjectIds.add(0, Pair(id, originalLanguageName))

                    for ((subjectId, language) in subjectIds) {
                        val playUrl =
                            "$domain/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=${data.season ?: 0}&ep=${data.episode ?: 0}"
                        val token = generateXClientToken()
                        val sign = generateXTrSignature("GET", "application/json", "application/json", playUrl)
                        val playHeaders = headers + mapOf("x-client-token" to token, "x-tr-signature" to sign)

                        val playRes = app.get(playUrl, headers = playHeaders)
                        if (playRes.code != 200) continue

                        val playRoot = mapper.readTree(playRes.body.string())
                        val streams = playRoot["data"]?.get("streams") ?: continue
                        if (!streams.isArray) continue

                        for (stream in streams) {
                            val streamId = stream["id"]?.asText() ?: "$subjectId|${data.season}|${data.episode}"
                            val subjectTitle = subjectData?.get("title")?.asText() ?: "Unknown Title"
                            val format = stream["format"]?.asText() ?: ""
                            val signCookie = stream["signCookie"]?.asText()?.takeIf { it.isNotEmpty() }

                            val resolutionNodes = stream["resolutionList"] ?: stream["resolutions"]

                            if (resolutionNodes != null && resolutionNodes.isArray) {
                                for (resNode in resolutionNodes) {
                                    val resUrl = resNode["resourceLink"]?.asText() ?: continue
                                    val quality = resNode["resolution"]?.asInt() ?: 0

                                    callback.invoke(
                                        newExtractorLink(
                                            source = "MovieBox",
                                            name = "MovieBox (${language.capitalize()}) [$subjectTitle]",
                                            url = resUrl,
                                            type = when {
                                                resUrl.startsWith("magnet:", true) -> ExtractorLinkType.MAGNET
                                                resUrl.endsWith(".mpd", true) -> ExtractorLinkType.DASH
                                                resUrl.endsWith(".torrent", true) -> ExtractorLinkType.TORRENT
                                                format.equals("HLS", true) || resUrl.endsWith(".m3u8", true) -> ExtractorLinkType.M3U8
                                                else -> INFER_TYPE
                                            }
                                        ) {
                                            this.headers = mapOf("Referer" to domain) +
                                                    (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                            this.quality = getQualityFromName("$quality")
                                        }
                                    )
                                }
                            } else {
                                // fallback single url
                                val singleUrl = stream["url"]?.asText() ?: continue
                                val resText = stream["resolutions"]?.asText() ?: ""

                                callback.invoke(
                                    newExtractorLink(
                                        source = "MovieBox",
                                        name = "MovieBox (${language.capitalize()}) [$subjectTitle]",
                                        url = singleUrl,
                                        type = when {
                                            singleUrl.startsWith("magnet:", true) -> ExtractorLinkType.MAGNET
                                            singleUrl.endsWith(".mpd", true) -> ExtractorLinkType.DASH
                                            singleUrl.endsWith(".torrent", true) -> ExtractorLinkType.TORRENT
                                            format.equals("HLS", true) || singleUrl.endsWith(".m3u8", true) -> ExtractorLinkType.M3U8
                                            else -> INFER_TYPE
                                        }
                                    ) {
                                        this.headers = mapOf("Referer" to domain) +
                                                (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                        this.quality = getQualityFromName(resText)
                                    }
                                )
                            }

                            // subtitles
                            val subLinks = listOf(
                                "$domain/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=$streamId",
                                "$domain/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$streamId&episode=${data.episode ?: 0}"
                            )

                            for (subLink in subLinks) {
                                val subToken = generateXClientToken()
                                val subSign = generateXTrSignature("GET", "", "", subLink)

                                val subHeaders = mapOf(
                                    "User-Agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                                    "Accept" to "",
                                    "Content-Type" to "",
                                    "X-Client-Info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                                    "X-Client-Status" to "0",
                                    "x-client-token" to subToken,
                                    "x-tr-signature" to subSign
                                )

                                val subRes = app.get(subLink, headers = subHeaders)
                                if (subRes.code != 200) continue

                                val subRoot = mapper.readTree(subRes.body.string())
                                val captions = subRoot["data"]?.get("extCaptions")
                                if (captions != null && captions.isArray) {
                                    for (caption in captions) {
                                        val captionUrl = caption["url"]?.asText() ?: continue
                                        val lang = caption["language"]?.asText()
                                            ?: caption["lanName"]?.asText()
                                            ?: caption["lan"]?.asText()
                                            ?: "Unknown"
                                        subtitleCallback.invoke(
                                            newSubtitleFile(
                                                url = captionUrl,
                                                lang = "$lang (${language.capitalize()})"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    continue
                }
            }
            return
        } catch (_: Exception) {
            return
        }
    }
}


private fun md5(input: ByteArray): String {
    return MessageDigest.getInstance("MD5").digest(input)
        .joinToString("") { "%02x".format(it) }
}

private fun reverseString(input: String): String = input.reversed()

fun generateXClientToken(hardcodedTimestamp: Long? = null): String {
    val timestamp = (hardcodedTimestamp ?: System.currentTimeMillis()).toString()
    val reversed = reverseString(timestamp)
    val hash = md5(reversed.toByteArray())
    return "$timestamp,$hash"
}

fun generateXTrSignature(
    method: String,
    accept: String? = "application/json",
    contentType: String? = "application/json",
    url: String,
    body: String? = null,
    useAltKey: Boolean = false,
    hardcodedTimestamp: Long? = null
): String {
    val timestamp = hardcodedTimestamp ?: System.currentTimeMillis()

    val canonical = buildCanonicalString(
        method = method,
        accept = accept,
        contentType = contentType,
        url = url,
        body = body,
        timestamp = timestamp
    )
    val secretKey = if (useAltKey) {
        BuildConfig.MOVIEBOX_SECRET_KEY_ALT
    } else {
        BuildConfig.MOVIEBOX_SECRET_KEY_DEFAULT
    }
    val secretBytes = android.util.Base64.decode(secretKey, android.util.Base64.DEFAULT)
    val mac = Mac.getInstance("HmacMD5").apply {
        init(SecretKeySpec(secretBytes, "HmacMD5"))
    }
    val rawSignature = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
    val signatureBase64 = android.util.Base64.encodeToString(rawSignature, android.util.Base64.NO_WRAP)
    return "$timestamp|2|$signatureBase64"
}


private fun buildCanonicalString(
    method: String,
    accept: String?,
    contentType: String?,
    url: String,
    body: String?,
    timestamp: Long
): String {
    val parsed = url.toUri()
    val path = parsed.path ?: ""

    // Build query string with sorted parameters (if any)
    val query = if (parsed.queryParameterNames.isNotEmpty()) {
        parsed.queryParameterNames.sorted().joinToString("&") { key ->
            parsed.getQueryParameters(key).joinToString("&") { value ->
                "$key=$value"  // Don't URL encode here - Python doesn't do it
            }
        }
    } else ""

    val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path

    val bodyBytes = body?.toByteArray(Charsets.UTF_8)
    val bodyHash = if (bodyBytes != null) {
        val trimmed = if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes
        md5(trimmed)
    } else ""

    val bodyLength = bodyBytes?.size?.toString() ?: ""
    return "${method.uppercase()}\n" +
            "${accept ?: ""}\n" +
            "${contentType ?: ""}\n" +
            "$bodyLength\n" +
            "$timestamp\n" +
            "$bodyHash\n" +
            canonicalUrl
}