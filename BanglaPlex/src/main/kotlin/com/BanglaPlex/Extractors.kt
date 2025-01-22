package com.BanglaPlex

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Vtbe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.FormBody
import java.util.zip.Inflater


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
    private var key: String? = null

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).toString()
        val encodedString =
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1) ?:""
        android.util.Log.d("Phisher",encodedString)
        val decoded = decodeEncryptedData(encodedString)
        val m3u8 =Regex("file:\\s*\"(.*?)\"").find(decoded ?:"")?.groupValues?.get(1) ?:""
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
            ExtractorLink(
                name,
                name,
                m3u8,
                mainUrl,
                Qualities.P1080.value,
                INFER_TYPE,
                headers = header
            )
        )

        val subtitles = extractSrtSubtitles(decoded ?:"")
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


    private fun decodeEncryptedData(encryptedString: String?): String? {
        if (encryptedString == null) return null

        return try {
            val decodedBytes = android.util.Base64.decode(encryptedString, android.util.Base64.DEFAULT)

            val decodedCharacters = decodedBytes.map { byte ->
                val binaryRepresentation = byte.toUByte().toString(2).padStart(8, '0')
                val reversedBinary = binaryRepresentation.reversed()
                reversedBinary.toInt(2).toByte()
            }
            val byteArray = ByteArray(decodedCharacters.size) { decodedCharacters[it] }
            val decompressedData = Inflater().run {
                setInput(byteArray)
                val output = ByteArray(1024 * 4)
                val decompressedSize = inflate(output)
                output.copyOf(decompressedSize).toString(Charsets.UTF_8)
            }
            val specialToAlphabetMap = mapOf(
                '!' to 'a', '@' to 'b', '#' to 'c', '$' to 'd', '%' to 'e',
                '^' to 'f', '&' to 'g', '*' to 'h', '(' to 'i', ')' to 'j'
            )
            val processedData = decompressedData.map { char ->
                specialToAlphabetMap[char] ?: char
            }.joinToString("")
            val finalDecodedData = android.util.Base64.decode(processedData, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
            android.util.Log.d("Phisher",finalDecodedData)
            finalDecodedData
        } catch (e: Exception) {
            println("Error decoding string: ${e.message}")
            null
        }
    }
}


open class GDFlix : ExtractorApi() {
    override val name: String = "GDFlix"
    override val mainUrl: String = "https://new.gdflix.dad"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
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
            val text = it.select("a").text()
            if (
                text.contains("FAST CLOUD") &&
                !text.contains("ZIP")
            )
            {
                val link=it.attr("href")
                if(link.contains("mkv") || link.contains("mp4")) {
                    callback.invoke(
                        ExtractorLink(
                            "GDFlix[Fast Cloud]",
                            "GDFLix[Fast Cloud] - $fileName",
                            link,
                            "",
                            getIndexQuality(fileName),
                        )
                    )
                }
                else {
                    val trueurl=app.get("https://new.gdflix.dad$link", timeout = 100L).document.selectFirst("a.btn-success")?.attr("href") ?:""
                    callback.invoke(
                        ExtractorLink(
                            "GDFlix[Fast Cloud]",
                            "GDFLix[Fast Cloud] - $fileName",
                            trueurl,
                            "",
                            getIndexQuality(fileName)
                        )
                    )
                }
            }
            else if(text.contains("DIRECT DL")) {
                val link = it.attr("href")
                callback.invoke(
                    ExtractorLink(
                        "GDFlix[Direct]",
                        "GDFLix[Direct] - $fileName",
                        link,
                        "",
                        getIndexQuality(fileName),
                    )
                )
            }
            else if(text.contains("Index Links")) {
                val link = it.attr("href")
                val doc = app.get("https://new.gdflix.dad$link").document
                doc.select("a.btn.btn-outline-info").amap {
                    val serverUrl = mainUrl + it.attr("href")
                    app.get(serverUrl).document.select("div.mb-4 > a").amap {
                        val source = it.attr("href")
                        callback.invoke(
                            ExtractorLink(
                                "GDFlix[Index]",
                                "GDFLix[Index] - $fileName",
                                source,
                                "",
                                getIndexQuality(fileName),
                            )
                        )
                    }
                }
            }
            else if (text.contains("DRIVEBOT LINK"))
            {
                val driveLink = it.attr("href")
                val id = driveLink.substringAfter("id=").substringBefore("&")
                val doId = driveLink.substringAfter("do=").substringBefore("==")
                val baseUrls = listOf("https://drivebot.sbs", "https://drivebot.cfd")
                baseUrls.amap { baseUrl ->
                    val indexbotlink = "$baseUrl/download?id=$id&do=$doId"
                    val indexbotresponse = app.get(indexbotlink, timeout = 100L)
                    if(indexbotresponse.isSuccessful) {
                        val cookiesSSID = indexbotresponse.cookies["PHPSESSID"]
                        val indexbotDoc = indexbotresponse.document
                        val token = Regex("""formData\.append\('token', '([a-f0-9]+)'\)""").find(indexbotDoc.toString()) ?. groupValues ?. get(1) ?: ""
                        val postId = Regex("""fetch\('/download\?id=([a-zA-Z0-9/+]+)'""").find(indexbotDoc.toString()) ?. groupValues ?. get(1) ?: ""

                        val requestBody = FormBody.Builder()
                            .add("token", token)
                            .build()

                        val headers = mapOf(
                            "Referer" to indexbotlink
                        )

                        val cookies = mapOf(
                            "PHPSESSID" to "$cookiesSSID",
                        )

                        val response = app.post(
                            "$baseUrl/download?id=${postId}",
                            requestBody = requestBody,
                            headers = headers,
                            cookies = cookies,
                            timeout = 100L
                        ).toString()

                        var downloadlink = Regex("url\":\"(.*?)\"").find(response) ?. groupValues ?. get(1) ?: ""

                        downloadlink = downloadlink.replace("\\", "")

                        callback.invoke(
                            ExtractorLink(
                                "GDFlix[DriveBot]",
                                "GDFlix[DriveBot] - $fileName",
                                downloadlink,
                                baseUrl,
                                getIndexQuality(fileName)
                            )
                        )
                    }
                }
            }
            else if (text.contains("Instant DL"))
            {
                val instantLink = it.attr("href")
                val link = app.get(instantLink, timeout = 30L, allowRedirects = false).headers["Location"]?.split("url=") ?. getOrNull(1) ?: ""
                callback.invoke(
                    ExtractorLink(
                        "GDFlix[Instant Download]",
                        "GDFlix[Instant Download] - $fileName",
                        link,
                        "",
                        getIndexQuality(fileName)
                    )
                )
            }
            else if(text.contains("CLOUD DOWNLOAD [FSL]")) {
                val link = it.attr("href").substringAfter("url=")
                callback.invoke(
                    ExtractorLink(
                        "GDFlix[FSL]",
                        "GDFlix[FSL] - $fileName",
                        link,
                        "",
                        getIndexQuality(fileName)
                    )
                )
            }
            else {
                Log.d("Error", "No Server matched")
            }
        }
    }
}
