package com.Toonstream


import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class StreamSB8 : StreamSB() {
    override var mainUrl = "https://streamsb.net"
}

class Cloudy : VidStack() {
    override var mainUrl = "https://cloudy.upns.one"
}

open class Streamruby : ExtractorApi() {
    override var name = "Streamruby"
    override var mainUrl = "streamruby.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val newUrl = if (url.contains("/e/")) url.replace("/e", "") else url
        val txt = app.get(newUrl).text
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1)

        return m3u8?.takeIf { it.isNotEmpty() }?.let {
            listOf(
                newExtractorLink(
                    name = this.name,
                    source = this.name,
                    url = it,
                    type = INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}


class Cdnwish : StreamWishExtractor() {
    override var mainUrl = "https://cdnwish.com"
}

class vidhidevip : VidhideExtractor() {
    override var mainUrl = "https://vidhidevip.com"
}

class D000d : DoodLaExtractor() {
    override var mainUrl = "https://d000d.com"
}


class FileMoonnl : Filesim() {
    override val mainUrl = "https://filemoon.nl"
    override val name = "FileMoon"
}
