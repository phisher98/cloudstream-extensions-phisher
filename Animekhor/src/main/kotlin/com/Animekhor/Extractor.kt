package com.Animekhor


import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.newSubtitleFile
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
            val fallback="${mainUrl}/hls-vod/${url.substringAfter("/embed/v").substringBefore("/")}/playlist.m3u8?u=0&b=0"
            M3u8Helper.generateM3u8(name, fallback, mainUrl).forEach(callback)
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

