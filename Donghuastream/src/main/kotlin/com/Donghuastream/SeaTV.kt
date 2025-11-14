package com.Donghuastream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup


open class SeaTV : Donghuastream() {
    override var mainUrl              = "https://seatv-24.xyz"
    override var name                 = "SeaTV"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?status=completed&type=&order=update" to "Completed",
        "anime/?status=upcoming&type=&sub=&order=" to "Upcoming",
    )

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        document.select(".mobius option").amap { server ->
            val base64 = server.attr("value").takeIf { it.isNotEmpty() }
            val doc = base64?.let { base64Decode(it).let(Jsoup::parse) }
            val iframeUrl = doc?.select("iframe")?.attr("src")?.let(::httpsify)
            val metaUrl = doc?.select("meta[itemprop=embedUrl]")?.attr("content")?.let(::httpsify)
            val url = iframeUrl?.takeIf { it.isNotEmpty() } ?: metaUrl.orEmpty()
            if (url.isNotEmpty()) {
                when {
                    url.contains("vidmoly") -> {
                        val newUrl = url.substringAfter("=\"").substringBefore("\"")
                        val link = "http:$newUrl"
                        loadExtractor(link, referer = url, subtitleCallback, callback)
                    }
                    url.endsWith("mp4") -> {
                        callback.invoke(
                            newExtractorLink(
                                "All Sub Player",
                                "All Sub Player",
                                url = url,
                                INFER_TYPE
                            ) {
                                this.referer = ""
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                    else -> {
                        loadExtractor(url, referer = url, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }
}