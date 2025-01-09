package com.Phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: String? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        callback.invoke(
            ExtractorLink(
                source,
                source,
                link.url,
                link.referer,
                getQualityFromName(quality),
                link.type,
                link.headers,
                link.extractorData
            )
        )
    }
}


class Kwik : ExtractorApi() {
    override val name            = "Kwik"
    override val mainUrl         = "https://kwik.si"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val res = app.get(url,referer=url)
        val script =
            res.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        val unpacked = getAndUnpack(script ?: return)
        val m3u8 =Regex("source=\\s*'(.*?m3u8.*?)'").find(unpacked)?.groupValues?.getOrNull(1) ?:""
        callback.invoke(
            ExtractorLink(
                name,
                name,
                m3u8,
                "",
                getQualityFromName(""),
                INFER_TYPE
            )
        )
    }
}