package com.Kaido

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class Rapid : MegaPlay() {
    override val name = "Rapid"
    override val mainUrl = "https://rapid-cloud.co"
    override val requiresReferer = true
}

open class MegaPlay : ExtractorApi() {

    override val name = "MegaPlay"
    override val mainUrl = "https://megaplay.buzz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val mainHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Origin" to "https://rapid-cloud.co",
            "Referer" to "https://rapid-cloud.co/",
            "Connection" to "keep-alive",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )

        try {
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )

            val id = url.substringAfterLast("/").substringBefore("?")
                .takeIf { it.isNotBlank() }
                ?: app.get(url, headers = headers)
                    .document
                    .selectFirst("#vidcloud-player")
                    ?.attr("data-id")
                ?: return


            val apiUrl = "$mainUrl/embed-2/v2/e-1/getSources?id=$id"

            val response = app.get(apiUrl, headers = headers)
                .parsedSafe<MegaPlayResponse>()
                ?: return

            val m3u8 = response.sources?.firstOrNull()?.file ?: return

            generateM3u8(name, m3u8, mainUrl, headers = mainHeaders)
                .forEach(callback)

            response.tracks?.forEach { track ->
                if (track.kind == "captions" || track.kind == "subtitles") {
                    val file = track.file ?: return@forEach
                    val label = track.label ?: "Unknown"

                    subtitleCallback(
                        newSubtitleFile(label, file)
                    )
                }
            }

        } catch (e: Exception) {
            Log.e("MegaPlay", "Extraction failed: ${e.message}")
        }
    }

    data class MegaPlayResponse(
        val sources: List<Source>?,
        val tracks: List<Track>?,
        val encrypted: Boolean?,
        val intro: Intro?,
        val outro: Outro?,
        val server: Long?
    )

    data class Source(
        val file: String?,
        val type: String?
    )

    data class Track(
        val file: String?,
        val label: String?,
        val kind: String?,
        val default: Boolean?
    )

    data class Intro(
        val start: Long?,
        val end: Long?
    )

    data class Outro(
        val start: Long?,
        val end: Long?
    )
}