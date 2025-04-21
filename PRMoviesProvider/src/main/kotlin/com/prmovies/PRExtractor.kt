package com.prmovies


import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Minoplres : ExtractorApi() {
    override val name = "Minoplres"
    override val mainUrl = "https://spedostream.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response=app.get(url,referer=referer).document
            val script = response.selectFirst("script:containsData(vplayer)")?.data().toString()
            Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.P720.value
                    }
                )
            }
    }
}

open class Embdproxy : ExtractorApi() {
    override val name = "Embdproxy"
    override val mainUrl = "https://embdproxy.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response=app.get(url,referer=referer).document
        val script = response.selectFirst("script:containsData(vplayer)")?.data().toString()
        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = link,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }
}

class Waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}