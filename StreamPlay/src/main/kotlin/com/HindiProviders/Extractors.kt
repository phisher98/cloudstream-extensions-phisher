package com.HindiProviders

//import android.util.Log
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.GMPlayer
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.Voe
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.PixelDrain
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.*
import java.math.BigInteger
import java.security.MessageDigest
import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import okhttp3.FormBody
import org.json.JSONObject

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
            ExtractorLink(
                this.name,
                this.name,
                source?.data ?: return,
                "$mainUrl/",
                Qualities.P1080.value,
                INFER_TYPE
            )
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
            ExtractorLink(
                this.name,
                this.name,
                video ?: return,
                referer ?: "",
                Qualities.P720.value,
                INFER_TYPE
            )
        )

    }

    private fun String.findIn(data: String): String? {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1)
    }

    data class Source(
        @JsonProperty("data") val data: String? = null,
    )

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
        var url=url
        if (url.contains("api/index.php"))
        {
            url=app.get(url).document.selectFirst("div.main h4 a")?.attr("href") ?:""
        }
        val doc = app.get(url).document
        val scriptTag = doc.selectFirst("script:containsData(url)")?.toString() ?:""
        val urlValue = Regex("var url = '([^']*)'").find(scriptTag) ?. groupValues ?. get(1) ?: ""
        val document = app.get(urlValue).document

        val size = document.selectFirst("i#size") ?. text()
        val div = document.selectFirst("div.card-body")
        val header = document.selectFirst("div.card-header") ?. text()
        div?.select("a")?.apmap {
            val link = it.attr("href")
            Log.d("Phisher V",link)
            if (link.contains("pixeldra")) {
                callback.invoke(
                    ExtractorLink(
                        "Pixeldrain",
                        "Pixeldrain $size",
                        link,
                        "",
                        getIndexQuality(header),
                    )
                )
            } else if(link.contains("dl.php")) {
                val response = app.get(link, allowRedirects = false)
                val downloadLink = response.headers["location"].toString().split("link=").getOrNull(1) ?: link
                callback.invoke(
                    ExtractorLink(
                        "V-Cloud[Download]",
                        "V-Cloud[Download] $size",
                        downloadLink,
                        "",
                        getIndexQuality(header),
                    )
                )
            } else if(link.contains(".dev")) {
                callback.invoke(
                    ExtractorLink(
                        "V-Cloud",
                        "V-Cloud $size",
                        link,
                        "",
                        getIndexQuality(header),
                    )
                )
            } else if (link.contains(".hubcdn.xyz"))
            {
                callback.invoke(
                    ExtractorLink(
                        "V-Cloud",
                        "V-Cloud $size",
                        link,
                        "",
                        getIndexQuality(header),
                    )
                )
            }
            else{
                loadExtractor(link, subtitleCallback, callback)
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
            ExtractorLink(
                this.name,
                this.name,
                video,
                "",
                Qualities.Unknown.value,
                INFER_TYPE
            )
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
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?: return,
                mainUrl,
                quality?.toIntOrNull() ?: defaulQuality,
                INFER_TYPE
            )
        )
    }

}

open class Gdmirrorbot : ExtractorApi() {
    override val name = "Gdmirrorbot"
    override val mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url, referer = referer).document.select("ul#videoLinks li").apmap {
            loadExtractor(it.attr("data-link"), "$mainUrl/", subtitleCallback, callback)
        }
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
            ExtractorLink(
                this.name,
                this.name,
                video,
                "$mainUrl/",
                getIndexQuality(title),
                INFER_TYPE
            )
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

class do0od : DoodLaExtractor() {
    override var mainUrl = "https://do0od.com"
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

class Flaswish : Ridoo() {
    override val name = "Flaswish"
    override var mainUrl = "https://flaswish.com"
    override val defaulQuality = Qualities.Unknown.value
}

class Comedyshow : Jeniusplay() {
    override val mainUrl = "https://comedyshow.to"
    override val name = "Comedyshow"
}
class Bestx : Chillx() {
    override val name = "Bestx"
    override val mainUrl = "https://bestx.stream"
}

class Vectorx : Chillx() {
    override val name = "Vectorx"
    override val mainUrl = "https://vectorx.top"
}


class Graceaddresscommunity : Voe() {
    override var mainUrl = "https://graceaddresscommunity.com"
}

class Sethniceletter : Voe() {
    override var mainUrl = "https://sethniceletter.com"
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
            ExtractorLink(
                name,
                name,
                link,
                url,
                Qualities.Unknown.value,
                type = INFER_TYPE
            )
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
            ExtractorLink(
                name,
                name,
                meta.videoSource,
                url,
                Qualities.Unknown.value,
                meta.hls
            )
        )
    }

    data class MetaData(
        val hls: Boolean,
        val videoSource: String
    )

}

open class Mdrive : ExtractorApi() {
    override val name: String = "Mdrive"
    override val mainUrl: String = "https://gamerxyt.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host=url.substringAfter("?").substringBefore("&")
        val id=url.substringAfter("id=").substringBefore("&")
        val token=url.substringAfter("token=").substringBefore("&")
        val Cookie="$host; hostid=$id; hosttoken=$token"
        val doc = app.get("$mainUrl/games/",headers = mapOf("Cookie" to Cookie)).document
        val links = doc.select("div.card-body > h2 > a").attr("href")
        val header = doc.selectFirst("div.card-header")?.text()
        if (links.contains("pixeldrain"))
        {
            callback.invoke(
                ExtractorLink(
                    "MovieDrive",
                    "PixelDrain",
                    links,
                    referer = links,
                    quality = getIndexQuality(header),
                    type = INFER_TYPE
                )
            )
        }else
        if (links.contains("gofile")) {
            loadExtractor(links, subtitleCallback, callback)
        }
        else {
            callback.invoke(
                ExtractorLink(
                    "MovieDrive",
                    "MovieDrive",
                    links,
                    referer = "",
                    quality = getIndexQuality(header),
                    type = INFER_TYPE
                )
            )
        }
    }
}

suspend fun Unblockedlinks(url: String): String {
    val driveLink = bypassHrefli(url) ?:""
    return driveLink
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
            ExtractorLink(
                name,
                name,
                url = link,
                "",
                getQualityFromName(quality)
            )
        )
    }
}



open class Asianbxkiun : ExtractorApi() {
    override val name: String = "Asianbxkiun"
    override val mainUrl: String = "https://asianbxkiun.pro"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
       app.get(url).document.select("#list-server-more ul li").map {
           val link=it.attr("data-video")
           loadExtractor(link,subtitleCallback, callback)
       }
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
    override val mainUrl: String = "https://new4.gdflix.cfd"
    override val requiresReferer = false

    private suspend fun extractbollytag(url:String): String {
        val tagdoc= app.get(url).text
        val tags ="""\b\d{3,4}p\b""".toRegex().find(tagdoc) ?. value ?. trim() ?:""
        return tags
    }

    private suspend fun extractbollytag2(url:String): String {
        val tagdoc= app.get(url).text
        val tags ="""\b\d{3,4}p\b\s(.*?)\[""".toRegex().find(tagdoc) ?. groupValues ?. get(1) ?. trim() ?:""
        return tags
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var originalUrl = url
        val tags = extractbollytag(originalUrl)
        val tagquality = extractbollytag2(originalUrl)

        if (originalUrl.startsWith("https://new2.gdflix.cfd/goto/token/")) {
            val partialurl = app.get(originalUrl).text.substringAfter("replace(\"").substringBefore("\")")
            originalUrl = mainUrl + partialurl
        }
        app.get(originalUrl).document.select("div.text-center a").map {
            if (it.select("a").text().contains("FAST CLOUD DL"))
            {
                val link=it.attr("href")
                val trueurl=app.get("https://new2.gdflix.cfd$link", timeout = 30L).document.selectFirst("a.btn-success")?.attr("href") ?:""
                callback.invoke(
                    ExtractorLink(
                        "$source GDFlix[Fast Cloud]",
                        "$source GDFLix[Fast Cloud] $tagquality",
                        trueurl,
                        "",
                        getQualityFromName(tags)
                    )
                )
            }
            else if (it.select("a").text().contains("DRIVEBOT LINK"))
            {
                val driveLink = it.attr("href")
                val id = driveLink.substringAfter("id=").substringBefore("&")
                val doId = driveLink.substringAfter("do=").substringBefore("==")
                val indexbotlink = "https://indexbot.lol/download?id=${id}&do=${doId}"
                val indexbotresponse = app.get(indexbotlink, timeout = 30L)
                if(indexbotresponse.isSuccessful) {
                    val cookiesSSID = indexbotresponse.cookies["PHPSESSID"]
                    val indexbotDoc = indexbotresponse.document
                    val token = Regex("""formData\.append\('token', '([a-f0-9]+)'\)""").find(indexbotDoc.toString()) ?. groupValues ?. get(1) ?: "token"
                    val postId = Regex("""fetch\('/download\?id=([a-zA-Z0-9/+]+)'""").find(indexbotDoc.toString()) ?. groupValues ?. get(1) ?: "postId"

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
                        "https://indexbot.lol/download?id=${postId}",
                        requestBody = requestBody,
                        headers = headers,
                        cookies = cookies,
                        timeout = 30L
                    ).toString()

                    var downloadlink = Regex("url\":\"(.*?)\"").find(response) ?. groupValues ?. get(1) ?: ""

                    downloadlink = downloadlink.replace("\\", "")

                    callback.invoke(
                        ExtractorLink(
                            "$source GDFlix[IndexBot]",
                            "$source GDFlix[IndexBot] $tagquality",
                            downloadlink,
                            "https://indexbot.lol/",
                            getQualityFromName(tags)
                        )
                    )
                }
            }
            else if (it.select("a").text().contains("Instant DL"))
            {
                val Instant_link=it.attr("href")
                val link =app.get(Instant_link, allowRedirects = false).headers["Location"]?.split("url=")?.getOrNull(1) ?: ""
                callback.invoke(
                    ExtractorLink(
                        "$source GDFlix[Instant Download]",
                        "$source GDFlix[Instant Download] $tagquality",
                        url = link,
                        "",
                        getQualityFromName(tags)
                    )
                )
            }
        }
    }
}

class HubCloudClub : HubCloud() {
    override var mainUrl = "https://hubcloud.club"
}

class HubCloudlol : HubCloud() {
    override var mainUrl = "https://hubcloud.lol"
}

class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        if (mId.isNullOrEmpty())
        {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    url,
                    url,
                    Qualities.Unknown.value,
                )
            )
        }
        else {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    "$mainUrl/api/file/${mId}?download",
                    url,
                    Qualities.Unknown.value,
                )
            )
        }
    }
}

open class HubCloud : ExtractorApi() {
    override val name: String = "Hub-Cloud"
    override val mainUrl: String = "https://hubcloud.art"
    override val requiresReferer = false

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).text
        val newLink = doc.substringAfter("url=").substringBefore("\"")
        val newDoc = app.get(newLink).document
        val gamerLink: String

        if(newLink.contains("drive")) {
            val scriptTag = newDoc.selectFirst("script:containsData(url)")!!.toString()
            gamerLink = Regex("var url = '([^']*)'").find(scriptTag) ?. groupValues ?. get(1) ?: ""
        }

        else {
            gamerLink = newDoc.selectFirst("div.vd > center > a") ?. attr("href") ?: ""
        }

        val document = app.get(gamerLink).document

        val size = document.selectFirst("i#size") ?. text()
        val div = document.selectFirst("div.card-body")
        val header = document.selectFirst("div.card-header")?.text()
        div?.select("a")?.apmap {
            val link = it.attr("href")
            val text = it.text()
            if (link.contains("pixeldra")) {
                callback.invoke(
                    ExtractorLink(
                        "$source Pixeldrain",
                        "$source Pixeldrain $size",
                        link,
                        "",
                        getIndexQuality(header),
                    )
                )
            }
            else if(text.contains("Download [Server : 10Gbps]")) {
                val response = app.get(link, allowRedirects = false)
                val downloadLink = response.headers["location"].toString().split("link=").getOrNull(1) ?: link
                callback.invoke(
                    ExtractorLink(
                        "$source Hub-Cloud[Download]",
                        "$source Hub-Cloud[Download] $size",
                        downloadLink,
                        "",
                        getIndexQuality(header),
                    )
                )
            }
            else if(link.contains(".dev")) {
                callback.invoke(
                    ExtractorLink(
                        "$source Hub-Cloud",
                        "$source Hub-Cloud $size",
                        link,
                        "",
                        getIndexQuality(header),
                    )
                )
            }
            else {
                loadExtractor(link, referer = "$source", subtitleCallback, callback)
            }
        }
    }


    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
            ?: Qualities.P2160.value
    }

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
        return Regex("(\\d{3,4}[pP](?:[^.]*\\.){5}[^.]+)").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun getNameQuality(str: String?): String {
        val tag=Regex("(\\d{3,4}[pP](?:[^.]*\\.){3}[^.]+)").find(str ?: "")?.groupValues ?.get(1)
            ?: ""
        Log.d("Phisher tag",tag)
        return tag
    }


    private suspend fun CFType1(url: String): List<String> {
        val cfWorkersLink = url.replace("/file", "/wfile") + "?type=1"
        val document = app.get(cfWorkersLink).document
        val links = document.select("a.btn-success").map { it.attr("href") }
        return links
    }

    private suspend fun CFType2(url: String): List<String> {
        val cfWorkersLink = url.replace("/file", "/wfile") + "?type=2"
        val document = app.get(cfWorkersLink).document
        val links = document.select("a.btn-success").map { it.attr("href") }
        return links
    }

    private suspend fun resumeCloudLink(url: String): String? {
        val resumeCloudUrl = mainUrl + url
        val document = app.get(resumeCloudUrl).document
        val link = document.selectFirst("a.btn-success")?.attr("href")
        return link
    }


    private suspend fun resumeBot(url : String): String? {
        val resumeBotResponse = app.get(url)
        val resumeBotDoc = resumeBotResponse.document.toString()
        val ssid = resumeBotResponse.cookies["PHPSESSID"]
        val resumeBotToken = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(resumeBotDoc)?.groups?.get(1)?.value
        val resumeBotPath = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/+]+)'").find(resumeBotDoc)?.groups?.get(1)?.value
        val resumeBotBaseUrl = url.split("/download")[0]
        val requestBody = FormBody.Builder()
            .addEncoded("token", "$resumeBotToken")
            .build()

        val jsonResponse = app.post(resumeBotBaseUrl + "/download?id=" + resumeBotPath,
            requestBody = requestBody,
            headers = mapOf(
                "Accept" to "*/*",
                "Origin" to resumeBotBaseUrl,
                "Sec-Fetch-Site" to "same-origin"
            ),
            cookies = mapOf("PHPSESSID" to "$ssid"),
            referer = url
        ).text
        val jsonObject = JSONObject(jsonResponse)
        val link = jsonObject.getString("url")
        return link
    }

    private suspend fun instantLink(finallink: String): String {
        val url = if(finallink.contains("video-leech")) "video-leech.xyz" else "video-seed.xyz"
        val token = finallink.substringAfter("https://$url/?url=")
        val downloadlink = app.post(
            url = "https://$url/api",
            data = mapOf(
                "keys" to token
            ),
            referer = finallink,
            headers = mapOf(
                "x-token" to url,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            )
        )
        val finaldownloadlink =
            downloadlink.toString().substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")

        return finaldownloadlink
    }


    @Suppress("SENSELESS_COMPARISON", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        val qualityText = document.selectFirst("li.list-group-item:contains(Name)")?.text()
        val quality = getIndexQuality(qualityText)
        val qualityname=getNameQuality(qualityText).replace("."," ")

        document.select("a.btn").amap {
            val text = it.text()
            //Log.d("Phisher text",text)
            val link = it.attr("href")
            //Log.d("Phisher link",link)
            if(text.contains("Resume Cloud")) {
                val streamUrl = resumeCloudLink(link)
                Log.d("Phisher streamUrl", streamUrl.toString())
                if (streamUrl != null) {
                    callback.invoke(
                        ExtractorLink(
                            "$source ResumeCloud $qualityname",
                            "$source ResumeCloud $qualityname",
                            httpsify(streamUrl.toString()),
                            "",
                            quality
                        )
                    )
                }
            }
            else if(text.contains("Instant Download")) {
                val streamUrl = instantLink(link)
                if (streamUrl.isNotEmpty()) {
                    callback.invoke(
                        ExtractorLink(
                            "$source Instant(Download) $qualityname",
                            "$source Instant(Download) $qualityname",
                            httpsify(streamUrl),
                            "",
                            quality
                        )
                    )
                }
            }
            else if(text.contains("Resume Worker Bot")) {
                val streamUrl = resumeBot(link)
                if (streamUrl != null) {
                    callback.invoke(
                        ExtractorLink(
                            "$source ResumeBot(VLC) $qualityname",
                            "$source ResumeBot(VLC) $qualityname",
                            streamUrl.toString(),
                            "",
                            quality
                        )
                    )
                }
            }
            else if(text.contains("Direct Links")) {
                val cfType1 = CFType1(url)
                val cfType2 = CFType2(url)
                if (cfType1.isNotEmpty()) {
                    cfType1.forEach { href ->
                        callback.invoke(
                            ExtractorLink(
                                "$source CF Type1 $qualityname",
                                "$source CF Type1 $qualityname",
                                httpsify(href),
                                "",
                                quality
                            )
                        )
                    }
                }
                if (cfType2.isNotEmpty()) {
                    cfType2.forEach { href ->
                        callback.invoke(
                            ExtractorLink(
                                "$source CF Type2 $qualityname",
                                "$source CF Type2 $qualityname",
                                httpsify(href),
                                "",
                                quality
                            )
                        )
                    }
                }
            }
            else {
                //Nothing
            }
        }

    }
}

