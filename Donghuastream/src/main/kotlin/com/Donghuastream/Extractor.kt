package com.Donghuastream


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.newSubtitleFile
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
        val response = app.get(url,referer=mainUrl).documentLarge
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
            val response = app.get(url,referer=mainUrl).documentLarge
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
                        newSubtitleFile(
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
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val document = response.documentLarge

        val playerScript = document.selectFirst("script:containsData(jwplayer)")?.data()
            ?: return

        // Extract sources (mp4 or m3u8)
        val sourceRegex = """"file"\s*:\s*"(https:[^"]+\.(?:mp4|m3u8)[^"]*)"""".toRegex()
        val sources = sourceRegex.findAll(playerScript)

        for ((index, source) in sources.withIndex()) {
            val index = index + 1
            val fileUrl = source.groupValues[1].replace("\\/", "/")
            if (fileUrl.contains(".mp4"))
            {
                callback.invoke(
                    newExtractorLink(
                        name,
                        "$name Video Server $index",
                        url = fileUrl,
                        INFER_TYPE
                    ) {
                        this.referer = ""
                        this.quality = getQualityFromName("")
                    }
                )
            } else M3u8Helper.generateM3u8(name, fileUrl, mainUrl).forEach(callback)
        }

        // Extract subtitle tracks
        val trackRegex = """"file"\s*:\s*"(https:[^"]+\.vtt[^"]*)"\s*,\s*"label"\s*:\s*"([^"]+)"""".toRegex()
        val tracks = trackRegex.findAll(playerScript)

        for (track in tracks) {
            val fileUrl = track.groupValues[1].replace("\\/", "/")
            val label = track.groupValues[2]

            subtitleCallback.invoke(
                newSubtitleFile(label, fileUrl)
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
        val doc = app.get(url, timeout = 10000).documentLarge
        val packedScript = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data() ?: return
        val evalRegex = Regex("""eval\(.*?\)\)\)""", RegexOption.DOT_MATCHES_ALL)
        val packedCode = evalRegex.find(packedScript)?.value ?: return
        val unpackedJs = JsUnpacker(packedCode).unpack() ?: return
        val token = Regex("""kaken="(.*?)"""").find(unpackedJs)?.groupValues?.getOrNull(1) ?: return
        val apiUrl = "$mainUrl/api/?$token"
        val response = app.get(apiUrl, timeout = 10000).parsedSafe<Response>() ?: return

        val m3u8Url = response.sources.find { it.file.isNotBlank() }?.file
        if (!m3u8Url.isNullOrEmpty()) {
            val headers = mapOf(
                "pragma" to "no-cache",
                "priority" to "u=0, i",
                "sec-ch-ua" to "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"Google Chrome\";v=\"138\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "sec-fetch-dest" to "document",
                "sec-fetch-mode" to "navigate",
                "sec-fetch-site" to "none",
                "sec-fetch-user" to "?1",
                "upgrade-insecure-requests" to "1",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
            )
            M3u8Helper.generateM3u8(name, m3u8Url, mainUrl, headers = headers).forEach(callback)
        }

        response.tracks.forEach { subtitle ->
            subtitleCallback(
                newSubtitleFile(
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

