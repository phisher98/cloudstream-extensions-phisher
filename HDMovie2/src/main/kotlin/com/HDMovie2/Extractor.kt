package com.HDMovie2

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

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





