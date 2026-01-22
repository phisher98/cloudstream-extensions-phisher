package com.phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.nodes.Document

class vidcloudupns : VidStack() {
    override var mainUrl = "https://vidcloud.upns.ink"
}

class ascdn21 : AWSStream() {
    override val name = "Zephyrflick"
    override val mainUrl = "https://as-cdn21.top"
    override val requiresReferer = true
}


open class AWSStream : ExtractorApi() {
    override val name = "AWSStream"
    override val mainUrl = "https://z.awstream.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extractedHash = url.substringAfterLast("/")
        val doc = app.get(url).documentLarge
        val m3u8Url = "$mainUrl/player/index.php?data=$extractedHash&do=getVideo"
        val header = mapOf("x-requested-with" to "XMLHttpRequest")
        val formdata = mapOf("hash" to extractedHash, "r" to mainUrl)
        val response = app.post(m3u8Url, headers = header, data = formdata).parsedSafe<Response>()
        response?.videoSource?.let { m3u8 ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
            val extractedPack = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().orEmpty()

            JsUnpacker(extractedPack).unpack()?.let { unpacked ->
                Regex(""""kind":\s*"captions"\s*,\s*"file":\s*"(https.*?\.srt)""")
                    .find(unpacked)
                    ?.groupValues
                    ?.get(1)
                    ?.let { subtitleUrl ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                "English",
                                subtitleUrl
                            )
                        )
                    }
            }
        }
    }

    data class Response(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<Any?>,
        val attachmentLinks: List<Any?>,
        val ck: String,
    )
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
        val doc: Document? = if (url.contains("url=")) app.get(url).documentLarge else null
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
        ).documentLarge

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


class Blakiteapi : ExtractorApi() {
    override val name = "Blakiteapi"
    override val mainUrl = "https://blakiteapi.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiurl = "$mainUrl/api/get.php?id=${url.substringAfterLast("/")}&tmdbId=${url.substringAfter("embed/").substringBefore("/")}"

        val responseText = app.get(apiurl).text

        val json = JSONObject(responseText)
        val success = json.optBoolean("success", false)

        if (success) {
            val data = json.getJSONObject("data")

            val quality = data.optString("quality", "480p")
            val format = data.optString("format", "MP4")
            val dataId = data.optString("dataId", "")
            val streamUrl = "$mainUrl/stream/$dataId.$format"
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    streamUrl,
                    INFER_TYPE
                )
                {
                    this.quality=getQualityFromString(quality)
                }
            )
        }
    }

    private fun getQualityFromString(q: String): Int {
        return when {
            q.contains("1080", true) -> Qualities.P1080.value
            q.contains("720", true) -> Qualities.P720.value
            q.contains("480", true) -> Qualities.P480.value
            q.contains("360", true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}