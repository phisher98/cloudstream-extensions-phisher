package com.IStreamFlare

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink


class Istreamcdn : ExtractorApi() {
    override val name = "IStreamCDN"
    override val mainUrl = "https://istreamcdn.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val quality = referer?.substringBefore("+")
        val src=app.get(url, allowRedirects = false).headers["location"]
        if (src!=null)
        {
            callback.invoke(
                newExtractorLink(
                    name = name,
                    source = name,
                    url = src,
                    type = ExtractorLinkType.DASH
                ) {
                    this.referer = ""
                    this.quality = getQualityFromName(quality)
                }
            )
        }
    }
}
