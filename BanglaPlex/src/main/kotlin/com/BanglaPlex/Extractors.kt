package com.BanglaPlex

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vtbe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class Iplayerhls : Vtbe() {
    override var name = "Iplayerhls"
    override var mainUrl = "https://iplayerhls.com"
}

class StreamwishHG : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to"
}

class Rpmvid : VidStack() {
    override var name = "Rpmvid"
    override var mainUrl = "https://bpx.rpmvid.site"
}

class Plextream : ExtractorApi() {
    override val name = "Plextream"
    override val mainUrl = "https://plextream.work"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframeScript = app.get(url).documentLarge.selectFirst("script:containsData(videoUrls)")?.data() ?: return
        val regex = Regex("""(\w+)\s*:\s*['"](https://[^'"]+)['"]""")
        val matches = regex.findAll(iframeScript)
        matches.forEach { match ->
            val serverName = match.groupValues[1]
            val videoUrl = match.groupValues[2]
            Log.d("Phisher","$serverName $videoUrl")
            when (serverName.lowercase()) {
                "rpmshare", "upnshare", "streamp2p" -> {
                    VidStack().getUrl(videoUrl, referer, subtitleCallback, callback)
                }
                else -> {
                    loadExtractor(videoUrl, subtitleCallback, callback)
                }
            }
        }
    }
}


class XcloudC : Xcloud() {
    override var name = "XCloud"
    override var mainUrl = "https://xcloud.click"
}

open class Xcloud : ExtractorApi() {
    override val name = "XCloud"
    override val mainUrl = "https://xcloud.forum"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).documentLarge
        val directBtn = document.select("div.vd a.btn-primary")
            .firstOrNull { it.text().contains("Generate Direct", ignoreCase = true) }
            ?: return
        val linksDoc = app.get(fixUrl(directBtn.attr("href"))).documentLarge
        val iframe=linksDoc.select("iframe").attr("src")
        linksDoc.select("h2 a.btn").forEach { link ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    fixUrl(link.attr("href")),
                ) { this.quality = quality }
            )
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    iframe,
                ) { this.quality = quality }
            )
        }
    }
}

