package com.Desicinemas

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.*


open class Tellygossips : ExtractorApi() {
    override val name = "Tellygossips"
    override val mainUrl = "https://tellygossips.net"
    override val requiresReferer = true

    @Suppress("NAME_SHADOWING")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc=app.get(url,referer=$mainUrl).document
        val iframeembed=doc.selectFirst("div.video-player > iframe")!!.attr("src") ?:""
        val iframe=app.get(iframeembed)?.text
            Regex(""""src":"(.*)","label""").find(iframe)?.groupValues?.get(1)?.let { link ->
                    ExtractorLink(
                        "Tellygossips",
                        "Tellygossips",
                        link,
                        referer ?: "",
                        getQualityFromName(""),
                        type = INFER_TYPE
                )
            }
        return null
    }
}