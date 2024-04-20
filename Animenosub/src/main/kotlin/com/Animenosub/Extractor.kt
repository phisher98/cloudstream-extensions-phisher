package com.Animenosub

//import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI

open class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        referer ?: "",
                        Qualities.Unknown.value,
                        URI(link).path.endsWith(".m3u8")
                    )
                )
            }
        }
        return null
    }
}

class wishfast : StreamWishExtractor() {
    override var mainUrl = "https://wishfast.top"
    override var name = "StreamWish"
}

class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

open class Filemoonsxx : ExtractorApi() {
    override val name = "Filemoonsx"
    override val mainUrl = "http://filemoon.sx"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = "https://waaw.to")
        if (response.text.contains("eval(function(p,a,c,k,e,d)")) {
            val packed=response.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
            val unpacked=JsUnpacker(packed).unpack()
            val m3u8 =
                Regex("file:\\s*\"(.*?m3u8.*?)\"").find(unpacked ?: return)?.groupValues?.getOrNull(1)
            generateM3u8(
                name,
                m3u8 ?: return,
                mainUrl
            ).forEach(callback)
        } else {
            val script=response.document.selectFirst("script:containsData(sources:)")?.data()
            val m3u8 =
                Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
            generateM3u8(
                name,
                m3u8 ?: return,
                mainUrl
            ).forEach(callback)
        }
    }
}