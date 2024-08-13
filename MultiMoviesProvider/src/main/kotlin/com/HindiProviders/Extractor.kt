package com.HindiProviders

import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.SubtitleFile

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
}

class Animezia : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://animezia.cloud"
}

class server2 : VidhideExtractor() {
    override var name = "Multimovies Vidhide"
    override var mainUrl = "https://server2.shop"
}

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {
        app.get(url).document.select("ul#videoLinks li").map {
            val link=it.attr("data-link")
            loadExtractor(link,subtitleCallback, callback)
        }
    }
}

class Multimovies : ExtractorApi() {
    override var name = "Multimovies Cloud"
    override val mainUrl = "https://multimovies.cloud"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(
            url,
            referer = "https://multimovies.sbs"
        ).document
        var script = doc.select("script").find {
            it.html().contains("jwplayer(\"vplayer\").setup(")
        }
        var scriptContent = script?.html()
        val extractedurl = Regex("""sources: \[\{file:"(.*?)"""").find(scriptContent ?: "")?.groupValues?.get(1)
        if (!extractedurl.isNullOrBlank()) {
            callback(
                ExtractorLink(
                    this.name,
                    this.name,
                    extractedurl,
                    referer ?: "$mainUrl/",
                    getQualityFromName(""),
                    extractedurl.contains("m3u8")
                )
            )
        }
    }
                                 }
