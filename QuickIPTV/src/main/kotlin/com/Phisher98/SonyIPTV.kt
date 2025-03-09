package com.phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities

class SonyIPTV : SportsIPTV() {
    override var lang = "en"
    override var mainUrl: String = BuildConfig.SonyIPTV
    override var name = "Sony IPTV"
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
                DrmExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = loadData.url,
                    referer = "",
                    quality = Qualities.Unknown.value,
                    type = INFER_TYPE,
                    kid = loadData.keyid.trim(),
                    key = loadData.key.trim(),
                )
            )
        }
            else
        if(loadData.url.contains("&e=.m3u"))
            {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    loadData.url,
                    "https://embedme.top/",
                    Qualities.Unknown.value,
                    isM3u8 = true,
                )
            )
            }
        else
        {
            callback.invoke(
                ExtractorLink(
                this.name,
                loadData.title,
                loadData.url,
                "https://embedme.top/",
                Qualities.Unknown.value,
                type = INFER_TYPE,
                )
            )
        }
        return true
    }
}