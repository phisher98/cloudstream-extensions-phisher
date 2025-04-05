package com.phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.UUID

class PirateIPTV : SportsIPTV() {
    override var lang = "en"
    override var mainUrl: String = BuildConfig.PirateIPTV
    override var name = "Pirate IPTV"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        if (loadData.url.contains("mpd"))
        {
            callback.invoke(
                newDrmExtractorLink(
                    this.name,
                    this.name,
                    loadData.url,
                    INFER_TYPE,
                    UUID.randomUUID()
                )
                {
                    this.key=loadData.key.trim()
                    this.kid=loadData.keyid.trim()
                }
            )
        }
            else
        if(loadData.url.contains("&e=.m3u"))
            {
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = loadData.url,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://embedme.top/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        else
        {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    loadData.title,
                    url = loadData.url,
                    INFER_TYPE
                ) {
                    this.referer = "https://embedme.top/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }
}