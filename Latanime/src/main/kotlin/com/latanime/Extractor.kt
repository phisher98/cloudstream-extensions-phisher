package com.latanime

import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Zilla : ExtractorApi() {
    override var name = "HLS"
    override var mainUrl = "https://player.zilla-networks.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val mp4 = "$mainUrl/m3u8/${url.substringAfterLast("/")}"
            return listOf(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = mp4,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer ?: ""
                    this.quality = Qualities.P1080.value
                }
            )
    }
}

class Animeav1upn : VidStack() {
    override var mainUrl = "https://animeav1.uns.bio"
}