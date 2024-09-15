package com.HindiProviders

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.JsUnpacker
import java.util.Base64

class FMHD : Filesim() {
    override val name = "FMHD"
    override var mainUrl = "https://fmhd.bar/"
    override val requiresReferer = true
}

class VidSrcExtractorio : VidSrcExtractor() {
    override val mainUrl = "https://vidsrc.me"
}

class VidSrcExtractorcc : VidSrcExtractor() {
    override val mainUrl = "https://vidsrc.cc"
}

class Playonion : Filesim() {
    override val mainUrl = "https://playonion.sbs"
}


class onionhd : VidSrcExtractor() {
    override val mainUrl = "https://onionhd.buzz"
}


class Luluvdo : StreamWishExtractor() {
    override val mainUrl = "https://luluvdo.com"
}

class Lulust : StreamWishExtractor() {
    override val mainUrl = "https://lulu.st"
    }

open class FMX : ExtractorApi() {
    override var name = "FMX"
    override var mainUrl = "https://fmx.lol"
    override val requiresReferer = true

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
            JsUnpacker(extractedpack).unpack()?.let { unPacked ->
                Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                    return listOf(
                        ExtractorLink(
                            this.name,
                            this.name,
                            link,
                            referer ?: "",
                            Qualities.Unknown.value,
                            type = INFER_TYPE
                        )
                    )
                }
            }
            return null
    }
}

open class Akamaicdn : ExtractorApi() {
    override val name = "Akamaicdn"
    override val mainUrl = "https://akamaicdn.life"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val mappers = res.selectFirst("script:containsData(sniff\\()")?.data()?.substringAfter("sniff(")
            ?.substringBefore(");") ?: return
        val ids = mappers.split(",").map { it.replace("\"", "") }
        Log.d("Phisher",url)
        val header= mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0","Accept" to "*/*","Referer" to url)
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                "$mainUrl/m3u8/${ids[1]}/${ids[2]}/master.txt?s=1&cache=1",
                url,
                Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8,
                headers = header
            )
        )
    }
}

open class VidSrcExtractor : ExtractorApi() {
    override val name = "VidSrc"
    override val mainUrl = "https://vidsrc.net"
    open val apiUrl = "https://flickersky.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframedoc = app.get(url).document

        val srcrcpList =
            iframedoc.select("div.serversList > div.server").mapNotNull {
                val datahash = it.attr("data-hash") ?: return@mapNotNull null
                val rcpLink = "$apiUrl/rcp/$datahash"
                val rcpRes = app.get(rcpLink, referer = apiUrl).text
                val srcrcpLink =
                    Regex("src:\\s*'(.*)',").find(rcpRes)?.destructured?.component1()
                        ?: return@mapNotNull null
                "https:$srcrcpLink"
            }

        srcrcpList.amap { server ->
            val res = app.get(server, referer = apiUrl)
            if (res.url.contains("/prorcp")) {
                val encodedElement = res.document.select("div#reporting_content+div")
                val decodedUrl =
                    decodeUrl(encodedElement.attr("id"), encodedElement.text()) ?: return@amap

                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        decodedUrl,
                        apiUrl,
                        Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            } else {
                loadExtractor(res.url, url, subtitleCallback, callback)
            }
        }
    }

    private fun decodeUrl(encType: String, url: String): String? {
        return when (encType) {
            "NdonQLf1Tzyx7bMG" -> bMGyx71TzQLfdonN(url)
            "sXnL9MQIry" -> Iry9MQXnLs(url)
            "IhWrImMIGL" -> IGLImMhWrI(url)
            "xTyBxQyGTA" -> GTAxQyTyBx(url)
            "ux8qjPHC66" -> C66jPHx8qu(url)
            "eSfH1IRMyL" -> MyL1IRSfHe(url)
            "KJHidj7det" -> detdj7JHiK(url)
            "o2VSUnjnZl" -> nZlUnj2VSo(url)
            "Oi3v1dAlaM" -> laM1dAi3vO(url)
            "TsA2KGDGux" -> GuxKGDsA2T(url)
            "JoAHUMCLXV" -> LXVUMCoAHJ(url)
            else -> null
        }
    }

    private fun bMGyx71TzQLfdonN(a: String): String {
        val b = 3
        val c = mutableListOf<String>()
        var d = 0
        while (d < a.length) {
            c.add(a.substring(d, minOf(d + b, a.length)))
            d += b
        }
        val e = c.reversed().joinToString("")
        return e
    }

    @SuppressLint("NewApi")
    private fun Iry9MQXnLs(a: String): String {
        val b = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val d = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var c = ""
        for (e in d.indices) {
            c += (d[e].code xor b[e % b.length].code).toChar()
        }
        var e = ""
        for (ch in c) {
            e += (ch.code - 3).toChar()
        }
        return String(Base64.getDecoder().decode(e))
    }

    @SuppressLint("NewApi")
    private fun IGLImMhWrI(a: String): String {
        val b = a.reversed()
        val c =
            b
                .map {
                    when (it) {
                        in 'a'..'m', in 'A'..'M' -> it + 13
                        in 'n'..'z', in 'N'..'Z' -> it - 13
                        else -> it
                    }
                }
                .joinToString("")
        val d = c.reversed()
        return String(Base64.getDecoder().decode(d))
    }

    private fun GTAxQyTyBx(a: String): String {
        val b = a.reversed()
        val c = b.filterIndexed { index, _ -> index % 2 == 0 }
        return String(Base64.getDecoder().decode(c))
    }

    private fun C66jPHx8qu(a: String): String {
        val b = a.reversed()
        val c = "X9a(O;FMV2-7VO5x;Ao:dN1NoFs?j,"
        val d = b.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var e = ""
        for (i in d.indices) {
            e += (d[i].code xor c[i % c.length].code).toChar()
        }
        return e
    }

    private fun MyL1IRSfHe(a: String): String {
        val b = a.reversed()
        val c = b.map { (it.code - 1).toChar() }.joinToString("")
        val d = c.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return d
    }

    private fun detdj7JHiK(a: String): String {
        val b = a.substring(10, a.length - 16)
        val c = "3SAY~#%Y(V%>5d/Yg\"\$G[Lh1rK4a;7ok"
        val d = String(Base64.getDecoder().decode(b))
        val e = c.repeat((d.length + c.length - 1) / c.length).substring(0, d.length)
        var f = ""
        for (i in d.indices) {
            f += (d[i].code xor e[i].code).toChar()
        }
        return f
    }

    private fun nZlUnj2VSo(a: String): String {
        val b =
            mapOf(
                'x' to 'a',
                'y' to 'b',
                'z' to 'c',
                'a' to 'd',
                'b' to 'e',
                'c' to 'f',
                'd' to 'g',
                'e' to 'h',
                'f' to 'i',
                'g' to 'j',
                'h' to 'k',
                'i' to 'l',
                'j' to 'm',
                'k' to 'n',
                'l' to 'o',
                'm' to 'p',
                'n' to 'q',
                'o' to 'r',
                'p' to 's',
                'q' to 't',
                'r' to 'u',
                's' to 'v',
                't' to 'w',
                'u' to 'x',
                'v' to 'y',
                'w' to 'z',
                'X' to 'A',
                'Y' to 'B',
                'Z' to 'C',
                'A' to 'D',
                'B' to 'E',
                'C' to 'F',
                'D' to 'G',
                'E' to 'H',
                'F' to 'I',
                'G' to 'J',
                'H' to 'K',
                'I' to 'L',
                'J' to 'M',
                'K' to 'N',
                'L' to 'O',
                'M' to 'P',
                'N' to 'Q',
                'O' to 'R',
                'P' to 'S',
                'Q' to 'T',
                'R' to 'U',
                'S' to 'V',
                'T' to 'W',
                'U' to 'X',
                'V' to 'Y',
                'W' to 'Z'
            )
        return a.map { b[it] ?: it }.joinToString("")
    }

    private fun laM1dAi3vO(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.getDecoder().decode(c))
        var e = ""
        val f = 5
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    private fun GuxKGDsA2T(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.getDecoder().decode(c))
        var e = ""
        val f = 7
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    private fun LXVUMCoAHJ(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.getDecoder().decode(c))
        var e = ""
        val f = 3
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }
}