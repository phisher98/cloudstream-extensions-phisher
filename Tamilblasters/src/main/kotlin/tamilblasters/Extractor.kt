package com.tamilblasters

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class Streamhg: VidHidePro(){
    override var mainUrl = "https://tryzendm.com"
}

class Hgcloud: StreamHG(){
    override var mainUrl = "https://hgcloud.to"
}

open class StreamHG : ExtractorApi() {
    override val name = "StreamHG"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )

        val resolver = WebViewResolver(
            interceptUrl = Regex("""(m3u8|master\.txt)"""),
            additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
            useOkhttp = false,
            timeout = 15_000L
        )

        val interceptedUrl = app.get(
            url,
            referer = referer,
            interceptor = resolver,
            headers = headers
        ).url

        generateM3u8(
            name,
            interceptedUrl,
            referer = "$mainUrl/",)
        .forEach(callback)
    }

}