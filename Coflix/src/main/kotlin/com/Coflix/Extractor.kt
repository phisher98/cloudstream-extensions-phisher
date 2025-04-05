package com.Coflix

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlin.text.Regex

open class darkibox : ExtractorApi() {
    override var name = "Darkibox"
    override var mainUrl = "https://darkibox.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val response = app.get(url,referer=mainUrl).toString()
            Regex("""sources:\s*\[\{src:\s*"(.*?)"""").find(response)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer ?: ""
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        return null
    }
}

open class Videzz : ExtractorApi() {
    override var name = "Videzz"
    override var mainUrl = "https://videzz.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val mp4 = app.get(url,referer=mainUrl).document.select("#vplayer > #player source").attr("src")
            return listOf(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = mp4,
                    type = INFER_TYPE
                ) {
                    this.referer = referer ?: ""
                    this.quality = Qualities.P1080.value
                }
            )
    }
}

class VidHideplus : VidhideExtractor() {
    override var mainUrl = "https://vidhideplus.com"
}


class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}