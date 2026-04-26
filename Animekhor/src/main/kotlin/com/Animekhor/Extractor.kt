package com.Animekhor


import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class embedwish : StreamWishExtractor() {
    override var mainUrl = "https://embedwish.com"
}

class P2pstream : VidStack() {
    override var mainUrl = "https://animekhor.p2pstream.vip"
}

class Filelions : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://filelions.live"
}

class Swhoi : StreamWishExtractor() {
    override var mainUrl = "https://swhoi.com"
    override val requiresReferer = true
}

class VidHidePro5: VidHidePro() {
    override val mainUrl = "https://vidhidevip.com"
    override val requiresReferer = true
}

class PlayerDonghuaworld: Rumble() {
    override var mainUrl = "https://player.donghuaworld.in"
    override val requiresReferer = true
}

class Donghuaplanet: Rumble() {
    override var mainUrl = "https://player.donghuaplanet.com"
    override val requiresReferer = true
}

open class Rumble : ExtractorApi() {
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
        val document = response.document

        val playerScript = document.selectFirst("script:containsData(jwplayer)")?.data()
            ?: return

        val sourcesJson = Regex("""sources\s*:\s*(\[[\s\S]*?])""")
            .find(playerScript)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")

        sourcesJson?.let { raw ->
            tryParseJson<List<Map<String, String>>>(raw)?.forEach { source ->
                val fileUrl = source["file"] ?: return@forEach
                val label = source["label"] ?: ""
                val type = source["type"] ?: ""

                try {
                    when {
                        type.contains("mpegURL") || fileUrl.contains(".m3u8") ->
                            M3u8Helper.generateM3u8(name, fileUrl, mainUrl).forEach(callback)

                        fileUrl.contains(".mp4") ->
                            callback.invoke(
                                newExtractorLink(name, "$name $label", url = fileUrl, INFER_TYPE) {
                                    this.referer = referer ?: mainUrl
                                    this.quality = getQualityFromName(label)
                                }
                            )
                    }
                } catch (e: Exception) {
                    Log.e(name, "Source failed [$label]: ${e.message}")
                }
            }
        }

        val videoId = url.substringAfter("/embed/v").substringBefore("/")
        if (videoId.isNotEmpty()) {
            val fallback = "$mainUrl/hls-vod/$videoId/playlist.m3u8?u=0&b=0"
            M3u8Helper.generateM3u8(name, fallback, mainUrl).forEach(callback)
        }

        val tracksJsonRaw = Regex("""tracks\s*=\s*(\[[\s\S]*?])""")
            .find(playerScript)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")

        tracksJsonRaw?.let { raw ->
            tryParseJson<List<Map<String, String>>>(raw)?.forEach { track ->
                val file = track["file"] ?: return@forEach
                val label = track["label"] ?: "Unknown"
                if (file.endsWith(".vtt")) {
                    subtitleCallback.invoke(newSubtitleFile(label, file))
                }
            }
        }
    }
}
