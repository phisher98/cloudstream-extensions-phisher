package com.Streamblasters

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked


class D000d : DoodLaExtractor() {
    override var mainUrl = "https://dood.li"
}

class swhoi : StreamWishExtractor() {
    override var mainUrl = "https://swhoi.com"
}

class wishonly : StreamWishExtractor() {
    override var mainUrl = "https://wishonly.site"
}

class cybervynx : Filesim() {
    override var mainUrl = "https://cybervynx.com"
}


class mivalyo : VidhideExtractor() {
    override var name = "Mivalyo"
    override var mainUrl = "https://mivalyo.com"
}

class vidhidevip : VidhideExtractor() {
    override var mainUrl = "https://vidhidevip.com"
}

class luluvdoo : VidhideExtractor() {
    override var mainUrl = "https://luluvdoo.com"
}


class smoothpre : VidhideExtractor() {
    override var mainUrl = "https://smoothpre.com"
}

class vidhidepre : VidhideExtractor() {
    override var mainUrl = "https://vidhidepre.com"
}

class ups2up : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://ups2up.fun"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Origin" to mainUrl,
            "referer" to mainUrl,
        )

        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.documentLarge.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        // m3u8 urls could be prefixed by 'file:', 'hls2:' or 'hls4:', so we just match ':'
        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }

}

class jodwish : StreamWishExtractor() {
    override var mainUrl = "https://jodwish.com"
}

class asnwish : StreamWishExtractor() {
    override var mainUrl = "https://asnwish.com"
}

class hglink : ExtractorApi() {
    override var name = "hglink"
    override var mainUrl = "https://Hglink.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, allowRedirects = false)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.documentLarge.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        // m3u8 urls could be prefixed by 'file:', 'hls2:' or 'hls4:', so we just match ':'
        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
            ).forEach(callback)
        }
    }
}