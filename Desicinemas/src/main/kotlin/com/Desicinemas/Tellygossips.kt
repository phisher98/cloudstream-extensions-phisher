package com.Desicinemas

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE


class Tellygossips : ExtractorApi() {
override val mainUrl = "https://tellygossips.net"
override val name = "Tellygossips"
override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = this.referer).document
        val iframe=doc.selectFirst("div.video-player > iframe")?.attr("src")
        val iframetext=app.get(iframe).text()
        val source = Regex(""""src":"(.*)","label""").find(iframetext)?.groupValues?.get(1)
        callback.invoke(
            ExtractorLink(
                name,
                name,
                url = source ?: return,
                referer = "$mainUrl/",
                quality = Qualities.Unknown.value,
				INFER_TYPE,
                headers=headers
            )
        )
    }
}