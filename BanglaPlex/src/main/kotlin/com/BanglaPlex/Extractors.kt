package com.BanglaPlex

import android.annotation.SuppressLint
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.extractors.Vtbe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


class Vectorx : Chillx() {
    override val name = "BanglaPlex"
    override val mainUrl = "https://bestx.stream"
}

class Boosterx : Chillx() {
    override val name = "Boosterx"
    override val mainUrl = "https://boosterx.stream"
    override val requiresReferer = true
}

class Iplayerhls : Vtbe() {
    override var name = "Iplayerhls"
    override var mainUrl = "https://iplayerhls.com"
}

open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseurl=getBaseUrl(url)
        val headers = mapOf(
            "Origin" to baseurl,
            "Referer" to baseurl,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"
        )

        try {
            val res = app.get(url, referer = referer ?: mainUrl, headers = headers).toString()

            // Extract encoded string from response
            val encodedString = Regex("(?:const|let|var|window\\.\\w+)\\s+\\w*\\s*=\\s*'([^']{30,})'").find(res)
                ?.groupValues?.get(1)?.trim() ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Get Password from pastebin(Shareable, Auto-Update)
            val keyUrl = "https://chillx.supe2372.workers.dev/getKey"
            val passwordHex = app.get(keyUrl).text
            val password = passwordHex.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
            val decryptedData = decryptData(encodedString, password)
                ?: throw Exception("Decryption failed")

            // Extract m3u8 URL
            val m3u8 = Regex("(https?://[^\\s\"'\\\\]*m3u8[^\\s\"'\\\\]*)").find(decryptedData)
                ?.groupValues?.get(1)?.trim() ?: ""
            if (m3u8.isEmpty()) {
                throw Exception("m3u8 URL not found")
            }

            // Prepare headers for callback
            val header = mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT
            )

            // Return the extractor link
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                    this.headers = header
                }
            )

            // Extract and return subtitles
            val subtitles = extractSrtSubtitles(decryptedData)
            subtitles.forEachIndexed { _, (language, url) ->
                subtitleCallback.invoke(SubtitleFile(language, url))
            }

        } catch (e: Exception) {
            Log.e("Anisaga Stream", "Error: ${e.message}")
        }
    }


    @SuppressLint("NewApi")
    fun decryptData(encryptedData: String, password: String): String? {
        val decodedBytes = Base64.getDecoder().decode(encryptedData)
        val keyBytes = password.toByteArray(Charsets.UTF_8)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Try AES-CBC decryption first (assumes IV is 16 bytes)
        try {
            val ivBytesCBC = decodedBytes.copyOfRange(0, 16)
            val encryptedBytesCBC = decodedBytes.copyOfRange(16, decodedBytes.size)

            val ivSpec = IvParameterSpec(ivBytesCBC)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytesCBC)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            println("CBC decryption failed, trying AES-GCM...")
        }

        // Fallback to AES-GCM decryption (assumes IV is 12 bytes)
        return try {
            val ivBytesGCM = decodedBytes.copyOfRange(0, 12)
            val encryptedBytesGCM = decodedBytes.copyOfRange(12, decodedBytes.size)

            val gcmSpec = GCMParameterSpec(128, ivBytesGCM)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher.updateAAD("NeverGiveUp".toByteArray(Charsets.UTF_8))

            val decryptedBytes = cipher.doFinal(encryptedBytesGCM)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: BadPaddingException) {
            println("Decryption failed: Bad padding or incorrect password.")
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """\[(.*?)](https?://[^\s,"]+\.srt)""".toRegex()
        return regex.findAll(subtitle).map {
            it.groupValues[1] to it.groupValues[2]
        }.toList()
    }
    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

}


open class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new.gdflix.dad"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        val fileName = document.selectFirst("ul > li.list-group-item")?.text()?.substringAfter("Name : ") ?: ""

        document.select("div.text-center a").amap {
            val text = it.text()
            val link = it.attr("href")

            when {
                text.contains("FAST CLOUD") && !text.contains("ZIP") -> {
                    val videoUrl = if (link.contains("mkv") || link.contains("mp4")) {
                        link
                    } else {
                        app.get("$mainUrl$link", timeout = 100L).document.selectFirst("a.btn-success")?.attr("href") ?: return@amap
                    }

                    callback(
                        newExtractorLink(
                            "GDFlix[Fast Cloud]",
                            "GDFLix[Fast Cloud] - $fileName",
                            url = videoUrl
                        ) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("DIRECT DL") -> {
                    callback(
                        newExtractorLink(
                            "GDFlix[Direct]",
                            "GDFLix[Direct] - $fileName",
                            url = link
                        ) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("Index Links") -> {
                    val indexDoc = app.get("$mainUrl$link").document
                    indexDoc.select("a.btn.btn-outline-info").amap { btn ->
                        val serverUrl = mainUrl + btn.attr("href")
                        app.get(serverUrl).document.select("div.mb-4 > a").amap { a ->
                            callback(
                                newExtractorLink(
                                    "GDFlix[Index]",
                                    "GDFLix[Index] - $fileName",
                                    url = a.attr("href")
                                ) {
                                    this.quality = getIndexQuality(fileName)
                                }
                            )
                        }
                    }
                }

                text.contains("DRIVEBOT LINK") -> {
                    val id = link.substringAfter("id=").substringBefore("&")
                    val doId = link.substringAfter("do=").substringBefore("==")
                    val baseUrls = listOf("https://drivebot.sbs", "https://drivebot.cfd")

                    baseUrls.amap { baseUrl ->
                        val indexbotLink = "$baseUrl/download?id=$id&do=$doId"
                        val initialRes = app.get(indexbotLink, timeout = 100L)
                        if (initialRes.isSuccessful) {
                            val token = Regex("""formData\.append\('token', '([a-f0-9]+)'\)""")
                                .find(initialRes.body.string())?.groupValues?.get(1) ?: return@amap
                            val postId = Regex("""fetch\('/download\?id=([a-zA-Z0-9/+]+)'""")
                                .find(initialRes.body.string())?.groupValues?.get(1) ?: return@amap

                            val requestBody = FormBody.Builder().add("token", token).build()
                            val response = app.post(
                                "$baseUrl/download?id=$postId",
                                requestBody = requestBody,
                                headers = mapOf("Referer" to indexbotLink),
                                cookies = mapOf("PHPSESSID" to (initialRes.cookies["PHPSESSID"] ?: "")),
                                timeout = 100L
                            ).text

                            var downloadLink = Regex("url\":\"(.*?)\"").find(response)?.groupValues?.get(1) ?: ""
                            downloadLink = downloadLink.replace("\\", "")

                            callback(
                                newExtractorLink(
                                    "GDFlix[DriveBot]",
                                    "GDFlix[DriveBot] - $fileName",
                                    url = downloadLink
                                ) {
                                    this.referer = baseUrl
                                    this.quality = getIndexQuality(fileName)
                                }
                            )
                        }
                    }
                }

                text.contains("Instant DL") -> {
                    val finalLink = app.get(link, timeout = 30L, allowRedirects = false).headers["Location"]
                        ?.substringAfter("url=") ?: return@amap
                    callback(
                        newExtractorLink(
                            "GDFlix[Instant Download]",
                            "GDFlix[Instant Download] - $fileName",
                            url = finalLink
                        ) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("CLOUD DOWNLOAD [FSL]") -> {
                    val fslLink = link.substringAfter("url=")
                    callback(
                        newExtractorLink(
                            "GDFlix[FSL]",
                            "GDFlix[FSL] - $fileName",
                            url = fslLink
                        ) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                else -> Log.d("GDFlix", "Unknown server type for link: $text")
            }
        }
    }
}