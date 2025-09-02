package com.phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document

class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = " https://filemoon.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url).document.selectFirst("iframe")?.attr("src") ?:""
        val res= app.get(href, headers = mapOf("Accept-Language" to "en-US,en;q=0.5","sec-fetch-dest" to "iframe")).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        val m3u8= JsUnpacker(res).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)
        }
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?:"",
                url,
                Qualities.P1080.value,
                type = ExtractorLinkType.M3U8,
            )
        )
    }
}


class vidcloudupns : VidStack() {
    override var mainUrl = "https://vidcloud.upns.ink"
}

class Multimovies: StreamWishExtractor() {
    override var name = "Multimovies Cloud"
    override var mainUrl = "https://multimovies.cloud"
    override var requiresReferer = true
}

class FileMoonNL : Filesim() {
    override val mainUrl = "https://filemoon.nl"
    override val name = "FileMoon"
}

class Vidmolynet : Vidmoly() {
    override val mainUrl = "https://vidmoly.net"
}

class Cdnwish : StreamWishExtractor() {
    override var name = "Streamwish"
    override var mainUrl = "https://cdnwish.com"
}

class Cloudy : VidStack() {
    override var mainUrl = "https://cloudy.upns.one"
}


class Animezia : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://animezia.cloud"
    override var requiresReferer = true
}

data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)


class Animedekhoco : ExtractorApi() {
    override val name = "Animedekhoco"
    override val mainUrl = "https://animedekho.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc: Document? = if (url.contains("url=")) app.get(url).document else null
        val text: String? = if (!url.contains("url=")) app.get(url).text else null

        val links = mutableListOf<Pair<String, String>>()

        doc?.select("select#serverSelector option")?.forEach { option ->
            val link = option.attr("value")
            val name = option.text().ifBlank { "Unknown" }
            if (link.isNotBlank()) links.add(name to link)
        }

        text?.let {
            val regex = Regex("""file\s*:\s*"([^"]+)"""")
            regex.find(it)?.groupValues?.get(1)?.let { link ->
                links.add("Player File" to link)
            }
        }

        links.forEach { (serverName, serverUrl) ->
            callback.invoke(
                newExtractorLink(
                    serverName,
                    serverName,
                    url = serverUrl,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://rubystm.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanedUrl = url.replace("/e", "")
        val response = app.get(
            cleanedUrl,
            referer = cleanedUrl,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document

        val scriptData = response.selectFirst("script:containsData(vplayer)")?.data().orEmpty()

        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to cleanedUrl,
        )

        Regex("file:\"(.*)\"").find(scriptData)?.groupValues?.getOrNull(1)?.let { link ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = link,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                    this.headers = headers
                }
            )
        }
    }
}