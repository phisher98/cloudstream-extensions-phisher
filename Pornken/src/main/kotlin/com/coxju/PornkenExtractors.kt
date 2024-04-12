package com.coxju

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

// class D0000dExtractor : DoodLaExtractor() {
//     override var mainUrl = "https://d0000d.com"
// }

class D0000dExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://d0000d.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // html of DoodStream page to look for /pass_md5/...
        val response0 = app.get(url).text

        // get https://dood.ws/pass_md5/...
        val md5 = mainUrl + (Regex("/pass_md5/[^']*").find(response0)?.value ?: return null)
        val res = app.get(md5, referer = mainUrl + "/e/" + url.substringAfterLast("/"))

        // (zUEJeL3mUN is random)
        val trueUrl =
            if (res.toString().contains("cloudflarestorage")) res.toString()
            else res.text + "zUEJeL3mUN?token=" + md5.substringAfterLast("/")

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
        ) // links are valid for 8h
    }
}