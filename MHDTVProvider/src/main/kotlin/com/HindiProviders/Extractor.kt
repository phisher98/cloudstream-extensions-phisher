package com.HindiProviders

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

open class Mhdmaxtv : ExtractorApi() {
    override val name = "Mhdmaxtv"
    override val mainUrl = "https://live.notebulk.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0")
        val res = app.get(url, referer = referer, headers = headers).text
        val lines = res.lines()
        // Find the first non-comment line (line that does not start with #)
        val nonCommentLine = lines.firstOrNull { line ->
            !line.startsWith("#") && line.isNotBlank()
        }
        val source=url.replace("index.m3u8","$nonCommentLine")
        Log.d("Phisher Test",source)
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                url.replace("index.m3u8","$nonCommentLine"),
                url,
                Qualities.Unknown.value,
                isM3u8 = true,
            )
        )
    }
}

open class colorsscreen : ExtractorApi() {
    override val name = "Colorsscreen"
    override val mainUrl = "https://colorsscreen.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0")
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                url,
                referer?:"",
                Qualities.Unknown.value,
                isM3u8 = true,
                headers=headers
            )
        )
    }
}