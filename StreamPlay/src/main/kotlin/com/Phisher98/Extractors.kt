package com.phisher98

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
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
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.PixelDrain
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.StreamPlay.Companion.animepaheAPI
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val sharedGson by lazy { Gson() }
private val extractorTitleExtensionRegex = Regex("\\.[a-zA-Z0-9]{2,4}$")
private val extractorTitlePatterns = listOf(
    Regex("(WEB[- ]?DL|WEB[- ]?RIP|WEBDL|WEBRIP|BLURAY|BDRIP|BRRIP|REMUX|HDRIP|DVDRIP|HDTV|UHD|CAM|TS|TC)", RegexOption.IGNORE_CASE),
    Regex("(H[ .]?264|H[ .]?265|X264|X265|HEVC|AVC|AV1|VP9|XVID)", RegexOption.IGNORE_CASE),
    Regex("(DDP?[ .]?[0-9]\\.[0-9]|DD[ .]?[0-9]\\.[0-9]|AAC[ .]?[0-9]\\.[0-9]|AC3|DTS[- ]?HD|DTS|EAC3|TRUEHD|ATMOS|FLAC|MP3|OPUS)", RegexOption.IGNORE_CASE),
    Regex("(HDR10\\+?|HDR|DV|DOLBY[ .]?VISION)", RegexOption.IGNORE_CASE),
    Regex("\\b(NF|AMZN|DSNP|HULU|CRAV|ATVP|HMAX|PCOK|STAN)\\b", RegexOption.IGNORE_CASE),
    Regex("\\b(REPACK|PROPER|REAL|EXTENDED|UNCUT|REMASTERED|LIMITED|MULTI|DUAL)\\b", RegexOption.IGNORE_CASE)
)
private val extractorNormalizeWebDlRegex = Regex("WEB[-_. ]?DL")
private val extractorNormalizeWebRipRegex = Regex("WEB[-_. ]?RIP")
private val extractorNormalizeH265Regex = Regex("H[ .]?265")
private val extractorNormalizeH264Regex = Regex("H[ .]?264")
private val extractorNormalizeDolbyVisionRegex = Regex("DOLBY[ .]?VISION")
private val extractorQualityRegex = Regex("(\\d{3,4})[pP]")

private fun extractCleanTitle(title: String): String {
    val name = title.replace(extractorTitleExtensionRegex, "")
    val results = linkedSetOf<String>()

    for (pattern in extractorTitlePatterns) {
        pattern.findAll(name).forEach { match ->
            val value = match.value.uppercase()
                .replace(extractorNormalizeWebDlRegex, "WEB-DL")
                .replace(extractorNormalizeWebRipRegex, "WEBRIP")
                .replace(extractorNormalizeH265Regex, "H265")
                .replace(extractorNormalizeH264Regex, "H264")
                .replace(extractorNormalizeDolbyVisionRegex, "DOLBYVISION")
                .replace("2160P", "4K")
            results.add(value)
        }
    }

    return results.joinToString(" ")
}

private fun extractIndexQuality(str: String?, defaultQuality: Int = Qualities.Unknown.value): Int {
    return extractorQualityRegex.find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: defaultQuality
}

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
            newSubtitleFile(
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
        @param:JsonProperty("data") val data: String? = null,
        @param:JsonProperty("sub") val sub: String? = null,
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
        ).text.let { tryParseJson<Source>(it) }?.data

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
        @param:JsonProperty("data") val data: String? = null,
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
    override val mainUrl: String = "https://vcloud.zip"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        var href = url

        if (href.contains("api/index.php")) {
            href = runCatching {
                app.get(url).document.selectFirst("div.main h4 a")?.attr("href")
            }.getOrNull() ?: return
        }
        Log.d("Phisher",href)
        val doc = runCatching { app.get(href).document }.getOrNull() ?: return
        val scriptTag = doc.selectFirst("script:containsData(url)")?.data() ?: ""

        val urlValue =
            Regex("""atob\(atob\('([^']+)'\)\)""")
                .find(scriptTag)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { runCatching { base64Decode(base64Decode(it)) }.getOrNull() }
                ?: Regex("""var\s+url\s*=\s*'([^']*)'""")
                    .find(scriptTag)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()

        if (urlValue.isEmpty()) return
        Log.d("Phisher",urlValue)

        val document = runCatching { app.get(urlValue).document }.getOrNull() ?: return
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val headerdetails = cleanTitle(header)

        val labelExtras = buildString {
            if (headerdetails.isNotEmpty()) append(headerdetails)
            if (size.isNotEmpty()) append(" [$size]")
        }

        val div = document.selectFirst("div.card-body") ?: return

        div.select("h2 a.btn").amap {

            val link = it.attr("href")
            val text = it.text()
            val quality = getIndexQuality(header)
            Log.d("Phisher",link)
            Log.d("Phisher",text)

            when {
                text.contains("FSLv2", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "FSLv2",
                            "[FSLv2] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("FSL") -> {
                    callback.invoke(
                        newExtractorLink(
                            "FSL Server",
                            "[FSL Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("BuzzServer") -> {
                    val dlink = app.get("$link/download", referer = link, allowRedirects = false).headers["hx-redirect"] ?: ""
                    val baseUrl = getBaseUrl(link)
                    if (dlink.isNotEmpty()) {
                        callback.invoke(
                            newExtractorLink(
                                "BuzzServer",
                                "[BuzzServer] $labelExtras",
                                baseUrl + dlink,
                            ) { this.quality = quality }
                        )
                    } else {
                        Log.w("Error:", "Not Found")
                    }
                }

                text.contains("pixeldra", ignoreCase = true) || text.contains("pixel", ignoreCase = true) || text.contains("PixeLServer", ignoreCase = true) -> {
                    val baseUrlLink = getBaseUrl(link)
                    val finalURL = if (link.contains("download", true)) link
                    else "$baseUrlLink/api/file/${link.substringAfterLast("/")}?download"

                    callback(
                        newExtractorLink(
                            "Pixeldrain",
                            "[Pixeldrain] $labelExtras",
                            finalURL
                        ) { this.quality = quality }
                    )
                }

                text.contains("PDL Server") -> {
                    callback.invoke(
                        newExtractorLink(
                            "PDL Server",
                            "[PDL Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                /*
                text.contains("10Gbps", ignoreCase = true) -> {
                    var currentLink = link
                    var redirectUrl: String?

                    while (true) {
                        val response = app.get(currentLink, allowRedirects = false)
                        redirectUrl = response.headers["location"]
                        if (redirectUrl == null) {
                            Log.e("HubCloud", "10Gbps: No redirect")
                            return@amap
                        }
                        if ("link=" in redirectUrl) break
                        currentLink = redirectUrl
                    }
                    val finalLink = redirectUrl.substringAfter("link=")
                    callback.invoke(
                        newExtractorLink(
                            "10Gbps [Download]",
                            "10Gbps [Download] $labelExtras",
                            finalLink,
                        ) { this.quality = quality }
                    )
                }
                */

                text.contains("S3 Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "S3 Server",
                            "[S3 Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("Mega Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "Mega Server",
                            "[Mega Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun cleanTitle(title: String): String {
        return extractCleanTitle(title)
    }

    private fun getIndexQuality(str: String?): Int {
        return extractIndexQuality(str)
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
        generateM3u8(
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

        generateM3u8(this.name, m3u8, "$mainUrl/").forEach(callback)
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
        generateM3u8(
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

class dwish : VidhideExtractor() {
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

class mixdrop21 : MixDrop(){
    override var mainUrl = "https://mixdrop21.net"
}

class m1xdrop : MixDrop(){
    override var mainUrl = "https://m1xdrop.net"
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

class PixelServer : PixelDrain() {
    override val mainUrl: String = "https://pixeldrain.dev"
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


class Shikshakdaak : HubCloud() {
    override var mainUrl: String = "https://shikshakdaak.com"
}

class Hubcloudone : HubCloud(){
    override var mainUrl = "https://hubcloud.one"
}

open class HubCloud : ExtractorApi() {

    override val name = "Hub-Cloud"
    override var mainUrl: String = runBlocking {
        StreamPlay.getDomains()?.hubcloud ?: "https://hubcloud.foo"
    }
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val tag = "HubCloud"
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
            if (headerDetails.isNotEmpty()) append(headerDetails)
            if (size.isNotEmpty()) append(" [$size]")
        }

        document.select("a.btn").forEach { element ->
            val link = element.attr("href")

            val blocked = listOf("tinyurl", "telegram", "hubcloud.foo/tg")
            if (blocked.any { it in link }) return@forEach

            val text = element.ownText()
            val label = text.lowercase()
            when {
                "fslv2" in label -> {
                    callback(
                        newExtractorLink(
                            "FSLv2",
                            "[FSLv2] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "fsl" in label -> {
                    callback(
                        newExtractorLink(
                            "FSL Server",
                            "[FSL Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "download file" in label -> {
                    callback(
                        newExtractorLink(
                            "Download File",
                            "[Download file] $labelExtras",
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
                                "BuzzServer",
                                "[BuzzServer] $labelExtras",
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
                            "Pixeldrain",
                            "[Pixeldrain] $labelExtras",
                            finalUrl
                        ) { this.quality = quality }
                    )
                }

                "s3 server" in label -> {
                    callback(
                        newExtractorLink(
                            "S3 Server",
                            "[S3 Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "mega server" in label -> {
                    callback(
                        newExtractorLink(
                            "Mega Server",
                            "[Mega Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "pdl Server" in label -> {
                    callback(
                        newExtractorLink(
                            "PDL Server",
                            "[PDL Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return extractIndexQuality(str, Qualities.P2160.value)
    }

    private fun getBaseUrl(url: String): String {
        return runCatching {
            URI(url).let { "${it.scheme}://${it.host}" }
        }.getOrDefault("")
    }

    private fun cleanTitle(title: String): String {
        return extractCleanTitle(title)
    }
}


class OFile : ExtractorApi() {
    override val name = "OXXFile"
    override val mainUrl = "https://new.oxxfile.info"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cf = CloudflareKiller()

        val redirectedUrl = app.get(url, interceptor = cf).url

        val idIndex = redirectedUrl.indexOf("/s/")
        if (idIndex == -1) return

        val baseDomain = redirectedUrl.substring(0, idIndex)
        val id = redirectedUrl.substring(idIndex + 3).substringBefore('/')
        if (id.isEmpty()) return

        val hubcloudUrl = app.get(
            "$baseDomain/api/s/$id/hubcloud",
            interceptor = cf
        ).url


        loadExtractor(
            hubcloudUrl,
            referer ?: baseDomain,
            subtitleCallback,
            callback
        )
    }
}


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
        return extractIndexQuality(str)
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
        val parsed = cleanTitle(qualityText.substringAfter(":").trim())
        val size = document
            .selectFirst("li:nth-child(3)")
            ?.text()
            ?.substringAfter(":")?.substringBefore("\n")
            ?.trim()
            .orEmpty()


        val labelExtras = buildString {
            if (parsed.isNotEmpty()) append(parsed)
            if (size.isNotEmpty()) append(" [$size]")
        }

        document.select("div.text-center > a").forEach { element ->
            val text = element.text()
            val href = element.attr("href")
            Log.d("Driveseed", "Link: $href")

            if (href.isNotBlank()) {
                when {
                    text.contains("Resume Worker Bot", ignoreCase = true) -> {
                        resumeBot(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeBot(VLC)",
                                    "$name [ResumeBot(VLC)] $labelExtras",
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
                                    "$name [CF Type1] $labelExtras",
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
                                    "$name [ResumeCloud] $labelExtras",
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
                                "$name [Cloud Download] $labelExtras",
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

    private fun cleanTitle(title: String): String {
        return extractCleanTitle(title)
    }
}


class Kwik : ExtractorApi() {
    override val name = "Kwik"
    override val mainUrl = "https://kwik.cx"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = "${animepaheAPI}/")
        val title = res.document.title()

        val script =
            res.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        val unpacked = getAndUnpack(script ?: return)
        val m3u8 =
            Regex("source=\\s*'(.*?m3u8.*?)'").find(unpacked)?.groupValues?.getOrNull(1) ?: ""

        val fileName = title.substringBeforeLast(".mp4") + ".mp4"

        val mp4Url = m3u8
            .replace("/stream/", "/mp4/")
            .substringBeforeLast("/")
            .let { "$it?file=${URLEncoder.encode(fileName, "UTF-8")}" }


        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = m3u8,
                type = INFER_TYPE
            ) {
                this.referer = mainUrl
                this.quality = getQualityFromName(referer)
                this.headers = mapOf("origin" to mainUrl)
            }
        )

        callback(
            newExtractorLink(
                name,
                "$name [Download]",
                mp4Url,
                ExtractorLinkType.VIDEO
            ) {
                this.referer = url
                this.quality = getQualityFromName(fileName)
                this.headers = mapOf(
                    "Referer" to url,
                    "Origin" to mainUrl
                )
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
        val fContentString = fContent.body.toString()

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
                this.referer = "https://kwik.cx/"
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
            loadSourceNameExtractor("Anichi",href,"",subtitleCallback,callback)
        }
    }
}

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (sids, host) = extractSidsAndHost(url) ?: return

        if (host.isNullOrBlank()) {
            Log.e("Error:", "Host is null, aborting")
            return
        }

        sids.forEach { sid ->
            try {
                val responseText = app.post(
                    "$host/embedhelper.php",
                    data = mapOf("sid" to sid),
                    headers = mapOf(
                        "Referer" to host,
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                ).text

                val root = JsonParser.parseString(responseText)
                    .takeIf { it.isJsonObject }
                    ?.asJsonObject ?: return@forEach

                val siteUrls = root["siteUrls"]?.asJsonObject ?: return@forEach

                // Ensure defaults
                if (!siteUrls.has("gofs")) {
                    siteUrls.addProperty("GoFile", "https://gofile.io/d/")
                }
                if (!siteUrls.has("buzzheavier")) {
                    siteUrls.addProperty("buzzheavier", "https://buzzheavier.com/")
                }

                val siteFriendlyNames = root["siteFriendlyNames"]?.asJsonObject

                val decodedMresult = when {
                    root["mresult"]?.isJsonObject == true ->
                        root["mresult"].asJsonObject

                    root["mresult"]?.isJsonPrimitive == true -> {
                        try {
                            base64Decode(root["mresult"].asString)
                                .let { JsonParser.parseString(it).asJsonObject }
                        } catch (e: Exception) {
                            Log.e("Phisher", "Decode failed: $e")
                            return@forEach
                        }
                    }

                    else -> return@forEach
                }

                siteUrls.keySet().intersect(decodedMresult.keySet()).forEach { key ->
                    val base = siteUrls[key]?.asString?.trimEnd('/') ?: return@forEach
                    val path = decodedMresult[key]?.asString?.trimStart('/') ?: return@forEach

                    val fullUrl = "$base/$path"
                    val friendlyName = siteFriendlyNames?.get(key)?.asString ?: key

                    try {
                        Log.d("Sites:", "$friendlyName → $fullUrl")

                        when (friendlyName) {
                            "StreamHG", "EarnVids" ->
                                VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)

                            "RpmShare", "UpnShare", "StreamP2p" ->
                                VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)

                            else ->
                                loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                        }

                    } catch (e: Exception) {
                        Log.e("Error:", "Extractor failed: $friendlyName $e")
                    }
                }

            } catch (e: Exception) {
                Log.e("Error:", "SID failed: $sid $e")
            }
        }
    }

    // ------------------ CORE EXTRACTION ------------------

    private suspend fun extractSidsAndHost(url: String): Pair<List<String>, String?>? {
        return if (!url.contains("key=")) {
            val sid = url.substringAfterLast("embed/")
            val host = getBaseUrl(app.get(url).url)
            Pair(listOf(sid), host)
        } else {
            var pageText = app.get(url).text

            val finalId = Regex("""FinalID\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1)

            val myKey = Regex("""myKey\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1)

            val idType = Regex("""idType\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1) ?: "imdbid"

            val baseUrl = Regex("""let\s+baseUrl\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1)
                ?.takeIf { it.startsWith("http") }
                ?: Regex("""player_base\s*=\s*["']([^"']+)["']""")
                    .find(pageText)?.groupValues?.get(1)

            val host = baseUrl?.let { getBaseUrl(it) }

            if (finalId != null && myKey != null) {
                val apiUrl = if (url.contains("/tv/")) {
                    val season = Regex("""/tv/\d+/(\d+)/""")
                        .find(url)?.groupValues?.get(1) ?: "1"

                    val episode = Regex("""/tv/\d+/\d+/(\d+)""")
                        .find(url)?.groupValues?.get(1) ?: "1"

                    "$mainUrl/myseriesapi?tmdbid=$finalId&season=$season&epname=$episode&key=$myKey"
                } else {
                    "$mainUrl/mymovieapi?$idType=$finalId&key=$myKey"
                }

                pageText = app.get(apiUrl, referer = apiUrl).text
            }

            val json = JsonParser.parseString(pageText)
                .takeIf { it.isJsonObject }
                ?.asJsonObject ?: return null

            val dataArray = json["data"]?.asJsonArray

            val sids = dataArray
                ?.mapNotNull {
                    it.asJsonObject["fileslug"]?.asString
                }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(url.substringAfterLast("/")) // fallback

            Pair(sids, host)
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}

class Iqsmartgames: GDMirrorbot() {
    override var name = "Iqsmartgames"
    override var mainUrl = "https://streams.iqsmartgames.com"
    override var requiresReferer = true
}

open class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://*.gdflix.*"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val newUrl = try { app.get(url).document.selectFirst("meta[http-equiv=refresh]")?.attr("content")
            ?.substringAfter("url=")
        } catch (e: Exception) {
            Log.e("GDFlix", "Redirect error: ${e.localizedMessage}")
            return
        } ?: url

        val document = app.get(newUrl).document

        val fileName = document.select("ul > li.list-group-item:contains(Name)")
            .text()
            .substringAfter("Name : ")

        val fileSize = document.select("ul > li.list-group-item:contains(Size)")
            .text()
            .substringAfter("Size : ")

        val quality = getIndexQuality(fileName)
        val sourcename = referer
            ?.takeIf { it.isNotEmpty() && !it.startsWith("http", ignoreCase = true) }
            ?: ""

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.text()
            val link = anchor.attr("href")

            when {

                text.contains("DIRECT DL", true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "GDFlix [Direct]",
                            "$sourcename GDFlix [Direct] [$fileSize]",
                            link
                        ) { this.quality = quality }
                    )
                }


                text.contains("Instant DL") -> {
                    try {
                        val instantLink = app.get(link, allowRedirects = false).headers["location"]?.substringAfter("url=").orEmpty()
                        if (instantLink.isNotEmpty()) {
                            callback.invoke(
                                newExtractorLink(
                                    "GDFlix [Instant Download]",
                                    "$sourcename GDFlix [Instant Download] [$fileSize]",
                                    instantLink
                                ) { this.quality = quality }
                            )
                        }
                    } catch (e: Exception) {
                        Log.d("Instant DL", e.toString())
                    }
                }

                text.contains("GoFile", true) -> {
                    Gofile().getUrl(link)
                }

                text.contains("pixeldra", true)
                        || text.contains("pixel", true)
                        || text.contains("PixeLServer", true) -> {

                    val baseUrlLink = getBaseUrl(link)

                    val finalURL =
                        if (link.contains("download", true)) link
                        else "$baseUrlLink/api/file/${link.substringAfterLast("/")}?download"

                    callback.invoke(
                        newExtractorLink(
                            "GDFlix [Pixeldrain]",
                            "$sourcename GDFlix [Pixeldrain] [$fileSize]",
                            finalURL
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    Log.d("GDFlix", "No server matched")
                }
            }
        }

        // Cloudflare backup
        try {

            val types = listOf("type=1", "type=2")

            types.forEach { type ->

                val sourceurl = app.get("${newUrl.replace("file", "wfile")}?$type")
                    .document
                    .select("a.btn-success")
                    .attr("href")

                if (sourceurl.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            "GDFlix [CF]",
                            "$sourcename GDFlix [CF] [$fileSize]",
                            sourceurl
                        ) { this.quality = quality }
                    )
                }
            }

        } catch (e: Exception) {
            Log.d("GDFlix CF", e.toString())
        }
    }
}

open class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"
    private val browserLanguage = "en-GB"
    private val secret = "5d4f7g8sd45fsd"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return

        val token = app.post(
            "$mainApi/accounts",
        ).parsedSafe<AccountResponse>()?.data?.token ?: return

        val currentTimeSeconds = System.currentTimeMillis() / 1000
        val interval = (currentTimeSeconds / 14400).toString()
        val message = listOf(USER_AGENT, browserLanguage, token, interval, secret).joinToString("::")
        val hashedToken = sha256(message)

        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "User-Agent" to USER_AGENT,
            "Authorization" to "Bearer $token",
            "X-BL" to browserLanguage,
            "X-Website-Token" to hashedToken
        )

        val parsedResponse = app.get(
            "$mainApi/contents/$id?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1",
            headers = headers
        ).parsedSafe<GofileResponse>()

        val childrenMap = parsedResponse?.data?.children ?: return

        for ((_, file) in childrenMap) {
            if (file.link.isNullOrEmpty() || file.type != "file") continue
            val fileName = file.name ?: ""
            val size = file.size ?: 0L
            val formattedSize = formatBytes(size)

            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "[Gofile] $fileName [$formattedSize]",
                    file.link,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = getQuality(fileName)
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        }
    }

    private fun getQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes.toDouble() / (1024 * 1024))
            else -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        }
    }

    data class AccountResponse(
        @param:JsonProperty("data") val data: AccountData? = null
    )

    data class AccountData(
        @param:JsonProperty("token") val token: String? = null
    )

    data class GofileResponse(
        @param:JsonProperty("data") val data: GofileData? = null
    )

    data class GofileData(
        @param:JsonProperty("children") val children: Map<String, GofileFile>? = null
    )

    data class GofileFile(
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("link") val link: String? = null,
        @param:JsonProperty("size") val size: Long? = 0L
    )
}

class UqloadsXyz : ExtractorApi() {
    override val name = "Uqloadsxyz"
    override val mainUrl = "https://uqloads.xyz"
    override val requiresReferer = true

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var response = app.get(url.replace("/download/", "/e/"), referer = referer)
        val iframe = response.document.selectFirst("iframe")
        if (iframe != null) {
            response = app.get(
                iframe.attr("src"), headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ), referer = response.url
            )
        }

        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return
        val regex = Regex("""hls2":"(?<hls2>[^"]+)"|hls4":"(?<hls4>[^"]+)"""")
        val links = regex.findAll(script)
            .mapNotNull { matchResult ->
                val hls2 = matchResult.groups["hls2"]?.value
                val hls4 = matchResult.groups["hls4"]?.value
                when {
                    hls2 != null -> hls2
                    hls4 != null -> "https://uqloads.xyz$hls4"
                    else -> null
                }
            }.toList()
        links.forEach { m3u8->
            generateM3u8(
                name,
                m3u8,
                mainUrl
            ).forEach(callback)
        }

    }
}


class Cdnstreame : ExtractorApi() {
    override val name = "Cdnstreame"
    override val mainUrl = "https://cdnstreame.net"
    override val requiresReferer = false

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val id = url.substringAfterLast("/").substringBefore("?")
        val apiUrl = "$mainUrl/embed-1/v2/e-1/getSources?id=$id"

        val response = app.get(apiUrl, headers = headers)
            .parsedSafe<MegacloudResponse>() ?: return

        val key = app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json")
            .parsedSafe<Megakey>()?.rabbit ?: return

        val decryptedJson = decryptOpenSSL(response.sources, key)
        val m3u8Url = parseSourceJson(decryptedJson).firstOrNull()?.file ?: return

        val m3u8Headers = mapOf("Referer" to mainUrl, "Origin" to mainUrl)
        generateM3u8(name, m3u8Url, mainUrl, headers = m3u8Headers).forEach(callback)

        response.tracks
            .filter { it.kind in listOf("captions", "subtitles") }
            .forEach { track ->
                subtitleCallback(newSubtitleFile(track.label, track.file))
            }
    }

    data class MegacloudResponse(
        val sources: String,
        val tracks: List<MegacloudTrack>,
        val encrypted: Boolean,
        val intro: MegacloudIntro,
        val outro: MegacloudOutro,
        val server: Long
    )

    data class MegacloudTrack(val file: String, val label: String, val kind: String, val default: Boolean?)
    data class MegacloudIntro(val start: Long, val end: Long)
    data class MegacloudOutro(val start: Long, val end: Long)
    data class Megakey(val mega: String, val rabbit: String)
    data class Source2(val file: String, val type: String)

    private fun parseSourceJson(json: String): List<Source2> = runCatching {
        val jsonArray = JSONArray(json)
        List(jsonArray.length()) {
            val obj = jsonArray.getJSONObject(it)
            Source2(obj.getString("file"), obj.getString("type"))
        }
    }.getOrElse {
        Log.e("parseSourceJson", "Failed to parse JSON: ${it.message}")
        emptyList()
    }

    private fun opensslKeyIv(password: ByteArray, salt: ByteArray, keyLen: Int = 32, ivLen: Int = 16): Pair<ByteArray, ByteArray> {
        var d = ByteArray(0)
        var d_i = ByteArray(0)
        while (d.size < keyLen + ivLen) {
            d_i = MessageDigest.getInstance("MD5").digest(d_i + password + salt)
            d += d_i
        }
        return d.copyOfRange(0, keyLen) to d.copyOfRange(keyLen, keyLen + ivLen)
    }

    @SuppressLint("NewApi")
    private fun decryptOpenSSL(encBase64: String, password: String): String {
        return runCatching {
            val data = Base64.getDecoder().decode(encBase64)
            require(data.copyOfRange(0, 8).contentEquals("Salted__".toByteArray()))
            val salt = data.copyOfRange(8, 16)
            val (key, iv) = opensslKeyIv(password.toByteArray(), salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }

            String(cipher.doFinal(data.copyOfRange(16, data.size)))
        }.getOrElse {
            Log.e("decryptOpenSSL", "Decryption failed: ${it.message}")
            ""
        }
    }
}


class Videostr : ExtractorApi() {
    override val name = "Videostr"
    override val mainUrl = "https://videostr.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to mainUrl
        )

        val id = url.substringAfterLast("/").substringBefore("?")
        val html = app.get(url, headers = headers).text

        val nonce = Regex("""\b[a-zA-Z0-9]{48}\b""").find(html)?.value
            ?: Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""")
                .find(html)?.let { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            ?: throw Exception("Nonce not found")

        val apiUrl = "$mainUrl/embed-1/v3/e-1/getSources?id=$id&_k=$nonce"
        val response = sharedGson.fromJson(
            app.get(apiUrl, headers).text,
            VideostrResponse::class.java
        )

        val encodedSource = response.sources.firstOrNull()?.file
            ?: throw Exception("No sources found")

        val m3u8 = if (".m3u8" in encodedSource) {
            encodedSource
        } else {
            val key = sharedGson.fromJson(
                app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json").text,
                Megakey::class.java
            ).vidstr

            val decodeUrl =
                "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"

            val fullUrl =
                "$decodeUrl?encrypted_data=${URLEncoder.encode(encodedSource,"UTF-8")}&nonce=${URLEncoder.encode(nonce,"UTF-8")}&secret=${URLEncoder.encode(key,"UTF-8")}"

            Regex("\"file\":\"(.*?)\"")
                .find(app.get(fullUrl).text)
                ?.groupValues?.get(1)
                ?: throw Exception("Video URL not found")
        }

        generateM3u8(
            name,
            m3u8,
            mainUrl,
            headers = mapOf("Referer" to "$mainUrl/", "Origin" to mainUrl)
        ).forEach(callback)

        response.tracks.forEach {
            if (it.kind == "captions" || it.kind == "subtitles") {
                subtitleCallback(newSubtitleFile(it.label, it.file))
            }
        }
    }

    data class VideostrResponse(
        val sources: List<VideostrSource>,
        val tracks: List<Track>
    )

    data class VideostrSource(
        val file: String
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String
    )

    data class Megakey(
        val vidstr: String
    )
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
        val href=app.get(url, timeout = 2000).document.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud",ignoreCase = true)) HubCloud().getUrl(href,"HubDrive",subtitleCallback,callback)
        else loadExtractor(href,"HubDrive",subtitleCallback, callback)
    }
}

internal class Molop : ExtractorApi() {
    override val name = "Molop"
    override val mainUrl = "https://molop.art"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers= mapOf("user-agent" to "okhttp/4.12.0")
        val res = app.get(url, referer = referer, headers = headers).document
        val sniffScript = res.selectFirst("script:containsData(sniff\\()")
            ?.data()
            ?.substringAfter("sniff(")
            ?.substringBefore(");") ?: return
        val cleaned = sniffScript.replace(Regex("\\[.*?]"), "")
        val regex = Regex("\"(.*?)\"")
        val args = regex.findAll(cleaned).map { it.groupValues[1].trim() }.toList()
        val token = args.lastOrNull().orEmpty()
        val m3u8 = "$mainUrl/m3u8/${args[1]}/${args[2]}/master.txt?s=1&cache=1&plt=$token"
        generateM3u8(name, m3u8, mainUrl, headers = headers).forEach(callback)
    }
}

class Rubyvidhub : VidhideExtractor() {
    override var mainUrl = "https://rubyvidhub.com"
}

class Movearnpre : VidhideExtractor() {
    override var mainUrl = "https://movearnpre.com"
    override var requiresReferer = true
}

class smoothpre : VidhideExtractor() {
    override var mainUrl = "https://smoothpre.com"
    override var requiresReferer = true
}


internal class Akirabox : ExtractorApi() {
    override val name = "Akirabox"
    override val mainUrl = "https://akirabox.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id=url.substringAfter("$mainUrl/").substringBefore("/")
        val m3u8= app.post("$mainUrl/$id/file/generate", headers = mapOf("x-csrf-token" to "L57KI068FpaS5Ttgo1W20tQMlFhtEwCJGkOgIdSH")).parsedSafe<AkiraboxRes>()?.downloadLink
        if (m3u8!=null)
        {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3u8,
                    ExtractorLinkType.M3U8
                )
                {
                    this.referer=url
                    this.quality=Qualities.P1080.value
                    this.headers=headers

                }
            )
        }
    }

    data class AkiraboxRes(
        @param:JsonProperty("download_link")
        val downloadLink: String,
    )

}


class BuzzServer : ExtractorApi() {
    override val name = "BuzzServer"
    override val mainUrl = "https://buzzheavier.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val qualityText = app.get(url).document.selectFirst("div.max-w-2xl > span")?.text()
            val quality = getIndexQuality(qualityText)
            val response = app.get("$url/download", referer = url, allowRedirects = false)
            val redirectUrl = response.headers["hx-redirect"] ?: ""

            if (redirectUrl.isNotEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        "BuzzServer",
                        "BuzzServer",
                        redirectUrl,
                    ) {
                        this.quality = quality
                    }
                )
            } else {
                Log.w("BuzzServer", "No redirect URL found in headers.")
            }
        } catch (e: Exception) {
            Log.e("BuzzServer", "Exception occurred: ${e.message}")
        }
    }
}
class StreamwishHG : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to"
}

class StreamwishTO : StreamWishExtractor() {
    override val mainUrl = "https://streamwish.to"
}



class Vidora : ExtractorApi() {
    override val name = "Vidora"
    override val mainUrl = "https://vidora.stream"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("/download/", "/e/")
        var pageResponse = app.get(embedUrl, referer = referer)

        val iframeElement = pageResponse.document.selectFirst("iframe")
        if (iframeElement != null) {
            val iframeUrl = iframeElement.attr("src")
            pageResponse = app.get(
                iframeUrl,
                headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ),
                referer = pageResponse.url
            )
        }
        val headers= mapOf("origin" to mainUrl, "referer" to mainUrl)
        val scriptData = if (!getPacked(pageResponse.text).isNullOrEmpty()) {
            getAndUnpack(pageResponse.text)
        } else {
            pageResponse.document.selectFirst("script:containsData(sources:)")?.data()
        }

        val m3u8Url = scriptData?.let {
            Regex("""file:\s*"(.*?m3u8.*?)"""").find(it)?.groupValues?.getOrNull(1)
        }

        if (!m3u8Url.isNullOrEmpty()) {
            generateM3u8(
                name,
                m3u8Url,
                mainUrl,
                headers=headers
            ).forEach(callback)
        } else {
            // Fallback using WebViewResolver
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedUrl = app.get(
                url = pageResponse.url,
                referer = referer,
                interceptor = resolver
            ).url

            if (interceptedUrl.isNotEmpty()) {
                generateM3u8(
                    name,
                    interceptedUrl,
                    mainUrl
                ).forEach(callback)
            } else {
                Log.d("Filesim", "No m3u8 found via script or WebView fallback.")
            }
        }
    }
}

class XdMoviesExtractor : ExtractorApi() {

    override val name = "XdMoviesExtractor"
    override val mainUrl = "https://link.xdmovies.wtf"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val redirect = bypassXD(url) ?: return
        loadExtractor(redirect, "HubCloud", subtitleCallback, callback)
    }
}

class HdStream4u : VidHidePro() {
    override var mainUrl = "https://hdstream4u.*"
}

class Hubstream : VidStack() {
    override var mainUrl = "https://hubstream.*"
}

class KumiUns : VidStack() {
    override var mainUrl = "https://kumi.uns.wtf"
}

open class Hblinks : ExtractorApi() {
    override val name = "Hblinks"
    override val mainUrl = "https://hblinks.dad"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url).document.select("h3 a,h5 a,div.entry-content p a").forEach {
            val lower = it.absUrl("href").ifBlank { it.attr("href") }
            val href = lower.lowercase()
            when {
                "hubdrive" in lower -> Hubdrive().getUrl(href, name, subtitleCallback, callback)
                "hubcloud" in lower -> HubCloud().getUrl(href, name, subtitleCallback, callback)
                "hubcdn" in lower -> HUBCDN().getUrl(href, name, subtitleCallback, callback)
                else ->loadSourceNameExtractor(
                    name,
                    href,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        }
    }
}

class HUBCDN : ExtractorApi() {
    override val name = "Hubcdn"
    override val mainUrl = "https://hubcdn.*"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url).document.text().let {
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

open class Krakenfiles : ExtractorApi() {
    override val name = "Krakenfiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:view|embed-video)/([\\da-zA-Z]+)")
            .find(url)
            ?.groupValues
            ?.get(1)
            ?: return

        val doc = app.get("$mainUrl/embed-video/$id").document
        val title = doc.select("span.coin-name").text()
        val link = doc.selectFirst("source")?.attr("src") ?: return
        val quality = getIndexQuality(title)


        callback.invoke(
            newExtractorLink(
                name,
                name,
                httpsify(link)
            ) {
                this.quality = quality
            }
        )
    }
}

open class PpzjYoutube : ExtractorApi() {

    override val name = "PpzjYoutube"
    override val mainUrl = "https://if9.ppzj-youtube.cfd"
    override val requiresReferer = true
    private val apiUrl = "https://api-play-270325.ppzj-youtube.cfd/api/tp1rd/playiframe"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val TAG = "PpzjYoutube"
        try {
            val domain = URI(url).let { "${it.scheme}://${it.host}" }
            val headers = mapOf(
                "User-Agent"       to USER_AGENT,
                "Referer"          to domain,
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type"     to "application/x-www-form-urlencoded"
            )

            val html = app.get(url, headers = headers).text
            val matches = Regex("""const\s*id(?:User|file)_enc\s*=\s*"([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
            val encryptedFileId = matches[0]
            val encryptedUserId = matches[1]
            val fileId = decryptHexAES(encryptedFileId, "jcLycoRJT6OWjoWspgLMOZwS3aSS0lEn")
            val userId = decryptHexAES(encryptedUserId, "PZZ3J3LDbLT0GY7qSA5wW5vchqgpO36O")
            val payload = buildPayloadJson(fileId, userId, referer ?: "https://m4uhd.vip")

            val encryptedPayload = encryptHexAES(
                payload,
                "vlVbUQhkOhoSfyteyzGeeDzU0BHoeTyZ"
            )

            val signatureInput = encryptedPayload + "KRWN3AdgmxEMcd2vLN1ju9qKe8Feco5h"
            val signature = md5(signatureInput)
            val body = "data=$encryptedPayload%7C$signature".toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val response = app.post(
                apiUrl,
                headers     = headers,
                requestBody = body
            ).parsedSafe<Map<String, Any>>() ?: return

            val encryptedVideo = (response["data"] as? String)?.substringBefore("|")

            if (encryptedVideo == null) {
                Log.e(TAG, "ERROR: No 'data' field in response or data is null")
                Log.d(TAG, "Response keys: ${response.keys}")
                return
            }

            val videoUrl = decryptHexAES(
                encryptedVideo,
                "oJwmvmVBajMaRCTklxbfjavpQO7SZpsL"
            )

            generateM3u8(
                name,
                videoUrl,
                domain,
                Qualities.P1080.value
            ).forEach(callback)

        } catch (e: Exception) {
            Log.e(TAG, "==================== ERROR ====================")
            Log.e(TAG, "Exception occurred: ${e.message}")
            Log.e(TAG, "Exception: ${e.stackTraceToString()}")
        }
    }

    private fun buildPayloadJson(
        fileId: String,
        userId: String,
        domain: String
    ): String {

        val payload =
            """
        {
          "idfile":"$fileId",
          "iduser":"$userId",
          "domain_play":"$domain",
          "platform":"Linux armv81",
          "hlsSupport":true,
          "jwplayer":{
            "Browser":{
              "androidNative":false,
              "chrome":true,
              "edge":false,
              "facebook":false,
              "firefox":false,
              "ie":false,
              "msie":false,
              "safari":false,
              "version":{
                "version":"137.0.0.0",
                "major":137,
                "minor":0
              }
            },
            "OS":{
              "android":true,
              "iOS":false,
              "mobile":true,
              "mac":false,
              "iPad":false,
              "iPhone":false,
              "windows":false,
              "tizen":false,
              "tizenApp":false,
              "version":{
                "version":"10",
                "major":10,
                "minor":null
              }
            },
            "Features":{
              "iframe":false,
              "passiveEvents":true,
              "backgroundLoading":true
            }
          }
        }
        """.trimIndent()
                .replace("\n", "")
                .replace("  ", "")


        return payload
    }

    private fun encryptHexAES(plaintext: String, password: String): String {
        val salt = ByteArray(8).apply { SecureRandom().nextBytes(this) }
        val keyIv = deriveKeyIv(password.toByteArray(), salt)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").also {
            it.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyIv.first, "AES"),
                IvParameterSpec(keyIv.second)
            )
        }

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val result = "Salted__".toByteArray() + salt + encrypted
        return result.toHex()
    }

    private fun decryptHexAES(hex: String, password: String): String {
        val bytes = hex.hexToBytes()
        val salt = bytes.copyOfRange(8, 16)
        val ciphertext = bytes.copyOfRange(16, bytes.size)
        val keyIv = deriveKeyIv(password.toByteArray(), salt)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").also {
            it.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyIv.first, "AES"),
                IvParameterSpec(keyIv.second)
            )
        }

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveKeyIv(password: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val md5 = MessageDigest.getInstance("MD5")
        val keyIv = ByteArray(48)
        var prev = ByteArray(0)
        var generated = 0

        while (generated < 48) {
            md5.reset()
            md5.update(prev)
            md5.update(password)
            md5.update(salt)
            prev = md5.digest()
            System.arraycopy(prev, 0, keyIv, generated, prev.size)
            generated += prev.size
        }

        return Pair(
            keyIv.copyOfRange(0, 32),
            keyIv.copyOfRange(32, 48)
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

class Filesdl : ExtractorApi() {
    override val name = "Filesdl"
    override val mainUrl = "https://*.filesdl.site"
    override val requiresReferer = true

    companion object {
        private val QUALITY_REGEX = Regex("(\\d{3,4}p)", RegexOption.IGNORE_CASE)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document

        val quality = QUALITY_REGEX.find(doc.selectFirst("div.title")?.text().orEmpty())?.value ?: "Unknown"
        val inferredQuality = getQualityFromName(quality)
        doc.select("div.container a").forEach { element ->

            val source = element.text().trim()
            val href = element.attr("href")

            when {
                source.contains("hubcloud", ignoreCase = true) -> {
                    HubCloud().getUrl(href, "Filmyfiy", subtitleCallback, callback)
                }

                source.contains("GDFLIX", ignoreCase = true) -> {
                    GDFlix().getUrl(href, "Filmyfiy", subtitleCallback, callback)
                }

                source.contains("Gofile", ignoreCase = true) -> {
                    Gofile().getUrl(href, "Filmyfiy", subtitleCallback, callback)
                }

                source.contains("Fast Cloud", ignoreCase = true) || source.contains("Ultra Fast Download", ignoreCase = true)-> {
                    callback(
                        newExtractorLink(
                            source = "Fast Cloud",
                            name = "Filmyfiy [Fast Cloud]",
                            url = href,
                            type = INFER_TYPE
                        ) {
                            this.quality = inferredQuality
                        }
                    )
                }

                source.contains("Direct Download", true)
                        || source.contains("Ultra FastDL", true)
                        || source.contains("Fast Cloud-02", true) -> {

                    val res = app.get(
                        href,
                        allowRedirects = false
                    )

                    val redirectUrl = res.headers["Location"]
                        ?: res.headers["location"]
                        ?: href

                    val finalUrl = fixUrl(redirectUrl, href)
                    if (
                        finalUrl.contains(".mkv", true) ||
                        finalUrl.contains(".mp4", true) ||
                        finalUrl.contains(".m3u8", true)
                    ) {
                        callback(
                            newExtractorLink(
                                source = "[FastDL] [VLC]",
                                name = "Filmyfiy [FastDL] [VLC]",
                                url = finalUrl,
                                type = INFER_TYPE
                            ) {
                                this.quality = inferredQuality
                            }
                        )
                    } else {
                        loadSourceNameExtractor(
                            "Filmyfiy",
                            url = finalUrl,
                            referer = "",
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                    }
                }
            }
        }
    }
}

class HDm2 : ExtractorApi() {
    override val name = "Ultra Stream V3"
    override val mainUrl = "https://hdm2.ink"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("user-agent" to "okhttp/4.12.0")

        val res = app.get(url, referer = referer, headers = headers).text
        val regex = Regex("""data-stream-url=["'](.*?)["']""")
        val args = regex.find(res)?.groupValues?.get(1)?.trim()

        if (!args.isNullOrEmpty()) {
            val m3u8 = if (args.startsWith("http")) {
                args
            } else {
                "${mainUrl.trimEnd('/')}/${args.removePrefix("/")}"
            }
            val safe = safeUrl(m3u8)
            generateM3u8(name, safe, mainUrl, headers = headers).forEach(callback)
        } else {
            Log.w("HDm2", "stream url not found")
        }
    }

    private fun safeUrl(raw: String): String {
        val cleaned = raw.replace("&amp;", "&")
        val base = cleaned.substringBefore("?")
        val tok = Regex("""[?&]tok=([^&]+)""").find(cleaned)?.groupValues?.get(1)
        return if (!tok.isNullOrEmpty()) "$base?tok=$tok" else base
    }
}


class ZenCloudExtractor : ExtractorApi() {

    override val name = "ZenCloud"
    override val mainUrl = "https://zencloudz.cc"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(url).text
        if (html.isBlank()) return

        val seed = Regex("""obfuscation_seed:"([^"]+)"""")
            .find(html)?.groupValues?.get(1) ?: return

        val dataBlock = extractJsonBlock(html, "obfuscated_crypto_data") ?: return

        val dataJson = try {
            JSONObject(dataBlock)
        } catch (_: Exception) { return }

        val h1 = sha256(seed)
        val h2 = sha256(h1)

        val keyField      = "kf_${h1.substring(8,  16)}"
        val ivField       = "ivf_${h1.substring(16, 24)}"
        val containerName = "cd_${h1.substring(24, 32)}"
        val arrayName     = "ad_${h1.substring(32, 40)}"
        val objectName    = "od_${h1.substring(40, 48)}"
        val tokenField    = "${h1.substring(48, 64)}_${h1.substring(56, 64)}"
        val keyFrag2Field = "${h2.take(16)}_${h2.substring(16, 24)}"

        if (!dataJson.has(containerName)) return
        val container = dataJson.getJSONObject(containerName)

        if (!container.has(arrayName)) return
        val arr = container.getJSONArray(arrayName)

        if (arr.length() == 0) return
        val arrObj = arr.getJSONObject(0)

        if (!arrObj.has(objectName)) return
        val obj = arrObj.getJSONObject(objectName)

        if (!obj.has(keyField) || !obj.has(ivField)) return

        val frag1B64 = obj.getString(keyField)
        val ivB64    = obj.getString(ivField)

        val frag2B64 = Regex(""""?${Regex.escape(keyFrag2Field)}"?\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.get(1) ?: return

        val token = Regex(""""?${Regex.escape(tokenField)}"?\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.get(1) ?: return

        val apiResponse = app.get("$mainUrl/api/m3u8/$token").text
        if (apiResponse.isBlank()) return

        val apiJson = try {
            JSONObject(apiResponse)
        } catch (_: Exception) { return }

        if (!apiJson.has("video_b64") || !apiJson.has("key_frag")) return

        val videoB64 = apiJson.getString("video_b64")
        val frag3B64 = apiJson.getString("key_frag")

        val aesKey = try {
            wasmDeriveKey(
                frag1 = b64Decode(frag1B64),
                frag2 = b64Decode(frag2B64),
                frag3 = b64Decode(frag3B64),
                seed  = seed
            )
        } catch (_: Exception) { return }

        val streamUrl = try {
            aesCbcDecrypt(
                key        = aesKey,
                iv         = b64Decode(ivB64),
                ciphertext = b64Decode(videoB64)
            ).trim()
        } catch (_: Exception) { return }

        if (streamUrl.isBlank()) return

        callback.invoke(
            newExtractorLink(
                name,
                referer ?: name,
                streamUrl,
                INFER_TYPE
            )
            {
                this.referer = mainUrl
                this.quality = Qualities.P1080.value
            }
        )

        val subtitlesBlock = extractArrayBlock(html, "subtitles")
        if (subtitlesBlock != null) {
            Regex("""\{[^{}]+\}""").findAll(subtitlesBlock).forEach { entry ->
                val entryStr = entry.value
                val subUrl = Regex(""""?url"?\s*:\s*"([^"]+)"""")
                    .find(entryStr)?.groupValues?.get(1)
                val lang = Regex(""""?language"?\s*:\s*"([^"]+)"""")
                    .find(entryStr)?.groupValues?.get(1) ?: "Unknown"
                if (!subUrl.isNullOrBlank()) {
                    subtitleCallback.invoke(newSubtitleFile(lang, subUrl))
                }
            }
        }
    }

    private fun extractJsonBlock(html: String, key: String): String? {
        val match = Regex(""""?${Regex.escape(key)}"?\s*:\s*(\{)""").find(html) ?: return null
        val startIdx = match.groups[1]!!.range.first
        var depth = 0
        var i = startIdx
        while (i < html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return html.substring(startIdx, i + 1) }
            }
            i++
        }
        return null
    }

    private fun extractArrayBlock(html: String, key: String): String? {
        val match = Regex(""""?${Regex.escape(key)}"?\s*:\s*(\[)""").find(html) ?: return null
        val startIdx = match.groups[1]!!.range.first
        var depth = 0
        var i = startIdx
        while (i < html.length) {
            when (html[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return html.substring(startIdx, i + 1) }
            }
            i++
        }
        return null
    }

    private fun wasmDeriveKey(
        frag1: ByteArray,
        frag2: ByteArray,
        frag3: ByteArray,
        seed:  String
    ): ByteArray {
        val seedInt = seed.take(8).toLong(16).toInt()
        val lookup  = ByteArray(512) { i -> ((i * 37 + seedInt) and 0xFF).toByte() }
        return ByteArray(frag1.size) { i ->
            val a   = frag1[i].toInt() and 0xFF
            val b   = frag2[i].toInt() and 0xFF
            val c   = frag3[i].toInt() and 0xFF
            val lut = lookup[i and 0xFF].toInt() and 0xFF
            (a xor b xor c xor lut).toByte()
        }
    }

    private fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun b64Decode(input: String): ByteArray =
        base64DecodeArray(input)
}

class Rapid : MegaPlay() {
    override val name = "Rapid"
    override val mainUrl = "https://rapid-cloud.co"
    override val requiresReferer = true
}

open class MegaPlay : ExtractorApi() {

    override val name = "MegaPlay"
    override val mainUrl = "https://megaplay.buzz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val mainHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Origin" to "https://rapid-cloud.co",
            "Referer" to "https://rapid-cloud.co/",
            "Connection" to "keep-alive",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )

        try {
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )


            val page = app.get(url, headers = headers).document

            val id = page.selectFirst("iframe.s5-embed")
                ?.attr("src")
                ?.let {
                    Regex("/stream/s-\\d+/(\\d+)/")
                        .find(it)
                        ?.groupValues
                        ?.getOrNull(1)
                }
                ?: page.selectFirst("#megaplay-player,#megaplay-player")
                    ?.attr("data-id")
                ?: return

            val apiUrl = "$mainUrl/embed-2/v2/e-1/getSources?id=$id"

            val response = app.get(apiUrl, headers = headers)
                .parsedSafe<MegaPlayResponse>()
                ?: return

            val m3u8 = response.sources?.firstOrNull()?.file ?: return

            generateM3u8(name, m3u8, mainUrl, headers = mainHeaders)
                .forEach(callback)

            response.tracks?.forEach { track ->
                if (track.kind == "captions" || track.kind == "subtitles") {
                    val file = track.file ?: return@forEach
                    val label = track.label ?: "Unknown"

                    subtitleCallback(
                        newSubtitleFile(label, file)
                    )
                }
            }

        } catch (e: Exception) {
            Log.e("MegaPlay", "Extraction failed: ${e.message}")
        }
    }

    data class MegaPlayResponse(
        val sources: List<Source>?,
        val tracks: List<Track>?,
        val encrypted: Boolean?,
        val intro: Intro?,
        val outro: Outro?,
        val server: Long?
    )

    data class Source(
        val file: String?,
        val type: String?
    )

    data class Track(
        val file: String?,
        val label: String?,
        val kind: String?,
        val default: Boolean?
    )

    data class Intro(
        val start: Long?,
        val end: Long?
    )

    data class Outro(
        val start: Long?,
        val end: Long?
    )
}

class Allanimeups : VidStack() {
    override var name = "Allanime UPS"
    override var mainUrl = "https://allanime.uns.bio"
}

class Luluvdo : StreamWishExtractor() {
    override var name = "Luluvdo"
    override val mainUrl = "https://luluvdo.com"
}


class Wootly : ExtractorApi() {
    override var name = "Wootly"
    override var mainUrl = "https://www.wootly.ch"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val iframe = app.get(url).document.select("iframe").attr("src")
        val body = FormBody.Builder()
            .add("qdfx", "1")
            .build()

        val iframeResp = app.post(iframe, requestBody = body)
        val iframeHtml = iframeResp.text
        val vdRegex = Regex("""var\s+vd\s*=\s*["']([^"']+)["']""")
        val tkRegex = Regex("""tk\s*=\s*["']([^"']+)["']""")
        val vd = vdRegex.find(iframeHtml)?.groupValues?.get(1)
        val tk = tkRegex.find(iframeHtml)?.groupValues?.get(1)

        if (vd.isNullOrBlank() || tk.isNullOrBlank()) {
            return null
        }
        val iframeurl=app.get("https://web.wootly.ch/grabm?t=$tk&id=$vd").text

        return listOf(
            newExtractorLink(
                this.name,
                this.name,
                url = iframeurl,
                type = INFER_TYPE
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P720.value
            }
        )
    }
}

class Gdshine : ExtractorApi() {
    override val name = "Gdshine"
    override val mainUrl = "https://gdshine.org"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast('/')

        val fileData = app.get("$mainUrl/api/files/s/$id")
            .parsedSafe<Response>()
            ?.data ?: return

        val workerData = app.post("$mainUrl/api/downloads/${fileData.id}/via-worker")
            .parsedSafe<Worker>()
            ?.data ?: return

        callback.invoke(
            newExtractorLink(
                name,
                "$referer",
                workerData.copyUrl
            ) {
                quality = getIndexQuality(fileData.name)
            }
        )
    }

    data class Response(
        val data: Data
    )

    data class Data(
        val id: String,
        val name: String
    )

    data class Worker(
        val data: WorkerData
    )

    data class WorkerData(
        val copyUrl: String
    )
}

class FlixCloud : ExtractorApi() {

    override val name = "FlixCloud"
    override val mainUrl = "https://flixcloud.cc"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf("Referer" to "$mainUrl/")
        val res = app.get(url, headers = headers).document

        val script = res.selectFirst("script:containsData(video_id)")
            ?.data()
            ?: return

        val start = script.indexOf("data:{")
        if (start == -1) return

        val from = script.indexOf('{', start)

        var depth = 0
        var end = -1

        for (i in from until script.length) {
            when (script[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }

        if (end == -1) return

        val rawData = script.substring(from, end + 1).toJson()
        val data = JSONObject(
            rawData.replace(
                Regex("""([{,]\s*)([A-Za-z0-9_]+)(\s*:)"""),
                "$1\"$2\"$3"
            )
        )

        data.optJSONArray("subtitles")?.let { subtitles ->
            for (i in 0 until subtitles.length()) {
                subtitles.optJSONObject(i)?.run {
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            optString("language"),
                            optString("url")
                        )
                    )
                }
            }
        }

        data.remove("subtitles")

        val body = JSONObject()
            .put("data", data)
            .toString()

        val resolvedRes = app.post(
            "https://enc-dec.app/api/dec-reanime?type=resolve",
            requestBody = body.toRequestBody(
                "application/json".toMediaType()
            ),
            timeout = 10000L
        )

        val resolvedJson = JSONObject(resolvedRes.text)

        val resolved = resolvedRes
            .parsedSafe<ResolvedReAnime>()
            ?.result ?: return

        val tokenResponse = app.get(
            "$mainUrl/api/m3u8/${resolved.token}",
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            )
        )

        val decryptBody = JSONObject()
            .put(
                "data",
                JSONObject()
                    .put(
                        "state",
                        resolvedJson
                            .getJSONObject("result")
                            .getJSONObject("state")
                    )
                    .put(
                        "token_response",
                        JSONObject(tokenResponse.text)
                    )
            ).toString()

        val decrypted = app.post(
            "https://enc-dec.app/api/dec-reanime?type=decrypt",
            requestBody = decryptBody.toRequestBody(
                "application/json".toMediaType()
            ),
            timeout = 10000L
        ).parsedSafe<ReAnimeStream>()?.result ?: return


        generateM3u8(
            name,
            decrypted.stream,
            mainUrl
        ).forEach(callback)
    }
}

class Bysekoze  : ByseSX() {
    override var name = "Bysekoze"
    override var mainUrl = "https://bysekoze.com"
}

open class ByseSX : ExtractorApi() {
    override var name = "Byse"
    override var mainUrl = "https://byse.sx"
    override val requiresReferer = true

    private fun b64UrlDecode(s: String): ByteArray {
        val fixed = s.replace('-', '+').replace('_', '/')
        val pad = (4 - fixed.length % 4) % 4
        return base64DecodeArray(fixed + "=".repeat(pad))
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    private fun getCodeFromUrl(url: String): String {
        val path = URI(url).path ?: ""
        return path.trimEnd('/').substringAfterLast('/')
    }

    private suspend fun getDetails(mainUrl: String): DetailsRoot? {
        val base = getBaseUrl(mainUrl)
        val code = getCodeFromUrl(mainUrl)
        val url = "$base/api/videos/$code/embed/details"
        return app.get(url).parsedSafe<DetailsRoot>()
    }

    private suspend fun getPlayback(mainUrl: String): PlaybackRoot? {
        val details = getDetails(mainUrl) ?: return null
        val embedFrameUrl = details.embedFrameUrl
        val embedBase = getBaseUrl(embedFrameUrl)
        val code = getCodeFromUrl(embedFrameUrl)

        val playbackUrl = "$embedBase/api/videos/$code/embed/playback"

        val headers = mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.5",
            "priority" to "u=1, i",
            "referer" to embedFrameUrl,
            "x-embed-parent" to embedFrameUrl,
        )

        val postheaders = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Content-Type" to "application/json",
            "Referer" to embedFrameUrl,
            "X-Embed-Parent" to mainUrl,
            "Priority" to "u=4",
        )

        val response = app.get(playbackUrl, headers = headers)

        return if (response.code == 200) {
            response.parsedSafe<PlaybackRoot>()
        } else {
            val json = """{
  "fingerprint": {}
}"""
            app.post(playbackUrl, headers = postheaders, requestBody = json.toRequestBody())
                .parsedSafe<PlaybackRoot>()
        }
    }

    private fun buildAesKey(playback: Playback): ByteArray {
        val p1 = b64UrlDecode(playback.keyParts[0])
        val p2 = b64UrlDecode(playback.keyParts[1])
        return p1 + p2
    }

    private fun decryptPlayback(playback: Playback): String? {
        val keyBytes = buildAesKey(playback)
        val ivBytes = b64UrlDecode(playback.iv)
        val cipherBytes = b64UrlDecode(playback.payload)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, ivBytes)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        var jsonStr = String(plainBytes, StandardCharsets.UTF_8)

        if (jsonStr.startsWith("\uFEFF")) jsonStr = jsonStr.substring(1)

        val root = try {
            tryParseJson<PlaybackDecrypt>((jsonStr))
        } catch (_: Exception) {
            return null
        }

        return root?.sources?.firstOrNull()?.url
    }


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val refererUrl = getBaseUrl(url)
        val playbackRoot = getPlayback(url) ?: return
        val streamUrl  = decryptPlayback(playbackRoot.playback) ?: return


        val headers = mapOf("Referer" to refererUrl)
        generateM3u8(
            name,
            streamUrl,
            mainUrl,
            headers = headers
        ).forEach(callback)
    }
}

data class DetailsRoot(
    val id: Long,
    val code: String,
    val title: String,
    @JsonProperty("poster_url")
    val posterUrl: String,
    val description: String,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("owner_private")
    val ownerPrivate: Boolean,
    @JsonProperty("embed_frame_url")
    val embedFrameUrl: String,
)

data class PlaybackRoot(
    val playback: Playback,
)

data class Playback(
    val algorithm: String,
    val iv: String,
    val payload: String,
    @JsonProperty("key_parts")
    val keyParts: List<String>,
    @JsonProperty("expires_at")
    val expiresAt: String,
    @JsonProperty("decrypt_keys")
    val decryptKeys: DecryptKeys,
    val iv2: String,
    val payload2: String,
)

data class DecryptKeys(
    @JsonProperty("edge_1")
    val edge1: String,
    @JsonProperty("edge_2")
    val edge2: String,
    @JsonProperty("legacy_fallback")
    val legacyFallback: String,
)

data class PlaybackDecrypt(
    val sources: List<PlaybackDecryptSource>,
)

data class PlaybackDecryptSource(
    val quality: String,
    val label: String,
    @JsonProperty("mime_type")
    val mimeType: String,
    val url: String,
    @JsonProperty("bitrate_kbps")
    val bitrateKbps: Long,
    val height: Any?,
)