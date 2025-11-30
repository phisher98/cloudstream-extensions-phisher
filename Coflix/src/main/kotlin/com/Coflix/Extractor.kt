package com.Coflix

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vidguardto
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.HlsPlaylistParser
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

open class darkibox : ExtractorApi() {
    override var name = "Darkibox"
    override var mainUrl = "https://darkibox.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val response = app.get(url).toString()
            Regex("""sources:\s*\[\{src:\s*"(.*?)"""").find(response)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer ?: ""
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        return null
    }
}

open class Videzz : ExtractorApi() {
    override var name = "Videzz"
    override var mainUrl = "https://videzz.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val mp4 = app.get(url,referer=mainUrl).documentLarge.select("#vplayer > #player source").attr("src")
            return listOf(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = mp4,
                    type = INFER_TYPE
                ) {
                    this.referer = referer ?: ""
                    this.quality = Qualities.P1080.value
                }
            )
    }
}

class VidHideplus : VidhideExtractor() {
    override var mainUrl = "https://vidhideplus.com"
}


class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

class wishonly : StreamWishExtractor() {
    override var mainUrl = "https://wishonly.site"
}

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}

class Vidguardto2 : Vidguardto() {
    override val mainUrl = "https://listeamed.net"
}

class CoflixUPN : VidStack() {
    override var mainUrl = "https://coflix.upn.one"
}

class Mivalyo : VidhideExtractor() {
    override var mainUrl = "https://mivalyo.com"
}


class Uqload : ExtractorApi() {
    override val name = "Uqload"
    override val mainUrl = "https://uqload.cx"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val html = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0")).text
        val srcRegex = Regex("""sources\s*:\s*\[\s*["']([^"']+)["']""")
        val videoUrl = srcRegex.find(html)?.groupValues?.get(1)
        if (videoUrl != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    videoUrl,
                    INFER_TYPE
                )
                {
                    this.referer = referer ?: mainUrl
                }
            )
        }
    }
}


class Veev : ExtractorApi() {
    override val name = "Veev"
    override val mainUrl = "https://veev.to"
    override val requiresReferer = false

    private val pattern =
        Regex("""(?://|\.)(?:veev|kinoger|poophq|doods)\.(?:to|pw|com)/[ed]/([0-9A-Za-z]+)""")

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mediaId = pattern.find(url)?.groupValues?.get(1)
            ?: return
        val pageUrl = "$mainUrl/e/$mediaId"
        val html = app.get(
            pageUrl,
            headers = mapOf("User-Agent" to DEFAULT_UA)
        ).text

        val encRegex = Regex("""[.\s'](?:fc|_vvto\[[^]]*)(?:['\]]*)?\s*[:=]\s*['"]([^'"]+)""")
        val foundValues = encRegex.findAll(html).map { it.groupValues[1] }.toList()

        if (foundValues.isEmpty()) return

        for (f in foundValues.reversed()) {
            val ch = veevDecode(f)
            if (ch == f) continue

            val dlUrl = "$mainUrl/dl?op=player_api&cmd=gi&file_code=$mediaId&r=$mainUrl&ch=$ch&ie=1"
            val responseText = app.get(dlUrl, headers = mapOf("User-Agent" to DEFAULT_UA)).text

            val json = try {
                JSONObject(responseText)
            } catch (_: Exception) {
                continue
            }
            val file = json.optJSONObject("file") ?: continue

            if (file.optString("file_status") != "OK") continue

            val dv = file.getJSONArray("dv").getJSONObject(0).getString("s")
            val decoded = decodeUrl(veevDecode(dv), buildArray(ch)[0])

            val fileMimeType = file.optString("file_mime_type", "")

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    decoded,
                    INFER_TYPE
                )
                {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return
        }
    }

    fun String.toExoPlayerMimeType(): String {
        return when (this.lowercase()) {
            "video/x-matroska", "video/webm" -> HlsPlaylistParser.MimeTypes.VIDEO_MATROSKA
            "video/mp4" -> HlsPlaylistParser.MimeTypes.VIDEO_MP4
            "application/x-mpegurl", "application/vnd.apple.mpegurl" -> HlsPlaylistParser.MimeTypes.APPLICATION_M3U8
            "video/avi" -> HlsPlaylistParser.MimeTypes.VIDEO_AVI
            else -> ""
        }
    }

    private fun veevDecode(etext: String): String {
        val result = StringBuilder()
        val lut = HashMap<Int, String>()
        var n = 256
        var c = etext[0].toString()
        result.append(c)

        for (char in etext.drop(1)) {
            val code = char.code
            val nc = if (code < 256) char.toString() else lut[code] ?: (c + c[0])
            result.append(nc)
            lut[n++] = c + nc[0]
            c = nc
        }
        return result.toString()
    }

    private fun jsInt(x: Char): Int = x.digitToIntOrNull() ?: 0

    private fun buildArray(encoded: String): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        val it = encoded.iterator()
        fun nextIntOrZero(): Int = if (it.hasNext()) jsInt(it.nextChar()) else 0
        var count = nextIntOrZero()
        while (count != 0) {
            val row = mutableListOf<Int>()
            repeat(count) {
                row.add(nextIntOrZero())
            }
            result.add(row.reversed())
            count = nextIntOrZero()
        }
        return result
    }


    private fun decodeUrl(encoded: String, rules: List<Int>): String {
        var text = encoded
        for (r in rules) {
            if (r == 1) text = text.reversed()
            val arr = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            text = arr.toString(Charsets.UTF_8).replace("dXRmOA==", "")
        }
        return text
    }
}
