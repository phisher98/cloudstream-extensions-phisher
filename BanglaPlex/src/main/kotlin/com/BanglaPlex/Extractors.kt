package com.BanglaPlex

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
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
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
        val res = app.get(url).toString()
        val encodedString =
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1) ?:""
        Log.d("Phisher",encodedString)
        val decoded = decodeEncryptedData(encodedString) ?:""
        val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decoded)?.groupValues?.get(1)
            ?.trim()
            ?:""
        val header =mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.5",
            "Origin" to mainUrl,
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "user-agent" to USER_AGENT,)
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

        val subtitles = extractSrtSubtitles(decoded)
        subtitles.forEachIndexed { _, (language, url) ->
            subtitleCallback.invoke(
                SubtitleFile(
                    language,
                    url
                )
            )
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """\[([^]]+)](https?://[^\s,]+\.srt)""".toRegex()

        return regex.findAll(subtitle).map { match ->
            val (language, url) = match.destructured
            language.trim() to url.trim()
        }.toList()
    }


    private fun decodeEncryptedData(encryptedString: String): String {
        val decodedData = base64Decode(encryptedString)
        val parsedJson = JSONObject(decodedData)
        val salt = stringTo32BitWords(parsedJson.getString("salt"))
        val password = stringTo32BitWords("3%.tjS0K@K9{9rTc")
        val derivedKey = deriveKey(password, salt, keySize = 32, iterations = 999, hashAlgo = "SHA-512")

        val iv = base64DecodeArray(parsedJson.getString("iv"))
        val encryptedContent = base64DecodeArray(parsedJson.getString("data"))

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(derivedKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        val decryptedData = cipher.doFinal(encryptedContent)

        val finalResult = String(decryptedData) // Simplified for demonstration
        return finalResult

    }

    private fun stringTo32BitWords(text: String): IntArray {
        val words = IntArray((text.length + 3) / 4)
        for (i in text.indices) {
            words[i shr 2] = words[i shr 2] or (text[i].code and 255 shl (24 - (i % 4) * 8))
        }
        return words
    }

    private fun deriveKey(password: IntArray, salt: IntArray, keySize: Int, iterations: Int, hashAlgo: String): ByteArray {
        val passwordBytes = password.flatMap { it.toByteArray() }.toByteArray()
        val saltBytes = salt.flatMap { it.toByteArray() }.toByteArray()

        // Use PBKDF2 with SHA-512 as the hash algorithm
        val keySpec = PBEKeySpec(
            passwordBytes.map { it.toInt().toChar() }.toCharArray(), // Convert password to CharArray
            saltBytes,
            iterations,
            keySize * 8 // The size in bits
        )
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val derivedKey = secretKeyFactory.generateSecret(keySpec).encoded

        return derivedKey
    }

    private fun Int.toByteArray(): List<Byte> {
        return listOf(
            (this shr 24 and 0xFF).toByte(),
            (this shr 16 and 0xFF).toByte(),
            (this shr 8 and 0xFF).toByte(),
            (this and 0xFF).toByte()
        )
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