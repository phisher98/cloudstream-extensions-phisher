package com.Donghuastream


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink

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
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer ?: ""
                        this.quality = Qualities.Unknown.value
                    }
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

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}


open class Ultrahd : ExtractorApi() {
    override var name = "Ultrahd Streamplay"
    override var mainUrl = "https://ultrahd.streamplay.co.in"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
            val response = app.get(url,referer=mainUrl).document
            val extractedpack =response.toString()
            Regex("\\\$\\.\\s*ajax\\(\\s*\\{\\s*url:\\s*\"(.*?)\"").find(extractedpack)?.groupValues?.get(1)?.let { link ->
                app.get(link).parsedSafe<Root>()?.sources?.map {
                    val m3u8= httpsify( it.file)
                    if (m3u8.contains(".mp4"))
                    {
                        callback.invoke(
                            newExtractorLink(
                                "Ultrahd Streamplay",
                                "Ultrahd Streamplay",
                                url = m3u8,
                                INFER_TYPE
                            ) {
                                this.referer = ""
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                    else
                    {
                        M3u8Helper.generateM3u8(
                            this.name,
                            m3u8,
                            "$referer",
                        ).forEach(callback)
                    }
                }
                app.get(link).parsedSafe<Root>()?.tracks?.map {
                    val langurl=it.file
                    val lang=it.label
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang,  // Use label for the name
                            langurl     // Use extracted URL
                        )
                    )
                }
            }
    }
}

class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(
            url, referer = referer ?: "$mainUrl/"
        )
        val playerScript =
            response.document.selectFirst("script:containsData(mp4)")?.data()
                ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{") ?:""
        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(playerScript)
        for (match in matches) {
            val href = match.groupValues[1].replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = href,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getQualityFromName("")
                }
            )
        }
    }
}

open class PlayStreamplay : ExtractorApi() {
    override var name = "All sub player"
    override var mainUrl = "https://play.streamplay.co.in"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        val packedScript = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data() ?: return
        val evalRegex = Regex("""eval\(.*?\)\)\)""", RegexOption.DOT_MATCHES_ALL)
        val packedCode = evalRegex.find(packedScript)?.value ?: return
        val unpackedJs = JsUnpacker(packedCode).unpack() ?: return
        val token = Regex("""kaken="(.*?)"""").find(unpackedJs)?.groupValues?.getOrNull(1) ?: return
        val apiUrl = "$mainUrl/api/?$token"
        val response = app.get(apiUrl).parsedSafe<Response>() ?: return

        val m3u8Url = response.sources.find { it.file.isNotBlank() }?.file
        if (!m3u8Url.isNullOrEmpty()) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3u8Url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        response.tracks.forEach { subtitle ->
            subtitleCallback(
                SubtitleFile(
                    lang = subtitle.label,
                    url = subtitle.file
                )
            )
        }
    }

    data class Response(
        val query: Query,
        val status: String,
        val message: String,
        @JsonProperty("embed_url")
        val embedUrl: String,
        @JsonProperty("download_url")
        val downloadUrl: String,
        val title: String,
        val poster: String,
        val filmstrip: String,
        val sources: List<Source>,
        val tracks: List<Track>,
    )

    data class Query(
        val source: String,
        val id: String,
        val download: String,
    )

    data class Source(
        val file: String,
        val type: String,
        val label: String,
        val default: Boolean,
    )

    data class Track(
        val file: String,
        val label: String,
        val default: Boolean?,
    )

}

