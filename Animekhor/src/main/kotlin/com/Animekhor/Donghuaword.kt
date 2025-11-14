package com.Animekhor

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor


class Donghuaword  : Animekhor() {
    override var mainUrl              = "https://donghuaworld.com"
    override var name                 = "Donghuaword"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.Anime)

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        document.select("div.server-item a").map {
            val base64=it.attr("data-hash")
            val decodedUrl = base64Decode(base64)
            val regex = Regex("""src=["']([^"']+)["']""",RegexOption.IGNORE_CASE)
            val matchResult = regex.find(decodedUrl)
            val url = matchResult?.groups?.get(1)?.value ?: "Not found"
            loadExtractor(url, referer = mainUrl, subtitleCallback, callback)

        }
        return true
    }
}