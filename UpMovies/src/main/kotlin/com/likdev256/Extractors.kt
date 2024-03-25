package com.likdev256

import android.annotation.SuppressLint
import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI
import com.lagradost.cloudstream3.utils.JsUnpacker


open class EPlayExtractor : ExtractorApi() {
    override var name = "EPlay"
    override var mainUrl = "https://eplayvid.net/"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url).document
        val trueUrl = response.select("source").attr("src")
        return listOf(
                ExtractorLink(
                        this.name,
                        this.name,
                        trueUrl,
                        mainUrl,
                        getQualityFromName(""), // this needs to be auto
                        false
                )
        )
    }
}
open class DoodmainExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://d000d.com"
    override val requiresReferer = true

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/d/$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response0 = app.get(url).text // html of DoodStream page to look for /pass_md5/...
        val md5 =mainUrl+(Regex("/pass_md5/[^']*").find(response0)?.value ?: return null)  // get https://dood.ws/pass_md5/...
        val trueUrl = app.get(md5, referer = url).text + "zUEJeL3mUN?token=" + md5.substringAfterLast("/")   //direct link to extract  (zUEJeL3mUN is random)
        val quality = Regex("\\d{3,4}p").find(response0.substringAfter("<title>").substringBefore("</title>"))?.groupValues?.get(0)
        return listOf(
            ExtractorLink(
                this.name,
                this.name,
                trueUrl,
                mainUrl,
                getQualityFromName(quality),
                false
            )
        ) // links are valid in 8h

    }
}

open class DoodReExtractor : DoodLaExtractor() {
    override var mainUrl = "https://d000d.com"

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response0 =
            app.get(url.replace("doodstream", "d000d"))
                .text // html of DoodStream page to look for /pass_md5/...
        val md5 =
            mainUrl +
                    (Regex("/pass_md5/[^']*").find(response0)?.value
                        ?: return null) // get https://dood.ws/pass_md5/...
        val trueUrl =
            app.get(md5, referer = url).text +
                    "zUEJeL3mUN?token=" +
                    md5.substringAfterLast(
                        "/"
                    ) // direct link to extract  (zUEJeL3mUN is random)
        val quality =
            Regex("\\d{3,4}p")
                .find(response0.substringAfter("<title>").substringBefore("</title>"))
                ?.groupValues
                ?.get(0)
        return listOf(
            ExtractorLink(
                this.name,
                this.name,
                trueUrl,
                mainUrl,
                getQualityFromName(quality),
                false
            )
        ) // links are valid in 8h
    }
}

open class vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https:///vtbe.to/"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=referer).document
        //println(response)
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        //val unpacked= getAndUnpack(extractedpack)
        println(extractedpack)
            JsUnpacker(extractedpack).unpack()?.let { unPacked ->
                Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                    return listOf(
                        ExtractorLink(
                            this.name,
                            this.name,
                            link,
                            referer ?: "",
                            Qualities.Unknown.value,
                            URI(link).path.endsWith(".m3u8")
                        )
                    )
                }
            }
            return null
    }
}


open class Filemoon : ExtractorApi() {
    override val name = "Filemoon"
    override val mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val replaceurl=url.replace("sx", "to")
        val response =app.get(replaceurl).document
        //val response = app.get(url, referer = referer)
        val script = response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        JsUnpacker(script).unpack()?.let { unPacked ->
            val Quality=Regex("""qualityLabels'\\s*:\\s*\{\\s*"\\d+"\\s*:\\s*"([^"]+)""").find(unPacked)?.groupValues?.get(1)
            Regex("file:\\s*\"(.*?m3u8.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        referer ?: "",
                        getQualityFromName(Quality),
                        URI(link).path.endsWith(".m3u8")
                    )
                )
            }
        }
        return null
    }
}

open class StreamWishExtractor : ExtractorApi() {
    override var name = "StreamWish"
    override var mainUrl = "https://streamwish.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(
            url, referer = referer ?: "$mainUrl/", interceptor = WebViewResolver(
                Regex("""master\.m3u8""")
            )
        )
        if (response.url.contains("m3u8"))
            return listOf(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = response.url,
                    referer = referer ?: "$mainUrl/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        return null
    }
}

open class Filelion : ExtractorApi() {
    override val name = "Filelion"
    override val mainUrl = "https://filelions.to"
    override val requiresReferer = false

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val response =app.get(url).document
        //val response = app.get(url, referer = referer)
        val script = response.selectFirst("script:containsData(sources)")?.data().toString()
        Log.d("Test9871",script)
            Regex("sources:.\\[.file:\"(.*)\".*").find(script)?.groupValues?.get(1)?.let { link ->
                Log.d("Test9876",link)
                if (link.contains("m3u8"))
                    return listOf(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = link,
                            referer = referer ?: "$mainUrl/",
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
            }
        return null
    }
}





