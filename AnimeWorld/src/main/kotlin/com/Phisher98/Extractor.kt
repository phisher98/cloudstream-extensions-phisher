package com.Phisher98

import com.Phisher98.AnimeWorld.Companion.API
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink

class Pixdrive : Filesim() {
    override val name = "Pixdrive"
    override var mainUrl = "https://pixdrive.cfd"
}

class Ghbrisk : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://ghbrisk.com"
    override val requiresReferer = true
}


class Vidcloud : VidStack() {
    override var name = "Vidcloud"
    override var mainUrl = "https://vidcloud.upns.ink"
    override val requiresReferer = true
}

open class Streamruby : ExtractorApi() {
    override var name = "Streamruby"
    override var mainUrl = "https://rubystm.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val newUrl = if (url.contains("/e/")) url.replace("/e", "") else url
        val txt = app.get(newUrl).text
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1)

        return m3u8?.takeIf { it.isNotEmpty() }?.let {
            listOf(
                newExtractorLink(
                    name = this.name,
                    source = this.name,
                    url = it,
                    type = INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

open class Animedekho : ExtractorApi() {
    override var name = "Pixel HD"
    override var mainUrl = "https://animedekho.co"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val slug=url.substringAfterLast("slug=")
        val m3u8 = "https://pixeldrain.net/api/file/$slug?download"

        return m3u8.takeIf { it.isNotEmpty() }?.let {
            listOf(
                newExtractorLink(
                    name = this.name,
                    source = this.name,
                    url = it,
                    type = INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

open class Cybervynx : ExtractorApi() {
    override val name = "Cybervynx"
    override val mainUrl = "https://cybervynx.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("/download/", "/e/")
        val response = app.get(embedUrl, referer = referer)

        val scriptData = getPacked(response.text)?.takeIf { it.isNotEmpty() }?.let {
            getAndUnpack(response.text)
        } ?: response.document.selectFirst("script:containsData(sources:)")?.data()

        if (scriptData.isNullOrEmpty()) return

        val m3u8Url = Regex("\"hls2\":\"(.*?)\"").find(scriptData)?.groupValues?.getOrNull(1)
        val subtitleUrl = Regex("""file\s*:\s*"(https?://[^"]+\.vtt)"""").find(scriptData)?.groupValues?.getOrNull(1)

        if (m3u8Url.isNullOrBlank()) return
        callback(
            newExtractorLink(
                name,
                name,
                url = m3u8Url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P1080.value
            }
        )

        if (subtitleUrl != null) {
            subtitleCallback(
                SubtitleFile(
                    "English",
                    subtitleUrl
                )
            )
        }
    }
}




/*
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
        if (url.contains("/video/")) {
            val extractedHash = url.substringAfterLast("/")
            val m3u8Url = "$API/player/index.php?data=$extractedHash&do=getVideo"
            Log.d("Phisher",m3u8Url)
            val header= mapOf("x-requested-with" to "XMLHttpRequest")
            val formdata= mapOf("hash" to extractedHash,"r" to "https://anime-world.co/")
            val response = app.post(m3u8Url, headers=header, data = formdata).parsedSafe<Response>()
            response?.videoSource?.let { m3u8 ->
                callback(
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

                subtitleCallback.invoke(
                    SubtitleFile(
                        "English",
                        "$API/subs/m3u8/$extractedHash/subtitles-eng.vtt"
                    )
                )
            }
        }
    }
}
 */

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
        val evalContent = app.get(url, referer = referer)
            .document
            .selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data() ?: return

        val evalRegex = Regex("""eval\(.*""", RegexOption.DOT_MATCHES_ALL)
        val matchedEval = evalRegex.find(evalContent)?.value ?: return
        val scriptData = JsUnpacker(matchedEval).unpack().toString()
        val extractedHash = scriptData.substringAfter("cdn\\\\/down\\\\/").substringBefore("\\")
        val subtitleUrl = Regex("""playerjsSubtitle\s*=\s*"(?:\[.*?])?(https?://[^"]+\.srt)"""").find(scriptData)?.groupValues?.getOrNull(1)
        val m3u8Url = "$API/cdn/hls/$extractedHash/master.txt"

        callback(
            newExtractorLink(
                name,
                name,
                url = m3u8Url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P1080.value
            }
        )

        if (subtitleUrl != null) {
            subtitleCallback(
                SubtitleFile(
                    "English",
                    subtitleUrl
                )
            )
        }
    }
}

