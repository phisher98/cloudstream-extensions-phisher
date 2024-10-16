package com.darkdemon

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

open class Sendcm: ExtractorApi() {
    override val name = "Sendcm"
    override val mainUrl = "https://send.cm"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val url = app.get(url).document.select("source").attr("src")
        sources.add(
            ExtractorLink(
                name = name,
                source = name,
                url = url,
                isM3u8 = false,
                quality = Qualities.Unknown.value,
                referer = url
            )
        )
        return sources
    }
}