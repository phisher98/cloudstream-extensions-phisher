package com.phisher98

import com.phisher98.StreamPlay.Companion.animepaheAPI
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.GMPlayer
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


open class Playm4u : ExtractorApi() {
    override val name = "Playm4u"
    override val mainUrl = "https://play9str.playm4u.xyz"
    override val requiresReferer = true
    private val password = "plhq@@@22"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val script = document.selectFirst("script:containsData(idfile =)")?.data() ?: return
        val passScript = document.selectFirst("script:containsData(domain_ref =)")?.data() ?: return

        val pass = passScript.substringAfter("CryptoJS.MD5('").substringBefore("')")
        val amount = passScript.substringAfter(".toString()), ").substringBefore("));").toInt()

        val idFile = "idfile".findIn(script)
        val idUser = "idUser".findIn(script)
        val domainApi = "DOMAIN_API".findIn(script)
        val nameKeyV3 = "NameKeyV3".findIn(script)
        val dataEnc = caesarShift(
            mahoa(
                "Win32|$idUser|$idFile|$referer",
                md5(pass)
            ), amount
        ).toHex()

        val captchaKey =
            document.select("script[src*=https://www.google.com/recaptcha/api.js?render=]")
                .attr("src").substringAfter("render=")
        val token = getCaptchaToken(
            url,
            captchaKey,
            referer = referer
        )

        val source = app.post(
            domainApi, data = mapOf(
                "namekey" to nameKeyV3,
                "token" to "$token",
                "referrer" to "$referer",
                "data" to "$dataEnc|${md5(dataEnc + password)}",
            ), referer = "$mainUrl/"
        ).parsedSafe<Source>()

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = source?.data ?: return,
                INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )

        subtitleCallback.invoke(
            SubtitleFile(
                source.sub?.substringBefore("|")?.toLanguage() ?: return,
                source.sub.substringAfter("|"),
            )
        )

    }

    private fun caesarShift(str: String, amount: Int): String {
        var output = ""
        val adjustedAmount = if (amount < 0) amount + 26 else amount
        for (element in str) {
            var c = element
            if (c.isLetter()) {
                val code = c.code
                c = when (code) {
                    in 65..90 -> ((code - 65 + adjustedAmount) % 26 + 65).toChar()
                    in 97..122 -> ((code - 97 + adjustedAmount) % 26 + 97).toChar()
                    else -> c
                }
            }
            output += c
        }
        return output
    }

    private fun mahoa(input: String, key: String): String {
        val a = CryptoJS.encrypt(key, input)
        return a.replace("U2FsdGVkX1", "")
            .replace("/", "|a")
            .replace("+", "|b")
            .replace("=", "|c")
            .replace("|", "-z")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    private fun String.toHex(): String {
        return this.toByteArray().joinToString("") { "%02x".format(it) }
    }

    private fun String.findIn(data: String): String {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1) ?: ""
    }

    private fun String.toLanguage(): String {
        return if (this == "EN") "English" else this
    }

    data class Source(
        @JsonProperty("data") val data: String? = null,
        @JsonProperty("sub") val sub: String? = null,
    )

}

open class M4ufree : ExtractorApi() {
    override val name = "M4ufree"
    override val mainUrl = "https://play.playm4u.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = session.get(url, referer = referer).document
        val script = document.selectFirst("script:containsData(idfile =)")?.data() ?: return

        val idFile = "idfile".findIn(script)
        val idUser = "idUser".findIn(script)

        val video = session.post(
            "https://api-plhq.playm4u.xyz/apidatard/$idUser/$idFile",
            data = mapOf("referrer" to "$referer"),
            headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
            )
        ).text.let { AppUtils.tryParseJson<Source>(it) }?.data

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = video ?: return,
                INFER_TYPE
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P720.value
            }
        )

    }

    private fun String.findIn(data: String): String? {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1)
    }

    data class Source(
        @JsonProperty("data") val data: String? = null,
    )

}

class VCloudGDirect : ExtractorApi() {
    override val name: String = "V-Cloud GD"
    override val mainUrl: String = "https://fastdl.icu"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val source = app.get(url).document.selectFirst("#vd")?.attr("href") ?: ""
        if (source.isBlank()) {
            Log.e("Error:", "Failed to extract video link from $url")
            //loadExtractor(url, subtitleCallback, callback) // Passes original URL, not an empty string
            return
        }
        callback.invoke(
            newExtractorLink(
                "V-Cloud GD 10 Gbps",
                "V-Cloud GD 10 Gbps",
                url = source
            ) {
                this.quality = getQualityFromName(source)
            }
        )
    }
}

    
class VCloud : ExtractorApi() {
    override val name: String = "V-Cloud"
    override val mainUrl: String = "https://vcloud.lol"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var href=url
        if (href.contains("api/index.php"))
        {
            href=app.get(url).document.selectFirst("div.main h4 a")?.attr("href") ?:""
        }
        val doc = app.get(href).document
        val scriptTag = doc.selectFirst("script:containsData(url)")?.toString() ?:""
        val urlValue = Regex("var url = '([^']*)'").find(scriptTag) ?. groupValues ?. get(1) ?: ""
        if (urlValue.isNotEmpty()) {
            val document = app.get(urlValue).document
            val size = document.selectFirst("i#size")?.text() ?: ""
            val div = document.selectFirst("div.card-body")
            val header = document.selectFirst("div.card-header")?.text() ?: ""
            val headerdetails =
                """\.\d{3,4}p\.(.*)-[^-]*${'$'}""".toRegex().find(header)?.groupValues?.get(1)
                    ?.trim() ?: ""
            div?.select("h2 a.btn")?.filterNot {it.text().contains("Telegram", ignoreCase = true)}
                ?.amap {
                    val link = it.attr("href")
                    if (link.contains("technorozen.workers.dev")) {
                        @Suppress("NAME_SHADOWING") val href = app.get(link).document.selectFirst("#vd")?.attr("href") ?: ""
                        callback.invoke(
                            newExtractorLink(
                                "V-Cloud 10 Gbps $headerdetails",
                                "V-Cloud 10 Gbps $size",
                                url = href
                            ) {
                                this.quality = getIndexQuality(header)
                            }
                        )
                    } else
                        if (link.contains("pixeldra")) {
                            callback.invoke(
                                newExtractorLink(
                                    "Pixeldrain $headerdetails",
                                    "Pixeldrain $size",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(header)
                                }
                            )
                        } else if (link.contains("dl.php")) {
                            val response = app.get(link, allowRedirects = false)
                            val downloadLink =
                                response.headers["location"].toString().split("link=").getOrNull(1)
                                    ?: link
                            callback.invoke(
                                newExtractorLink(
                                    "V-Cloud[Download] $headerdetails",
                                    "V-Cloud[Download] $size",
                                    url = downloadLink
                                ) {
                                    this.quality = getIndexQuality(header)
                                }
                            )
                        } else if (link.contains(".dev")) {
                            callback.invoke(
                                newExtractorLink(
                                    "V-Cloud $headerdetails",
                                    "V-Cloud $size",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(header)
                                }
                            )
                        } else if (link.contains(".hubcdn.xyz")) {
                            callback.invoke(
                                newExtractorLink(
                                    "V-Cloud $headerdetails",
                                    "V-Cloud $size",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(header)
                                }
                            )
                        } else if (link.contains(".lol")) {
                            callback.invoke(
                                newExtractorLink(
                                    "V-Cloud [FSL] $headerdetails",
                                    "V-Cloud $size",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(header)
                                }
                            )
                        } else {
                            loadExtractor(link, subtitleCallback, callback)
                        }
                }
        }
    }


    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

}

open class Streamruby : ExtractorApi() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = "/e/(\\w+)".toRegex().find(url)?.groupValues?.get(1) ?: return
        val response = app.post(
            "$mainUrl/dl", data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "",
            ), referer = referer
        )
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}

open class Uploadever : ExtractorApi() {
    override val name = "Uploadever"
    override val mainUrl = "https://uploadever.in"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var res = app.get(url, referer = referer).document
        val formUrl = res.select("form").attr("action")
        var formData = res.select("form input").associate { it.attr("name") to it.attr("value") }
            .filterKeys { it != "go" }
            .toMutableMap()
        val formReq = app.post(formUrl, data = formData)

        res = formReq.document
        val captchaKey =
            res.select("script[src*=https://www.google.com/recaptcha/api.js?render=]").attr("src")
                .substringAfter("render=")
        val token = getCaptchaToken(url, captchaKey, referer = "$mainUrl/")
        formData = res.select("form#down input").associate { it.attr("name") to it.attr("value") }
            .toMutableMap()
        formData["adblock_detected"] = "0"
        formData["referer"] = url
        res = app.post(
            formReq.url,
            data = formData + mapOf("g-recaptcha-response" to "$token"),
            cookies = formReq.cookies
        ).document
        val video = res.select("div.download-button a.btn.btn-dow.recaptchav2").attr("href")

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = video,
                INFER_TYPE
            ) {
                this.quality = Qualities.Unknown.value
            }
        )
    }

}

open class Netembed : ExtractorApi() {
    override var name: String = "Netembed"
    override var mainUrl: String = "https://play.netembed.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = getAndUnpack(response.text)
        val m3u8 = Regex("((https:|http:)//.*\\.m3u8)").find(script)?.groupValues?.getOrNull(1) ?: return

        M3u8Helper.generateM3u8(this.name, m3u8, "$mainUrl/").forEach(callback)
    }
}

open class Ridoo : ExtractorApi() {
    override val name = "Ridoo"
    override var mainUrl = "https://ridoo.net"
    override val requiresReferer = true
    open val defaulQuality = Qualities.P1080.value

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        val quality = "qualityLabels.*\"(\\d{3,4})[pP]\"".toRegex().find(script)?.groupValues?.get(1)
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = m3u8 ?: return,
                INFER_TYPE
            ) {
                this.referer = mainUrl
                this.quality = quality?.toIntOrNull() ?: defaulQuality
            }
        )
    }

}

open class Streamvid : ExtractorApi() {
    override val name = "Streamvid"
    override val mainUrl = "https://streamvid.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 =
            Regex("src:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}

open class Embedrise : ExtractorApi() {
    override val name = "Embedrise"
    override val mainUrl = "https://embedrise.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val title = res.select("title").text()
        val video = res.select("video#player source").attr("src")

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = video,
                INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
                this.quality = getIndexQuality(title)
            }
        )
    }

}

class FilemoonNl : Ridoo() {
    override val name = "FilemoonNl"
    override var mainUrl = "https://filemoon.nl"
    override val defaulQuality = Qualities.Unknown.value
}

class AllinoneDownloader : Filesim() {
    override var name = "MultiMovies API"
    override var mainUrl = "https://allinonedownloader.fun"
}

class Alions : Ridoo() {
    override val name = "Alions"
    override var mainUrl = "https://alions.pro"
    override val defaulQuality = Qualities.Unknown.value
}

class UqloadsXyz : Filesim() {
    override val name = "Uqloads"
    override var mainUrl = "https://uqloads.xyz"
}

class Pixeldra : PixelDrain() {
    override val mainUrl = "https://pixeldra.in"
}

class Snolaxstream : Filesim() {
    override val mainUrl = "https://snolaxstream.online"
    override val name = "Snolaxstream"
}

class bulbasaur : Filesim() {
    override val mainUrl = "https://file-mi11ljwj-embed.com"
    override val name = "Filemoon"
}
class do0od : DoodLaExtractor() {
    override var mainUrl = "https://do0od.com"
}

class doodre : DoodLaExtractor() {
    override var mainUrl = "https://dood.re"
}

class TravelR : GMPlayer() {
    override val name = "TravelR"
    override val mainUrl = "https://travel-russia.xyz"
}

class Mwish : Filesim() {
    override val name = "Mwish"
    override var mainUrl = "https://mwish.pro"
}

class Animefever : Filesim() {
    override val name = "Animefever"
    override var mainUrl = "https://animefever.fun"
}

class Multimovies : Ridoo() {
    override val name = "Multimovies"
    override var mainUrl = "https://multimovies.cloud"
}

class MultimoviesSB : StreamSB() {
    override var name = "Multimovies"
    override var mainUrl = "https://multimovies.website"
}

class Yipsu : Voe() {
    override val name = "Yipsu"
    override var mainUrl = "https://yip.su"
}

class Filelions : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://alions.pro"
    override val requiresReferer = false
}


class Embedwish : Filesim() {
    override val name = "Embedwish"
    override var mainUrl = "https://embedwish.com"
}

class dwish : StreamWishExtractor() {
    override var mainUrl = "https://dwish.pro"
}

class dlions : VidhideExtractor() {
    override var name = "Dlions"
    override var mainUrl = "https://dlions.pro"
}

class Animezia : VidhideExtractor() {
    override var name = "MultiMovies API"
    override var mainUrl = "https://animezia.cloud"
}

class MixDropSi : MixDrop(){
    override var mainUrl = "https://mixdrop.si"
}

class MixDropPs : MixDrop(){
    override var mainUrl = "https://mixdrop.ps"
}

class Servertwo : VidhideExtractor() {
    override var name = "MultiMovies Vidhide"
    override var mainUrl = "https://server2.shop"
}

class Filelion : Filesim() {
    override val name = "Filelion"
    override var mainUrl = "https://filelions.to"
}

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
}

class Rapidplayers: StreamWishExtractor() {
    override var mainUrl = "https://rapidplayers.com"
}

class Flaswish : Ridoo() {
    override val name = "Flaswish"
    override var mainUrl = "https://flaswish.com"
    override val defaulQuality = Qualities.Unknown.value
}

class Comedyshow : Jeniusplay() {
    override val mainUrl = "https://comedyshow.to"
    override val name = "Comedyshow"
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
        val headers = mapOf(
            "priority" to "u=0, i",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        try {
            val res = app.get(url,referer=mainUrl,headers=headers).toString()

            val encodedString = Regex("(?:const|let|var|window\\.(?:Delta|Alpha|Ebolt|Flagon))\\s+\\w*\\s*=\\s*'(.*?)'").find(res)?.groupValues?.get(1) ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Decrypt the encoded string
            val keyBase64 = "SCkjX0Y9Vy5tY1FNIyZtdg=="
            val decryptedData = decryptData(keyBase64, encodedString)
            // Extract the m3u8 URL from decrypted data
            val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decryptedData)?.groupValues?.get(1)?.trim() ?: ""
            if (m3u8.isEmpty()) {
                throw Exception("m3u8 URL not found")
            }

            // Prepare headers
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
            println("Error: ${e.message}")
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """tracks:\s*\[(.*?)]""".toRegex()
        val match = regex.find(subtitle)?.groupValues?.get(1) ?: return emptyList()

        return try {
            val subtitles = JSONArray("[$match]") // Wrap in brackets to form valid JSON
            (0 until subtitles.length()).mapNotNull { i ->
                val obj = subtitles.optJSONObject(i) ?: return@mapNotNull null
                val kind = obj.optString("kind")
                if (kind == "captions") {
                    val label = obj.optString("label")
                    val file = obj.optString("file")
                    label to file
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decryptData(base64Key: String, encryptedData: String): String {
        return try {
            // Decode Base64-encoded encrypted data
            val decodedBytes = base64DecodeArray(encryptedData)

            // Extract IV, Authentication Tag, and Ciphertext
            val salt=decodedBytes.copyOfRange(0, 16)
            val iv = decodedBytes.copyOfRange(16, 28)
            val authTag = decodedBytes.copyOfRange(28, 44)
            val ciphertext = decodedBytes.copyOfRange(44, decodedBytes.size)

            // Convert Base64-encoded password to a SHA-256 encryption key
            val password = base64Decode(base64Key)
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(
                PBEKeySpec(password.toCharArray(), salt, 999, 32 * 8)
            ).encoded

            // Decrypt the data using AES-GCM
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Perform decryption
            val decryptedBytes = cipher.doFinal(ciphertext + authTag)
            String(decryptedBytes, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            "Decryption failed"
        }
    }

    /** End **/
}



class Bestx : Chillx() {
    override val name = "Bestx"
    override val mainUrl = "https://bestx.stream"
    override val requiresReferer = true
}

class Vectorx : Chillx() {
    override val name = "Vectorx"
    override val mainUrl = "https://vectorx.top"
    override val requiresReferer = true
}

class Boosterx : Chillx() {
    override val name = "Vectorx"
    override val mainUrl = "https://boosterx.stream"
    override val requiresReferer = true
}

class Graceaddresscommunity : Voe() {
    override var mainUrl = "https://graceaddresscommunity.com"
}

class Sethniceletter : Voe() {
    override var mainUrl = "https://sethniceletter.com"
}

class Maxfinishseveral : Voe() {
    override var mainUrl = "https://maxfinishseveral.com"
}


class Tellygossips : ExtractorApi() {
    override val mainUrl = "https://flow.tellygossips.net"
    override val name = "Tellygossips"
    override val requiresReferer = false
    private val referer = "http://tellygossips.net/"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = this.referer).text
        val link = doc.substringAfter("src\":\"").substringBefore("\",")
        callback(
            newExtractorLink(
                name,
                name,
                url = link,
                INFER_TYPE
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

class Tvlogy : ExtractorApi() {
    override val mainUrl = "https://hls.tvlogy.to"
    override val name = "Tvlogy"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("data=")
        val data = mapOf(
            "hash" to id,
            "r" to "http%3A%2F%2Ftellygossips.net%2F"
        )
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val meta = app.post("$url&do=getVideo", headers = headers, referer = url, data = data)
            .parsedSafe<MetaData>() ?: return
        callback(
            newExtractorLink(
                name,
                name,
                url = meta.videoSource,
                ExtractorLinkType.M3U8
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }

    data class MetaData(
        val hls: Boolean,
        val videoSource: String
    )

}

open class Modflix : ExtractorApi() {
    override val name: String = "Modflix"
    override val mainUrl: String = "https://video-seed.xyz"
    override val requiresReferer = true

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun getUrl(
        finallink: String,
        quality: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val token = finallink.substringAfter("https://video-seed.xyz/?url=")
        val downloadlink = app.post(
            url = "https://video-seed.xyz/api",
            data = mapOf(
                "keys" to token
            ),
            referer = finallink,
            headers = mapOf(
                "x-token" to "video-seed.xyz",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            )
        )
        val finaldownloadlink =
            downloadlink.toString().substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
        val link = finaldownloadlink
        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = link,
            ) {
                this.quality = getQualityFromName(quality)
            }
        )
    }
}

class furher : Filesim() {
    override val name: String = "AZSeries"
    override var mainUrl = "https://furher.in"
}
class fastdlserver : GDFlix() {
    override var mainUrl = "https://fastdlserver.online"
}

class GDFlix1 : GDFlix() {
    override val mainUrl: String = "https://new3.gdflix.cfd"
}

class GDFlix2 : GDFlix() {
    override val mainUrl: String = "https://new2.gdflix.cfd"
}


open class GDFlix : ExtractorApi() {
    override val name: String = "GDFlix"
    override val mainUrl: String = "https://new1.gdflix.dad"
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
            val text = it.select("a").text()
            val link = it.attr("href")

            if (text.contains("FAST CLOUD") && !text.contains("ZIP")) {
                if (link.contains("mkv") || link.contains("mp4")) {
                    callback(
                        newExtractorLink(
                            "GDFlix[Fast Cloud]",
                            "GDFLix[Fast Cloud] - $fileName",
                            url = link
                        ) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                } else {
                    val trueurl = app.get("https://new1.gdflix.dad$link", timeout = 100L)
                        .document.selectFirst("a.btn-success")?.attr("href") ?: ""
                    callback(
                        newExtractorLink(
                            "GDFlix[Fast Cloud]",
                            "GDFLix[Fast Cloud] - $fileName",
                            url = trueurl
                        ) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }
            } else if (text.contains("DIRECT DL")) {
                callback(
                    newExtractorLink(
                        "GDFlix[Direct]",
                        "GDFLix[Direct] - $fileName",
                        url = link
                    ) {
                        this.quality = getIndexQuality(fileName)
                    }
                )
            } else if (text.contains("Index Links")) {
                val doc = app.get("https://new1.gdflix.dad$link").document
                doc.select("a.btn.btn-outline-info").amap {
                    val serverUrl = mainUrl + it.attr("href")
                    app.get(serverUrl).document.select("div.mb-4 > a").amap {
                        val source = it.attr("href")
                        callback(
                            newExtractorLink(
                                "GDFlix[Index]",
                                "GDFLix[Index] - $fileName",
                                url = source
                            ) {
                                this.quality = getIndexQuality(fileName)
                            }
                        )
                    }
                }
            } else if (text.contains("DRIVEBOT LINK")) {
                val driveLink = it.attr("href")
                val id = driveLink.substringAfter("id=").substringBefore("&")
                val doId = driveLink.substringAfter("do=").substringBefore("==")
                val baseUrls = listOf("https://drivebot.sbs", "https://drivebot.cfd")
                baseUrls.amap { baseUrl ->
                    val indexbotlink = "$baseUrl/download?id=$id&do=$doId"
                    val indexbotresponse = app.get(indexbotlink, timeout = 100L)
                    if (indexbotresponse.isSuccessful) {
                        val cookiesSSID = indexbotresponse.cookies["PHPSESSID"]
                        val indexbotDoc = indexbotresponse.document
                        val token = Regex("""formData\\.append\('token', '([a-f0-9]+)'\)""").find(indexbotDoc.toString())?.groupValues?.get(1) ?: ""
                        val postId = Regex("""fetch\('/download\\?id=([a-zA-Z0-9/+]+)'""").find(indexbotDoc.toString())?.groupValues?.get(1) ?: ""

                        val requestBody = FormBody.Builder()
                            .add("token", token)
                            .build()

                        val headers = mapOf("Referer" to indexbotlink)
                        val cookies = mapOf("PHPSESSID" to "$cookiesSSID")

                        val response = app.post(
                            "$baseUrl/download?id=${postId}",
                            requestBody = requestBody,
                            headers = headers,
                            cookies = cookies,
                            timeout = 100L
                        ).toString()

                        var downloadlink = Regex("\"url\":\"(.*?)\"").find(response)?.groupValues?.get(1) ?: ""
                        downloadlink = downloadlink.replace("\\", "")

                        callback(
                            newExtractorLink(
                                "GDFlix[DriveBot]",
                                "GDFlix[DriveBot] - $fileName",
                                url = downloadlink
                            ) {
                                this.referer = baseUrl
                                this.quality = getIndexQuality(fileName)
                            }
                        )
                    }
                }
            } else if (text.contains("Instant DL")) {
                val instantLink = it.attr("href")
                val link = app.get(instantLink, timeout = 30L, allowRedirects = false).headers["Location"]?.split("url=")?.getOrNull(1) ?: ""
                callback(
                    newExtractorLink(
                        "GDFlix[Instant Download]",
                        "GDFlix[Instant Download] - $fileName",
                        url = link
                    ) {
                        this.quality = getIndexQuality(fileName)
                    }
                )
            } else if (text.contains("CLOUD DOWNLOAD [FSL]")) {
                val link = it.attr("href").substringAfter("url=")
                callback(
                    newExtractorLink(
                        "GDFlix[FSL]",
                        "GDFlix[FSL] - $fileName",
                        url = link
                    ) {
                        this.quality = getIndexQuality(fileName)
                    }
                )
            } else {
                Log.d("Error", "No Server matched")
            }
        }
    }
}

class HubCloudClub : HubCloud() {
    override var mainUrl = "https://hubcloud.club"
}

class HubCloudink : HubCloud() {
    override var mainUrl = "https://hubcloud.ink"
}

class HubCloudtel : HubCloud() {
    override var mainUrl = "https://hubcloud.tel"
}

class HubCloudlol : HubCloud() {
    override var mainUrl = "https://hubcloud.lol"
}

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        if (mId.isNullOrEmpty())
        {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = url
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        } else {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = "$mainUrl/api/file/${mId}?download"
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class Moviesapi : Chillx() {
    override val name = "Moviesapi"
    override val mainUrl = "https://w1.moviesapi.club"
    override val requiresReferer = true
}

open class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.ink"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val realUrl = replaceHubclouddomain(url)
        val href = if (realUrl.contains("hubcloud.php")) {
            realUrl
        } else {
            val regex = "var url = '([^']*)'".toRegex()
            val regexdata=app.get(realUrl).document.selectFirst("script:containsData(url)")?.toString() ?: ""
            regex.find(regexdata)?.groupValues?.get(1).orEmpty()
        }
        if (href.isEmpty()) {
            Log.d("Error", "Not Found")
            return
        }

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text()
        val header = document.selectFirst("div.card-header")?.text()

        document.select("div.card-body a.btn").forEach { linkElement ->
            val link = linkElement.attr("href")
            val quality = getIndexQuality(header)

            when {
                link.contains("www-google-com") -> Log.d("Error:", "Not Found")

                link.contains("technorozen.workers.dev") -> {
                    callback(
                        newExtractorLink(
                            "$source 10GB Server",
                            "$source 10GB Server $size",
                            url = getGBurl(link)
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("pixeldra.in") || link.contains("pixeldrain") -> {
                    callback(
                        newExtractorLink(
                            "$source Pixeldrain",
                            "$source Pixeldrain $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("buzzheavier") -> {
                    callback(
                        newExtractorLink(
                            "$source Buzzheavier",
                            "$source Buzzheavier $size",
                            url = "$link/download"
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains(".dev") -> {
                    callback(
                        newExtractorLink(
                            "$source Hub-Cloud",
                            "$source Hub-Cloud $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("fastdl.lol") -> {
                    callback(
                        newExtractorLink(
                            "$source [FSL] Hub-Cloud",
                            "$source [FSL] Hub-Cloud $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("hubcdn.xyz") -> {
                    callback(
                        newExtractorLink(
                            "$source [File] Hub-Cloud",
                            "$source [File] Hub-Cloud $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("gofile.io") -> {
                    loadCustomExtractor(source.orEmpty(), link, "Pixeldrain", subtitleCallback, callback)
                }

                else -> Log.d("Error:", "No Server Match Found")
            }
        }
    }

    private fun getIndexQuality(str: String?) =
        Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P2160.value

    private suspend fun getGBurl(url: String): String =
        app.get(url).document.selectFirst("#vd")?.attr("href").orEmpty()
}


class Driveleech : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.org"
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
            Log.e("Error:", "Failed to fetch CFType1 links: ${it.message}")
            emptyList()
        }
    }

    private suspend fun resumeCloudLink(url: String): String? {
        val resumeCloudUrl = mainUrl + url
        return runCatching {
            app.get(resumeCloudUrl).document.selectFirst("a.btn-success")
                ?.attr("href")
                ?.takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Error:", "Failed to fetch ResumeCloud link: ${it.message}")
            null
        }
    }

    private suspend fun resumeBot(url: String): String? {
        return runCatching {
            val response = app.get(url)
            val docString = response.document.toString()
            val ssid = response.cookies["PHPSESSID"].orEmpty()
            val token = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(docString)
                ?.groups?.get(1)?.value.orEmpty()
            val path = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/+]+)'").find(docString)
                ?.groups?.get(1)?.value.orEmpty()
            val baseUrl = url.substringBefore("/download")

            if (token.isEmpty() || path.isEmpty()) return@runCatching null

            val jsonResponse = app.post(
                "$baseUrl/download?id=$path",
                requestBody = FormBody.Builder().addEncoded("token", token).build(),
                headers = mapOf("Accept" to "*/*", "Origin" to baseUrl, "Sec-Fetch-Site" to "same-origin"),
                cookies = mapOf("PHPSESSID" to ssid),
                referer = url
            ).text

            JSONObject(jsonResponse).getString("url").takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Error:", "Failed to fetch ResumeBot link: ${it.message}")
            null
        }
    }

    private suspend fun instantLink(finallink: String): String? {
        return runCatching {
            val url = if (finallink.contains("video-leech")) "video-leech.xyz" else "video-seed.xyz"
            val token = finallink.substringAfter("url=")
            val response = app.post(
                url = "https://$url/api",
                data = mapOf("keys" to token),
                referer = finallink,
                headers = mapOf("x-token" to url)
            ).text

            response.substringAfter("url\":\"").substringBefore("\",\"name")
                .replace("\\/", "/")
                .takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Error:", "Failed to fetch InstantLink: ${it.message}")
            null
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
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
            Log.e("Error:", "Failed to fetch page document: ${e.message}")
            return
        }
        val quality = document.selectFirst("li.list-group-item")?.text().orEmpty()
        val fileName = quality.replace("Name : ", "")

        document.select("div.text-center > a").forEach { element ->
            val text = element.text()
            val href = element.attr("href")

            if (href.isNotBlank()) {
                when {
                    text.contains("Instant Download") -> {
                        try {
                            val instant = instantLink(href)
                            if (instant!!.startsWith("http")) {
                                callback(
                                    newExtractorLink(
                                        "$name Instant(Download)",
                                        "$name Instant(Download) - $fileName",
                                        url = instant
                                    ) {
                                        this.quality = getIndexQuality(quality)
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("Error:", "Instant Download failed: ${e.message}")
                        }
                    }

                    text.contains("Resume Worker Bot") -> {
                        try {
                            val resumeLink = resumeBot(href)
                            if (resumeLink!!.startsWith("http")) {
                                callback(
                                    newExtractorLink(
                                        "$name ResumeBot(VLC)",
                                        "$name ResumeBot(VLC) - $fileName",
                                        url = resumeLink
                                    ) {
                                        this.quality = getIndexQuality(quality)
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("Error:", "Resume Worker Bot failed: ${e.message}")
                        }
                    }

                    text.contains("Direct Links") -> {
                        try {
                            val link = mainUrl + href
                            CFType1(link).forEach { directLink ->
                                if (directLink.startsWith("http")) {
                                    callback(
                                        newExtractorLink(
                                            "$name CF Type1",
                                            "$name CF Type1 - $fileName",
                                            url = directLink
                                        ) {
                                            this.quality = getIndexQuality(quality)
                                        }
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Error:", "CF Type1 failed: ${e.message}")
                        }
                    }

                    text.contains("Resume Cloud") -> {
                        try {
                            val resumeCloud = resumeCloudLink(href)
                            if (resumeCloud!!.startsWith("http")) {
                                callback(
                                    newExtractorLink(
                                        "$name ResumeCloud",
                                        "$name ResumeCloud - $fileName",
                                        url = resumeCloud
                                    ) {
                                        this.quality = getIndexQuality(quality)
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("Error:", "Resume Cloud failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}

class Kwik : ExtractorApi() {
    override val name            = "Kwik"
    override val mainUrl         = "https://kwik.si"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val res = app.get(url,referer=animepaheAPI)
        val script =
            res.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        val unpacked = getAndUnpack(script ?: return)
        val m3u8 =Regex("source=\\s*'(.*?m3u8.*?)'").find(unpacked)?.groupValues?.getOrNull(1) ?:""
        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = m3u8,
                type = INFER_TYPE
            ) {
                this.quality = getQualityFromName(referer)
            }
        )
    }
}


//Credit Thanks to https://github.com/SaurabhKaperwan/CSX/blob/7256fe183966412b2323beb15d03331009bfb80f/CineStream/src/main/kotlin/com/megix/Extractors.kt#L108
class Pahe : ExtractorApi() {
    override val name = "Pahe"
    override val mainUrl = "https://pahe.win"
    override val requiresReferer = true
    private val kwikParamsRegex = Regex("""\("(\w+)",\d+,"(\w+)",(\d+),(\d+),\d+\)""")
    private val kwikDUrl = Regex("action=\"([^\"]+)\"")
    private val kwikDToken = Regex("value=\"([^\"]+)\"")
    private val client = OkHttpClient()

    private fun decrypt(fullString: String, key: String, v1: Int, v2: Int): String {
        val keyIndexMap = key.withIndex().associate { it.value to it.index }
        val sb = StringBuilder()
        var i = 0
        val toFind = key[v2]

        while (i < fullString.length) {
            val nextIndex = fullString.indexOf(toFind, i)
            val decodedCharStr = buildString {
                for (j in i until nextIndex) {
                    append(keyIndexMap[fullString[j]] ?: -1)
                }
            }

            i = nextIndex + 1

            val decodedChar = (decodedCharStr.toInt(v2) - v1).toChar()
            sb.append(decodedChar)
        }

        return sb.toString()
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val noRedirects = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val initialRequest = Request.Builder()
            .url("$url/i")
            .get()
            .build()

        val kwikUrl = "https://" + noRedirects.newCall(initialRequest).execute()
            .header("location")!!.substringAfterLast("https://")

        val fContentRequest = Request.Builder()
            .url(kwikUrl)
            .header("referer", "https://kwik.cx/")
            .get()
            .build()

        val fContent = client.newCall(fContentRequest).execute()
        val fContentString = fContent.body.string()

        val (fullString, key, v1, v2) = kwikParamsRegex.find(fContentString)!!.destructured
        val decrypted = decrypt(fullString, key, v1.toInt(), v2.toInt())

        val uri = kwikDUrl.find(decrypted)!!.destructured.component1()
        val tok = kwikDToken.find(decrypted)!!.destructured.component1()

        val noRedirectClient = OkHttpClient().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(client.cookieJar)
            .build()

        var code = 419
        var tries = 0
        var content: Response? = null

        while (code != 302 && tries < 20) {
            val formBody = FormBody.Builder()
                .add("_token", tok)
                .build()

            val postRequest = Request.Builder()
                .url(uri)
                .header("user-agent", " Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .header("referer", fContent.request.url.toString())
                .header("cookie",  fContent.headers("set-cookie").firstOrNull().toString())
                .post(formBody)
                .build()

            content = noRedirectClient.newCall(postRequest).execute()
            code = content.code
            tries++
        }

        val location = content?.header("location").toString()
        content?.close()

        callback(
            newExtractorLink(
                name,
                name,
                url = location,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
    }
}


open class Embtaku : ExtractorApi() {
    override var name = "Embtaku"
    override var mainUrl = "https://embtaku.pro"
    override val requiresReferer = false

    override suspend fun
            getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val responsecode= app.get(url)
        val serverRes = responsecode.document
        serverRes.select("ul.list-server-items").amap {
            val href=it.attr("data-video")
            loadCustomExtractor("Anichi [Embtaku]",href,"",subtitleCallback,callback)
        }
    }
}

class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = getBaseUrl(app.get(url).url)
        val embed = url.substringAfterLast("/")
        val data = mapOf("sid" to embed)
        val jsonString = app.post("$host/embedhelper.php", data = data).toString()
        val jsonElement: JsonElement = JsonParser.parseString(jsonString)
        if (!jsonElement.isJsonObject) {
            Log.e("Error:", "Unexpected JSON format: Response is not a JSON object")
            return
        }
        val jsonObject = jsonElement.asJsonObject
        val siteUrls = jsonObject["siteUrls"]?.takeIf { it.isJsonObject }?.asJsonObject
        val mresultEncoded = jsonObject["mresult"]?.takeIf { it.isJsonPrimitive }?.asString
        val mresult = mresultEncoded?.let {
            val decodedString = base64Decode(it) // Decode from Base64
            JsonParser.parseString(decodedString).asJsonObject // Convert to JSON object
        }
        val siteFriendlyNames = jsonObject["siteFriendlyNames"]?.takeIf { it.isJsonObject }?.asJsonObject
        if (siteUrls == null || siteFriendlyNames == null || mresult == null) {
            return
        }
        val commonKeys = siteUrls.keySet().intersect(mresult.keySet())
        commonKeys.forEach { key ->
            val siteName = siteFriendlyNames[key]?.asString
            if (siteName == null) {
                Log.e("Error:", "Skipping key: $key because siteName is null")
                return@forEach
            }
            val siteUrl = siteUrls[key]?.asString
            val resultUrl = mresult[key]?.asString
            if (siteUrl == null || resultUrl == null) {
                Log.e("Error:", "Skipping key: $key because siteUrl or resultUrl is null")
                return@forEach
            }
            val href = siteUrl + resultUrl
            loadExtractor(href, subtitleCallback, callback)
        }

    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}


class OwlExtractor : ExtractorApi() {
    override var name = "OwlExtractor"
    override var mainUrl = "https://whguides.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val response = app.get(url).document
        val datasrc=response.select("button#hot-anime-tab").attr("data-source")
        val id=datasrc.substringAfterLast("/")
        val epJS= app.get("$referer/players/$id.v2.js").text.let {
            Deobfuscator.deobfuscateScript(it)
        }
        val jwt=findFirstJwt(epJS?: throw Exception("Unable to get jwt")) ?:return
        val jsonString=app.get("$referer$datasrc").toString()
        val mapper = jacksonObjectMapper()
        val servers: Map<String, List<VideoData>> = mapper.readValue(jsonString)
        val sources = mutableListOf<Pair<String, String>>()
        servers["kaido"]?.firstOrNull()?.url?.let {
            val finalUrl = "$it$jwt"
            sources += "Kaido" to finalUrl
        }

        servers["luffy"]?.forEach { video ->
            val finalUrl = "${video.url}$jwt"
            val m3u8 = getRedirectedUrl(finalUrl)
            sources += "Luffy-${video.resolution}" to m3u8
        }

        servers["zoro"]?.firstOrNull()?.url?.let {
            val finalUrl = "$it$jwt"
            val jsonResponse = getZoroJson(finalUrl)
            val (m3u8, vtt) = fetchZoroUrl(jsonResponse) ?: return
            sources += "Zoro" to m3u8
            sources += "Zoro" to vtt
        }


        sources.amap { (key, url) ->
            if (url.endsWith(".vvt")) {
                subtitleCallback.invoke(SubtitleFile("English", url))
            } else {
                callback(
                    newExtractorLink(
                        "AnimeOwl $key",
                        "AnimeOwl $key",
                        url = url,
                        type = INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = when {
                            key.contains("480") -> Qualities.P480.value
                            key.contains("720") -> Qualities.P720.value
                            key.contains("1080") -> Qualities.P1080.value
                            key.contains("1440") -> Qualities.P1440.value
                            key.contains("2160") -> Qualities.P2160.value
                            else -> Qualities.P1080.value
                        }
                    }
                )
            }
        }
        return
    }
    private fun findFirstJwt(text: String): String? {
        val jwtPattern = Regex("['\"]([A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+)['\"]")
        return jwtPattern.find(text)?.groupValues?.get(1)
    }

    private fun getRedirectedUrl(url: String): String {
        return url
    }

    data class ZoroResponse(val url: String, val subtitle: String)

    private suspend fun getZoroJson(url: String): String {
        return app.get(url).text
    }

    private fun fetchZoroUrl(jsonResponse: String): Pair<String, String>? {
        return try {
            val response = jacksonObjectMapper().readValue<ZoroResponse>(jsonResponse)
            response.url to response.subtitle
        } catch (e: Exception) {
            Log.e("Error:", "Error parsing Zoro JSON: ${e.message}")
            null
        }
    }

    data class VideoData(val resolution: String, val url: String)

}

class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.cc"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mediaUrl = url.replace("/e", "/media").replace("/e2", "/media")
        val displayName = referer ?: this.name

        val encoded = runCatching {
            app.get(mediaUrl, headers = HEADERS)
                .parsedSafe<AnimeKaiResponse>()?.result
        }.getOrNull() ?: return

        val decryptSteps = runCatching {
            app.get(KEYS_URL).parsedSafe<AnimeKaiKey>()?.megaup?.decrypt
        }.getOrNull() ?: return

        val decodedJson = runCatching {
            AnimekaiDecoder().decode(encoded, decryptSteps).replace("\\", "")
        }.getOrNull() ?: return

        val m3u8Data = runCatching {
            Gson().fromJson(decodedJson, AnimeKaiM3U8::class.java)
        }.getOrNull() ?: return

        m3u8Data.sources.firstOrNull()?.file?.let { m3u8 ->
            M3u8Helper.generateM3u8(displayName, m3u8, mainUrl).forEach(callback)
        }

        m3u8Data.tracks.forEach { track ->
            track.label?.let { subtitleCallback(SubtitleFile(it, track.file)) }
        }
    }

    companion object {
        private const val KEYS_URL =
            "https://raw.githubusercontent.com/amarullz/kaicodex/refs/heads/main/generated/kai_codex.json"

        private val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
            "Accept" to "text/html, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-GPC" to "1",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Priority" to "u=0",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "referer" to "https://animekai.to/",
            "Cookie" to "usertype=guest; session=your-session-id; cf_clearance=your-clearance-token"
        )
    }

    @Serializable
    data class AnimeKaiKey(val kai: Kai, val megaup: Megaup)

    @Serializable
    data class Kai(val encrypt: List<List<String>>, val decrypt: List<List<String>>)

    @Serializable
    data class Megaup(val encrypt: List<List<String>>, val decrypt: List<List<String>>)
}



