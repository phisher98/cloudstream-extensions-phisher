package com.likdev256

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName

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
                        true
                )
        )
    }
}

class DoodReExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.re"

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response0 =
                app.get(url.replace("watch", "la"))
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
        val unpacked= getAndUnpack(extractedpack)
        println(unpacked)
        val regexPattern = """file:"(.*?)".*qualityLabels':\{"\d+":"(\w+)"}.*""".toRegex()
        val matchResult = regexPattern.find(unpacked)
        val file: String?
        val quality: String?
        file = matchResult!!.groupValues[1]
        quality = matchResult.groupValues[2]

        println(file)
        println(quality)
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    "$file",
                    mainUrl,
                    getQualityFromName(quality),
                    false
                )
            )
    }
}



