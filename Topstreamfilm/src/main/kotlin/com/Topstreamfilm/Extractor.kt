package com.Topstreamfilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack


open class SuperVideo : ExtractorApi() {
    override val name = "SuperVideo"
    override val mainUrl = "https://supervideo.tv"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url.replace("tv","cc"),referer=referer)
        val script =
            res.documentLarge.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        val unpacked = getAndUnpack(script ?: return)
        val m3u8 =Regex("file:\"(.*?m3u8.*?)").find(unpacked)?.groupValues?.getOrNull(1) ?:""
        M3u8Helper.generateM3u8(
            this.name,
            m3u8,
            referer = "$mainUrl/",
        ).forEach(callback)
    }
}

//still in Development

open class Dropload : ExtractorApi() {
    override val name = "Dropload"
    override val mainUrl = "https://dropload.io"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url)
        val script =res.documentLarge.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        val unpacked = JsUnpacker(script).unpack().toString()
        val m3u8 =Regex("file:\"(.*?m3u8.*?)\"").find(unpacked)?.groupValues?.getOrNull(1) ?:""
        val headers= mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        M3u8Helper.generateM3u8(
            this.name,
            m3u8,
            referer = "$mainUrl/",
            headers = headers
        ).forEach(callback)
    }
}