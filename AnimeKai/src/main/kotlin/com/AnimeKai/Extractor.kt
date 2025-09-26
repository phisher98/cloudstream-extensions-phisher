package com.AnimeKai

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.phisher98.BuildConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


//Thanks to https://github.com/AzartX47/EncDecEndpoints
class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.live"
    override val requiresReferer = true

    companion object {
        private val HEADERS = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
                "Accept" to "text/html, *//*; q=0.01",
                "Accept-Language" to "en-US,en;q=0.5",
                "Sec-GPC" to "1",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
                "Priority" to "u=0",
                "Pragma" to "no-cache",
                "Cache-Control" to "no-cache",
                "referer" to "https://animekai.to/",
        )
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val mediaUrl = url.replace("/e/", "/media/").replace("/e2/", "/media/")
        val displayName = referer ?: this.name
        
        val encodedResult = app.get(mediaUrl, headers = HEADERS)
        .parsedSafe<AnimeKaiResponse>()
        ?.result

        if (encodedResult == null) return

        val body = """
        {
        "text": "$encodedResult",
        "agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
        }
        """.trimIndent()
            .trim()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val m3u8Data=app.post(BuildConfig.KAIDEC, requestBody = body).parsedSafe<AnimeKaiM3U8>()
        if (m3u8Data == null) {
            Log.d("Phisher", "Encoded result is null")
            return
        }


        m3u8Data.result.sources.firstOrNull()?.file?.let { m3u8 ->
            M3u8Helper.generateM3u8(displayName, m3u8, mainUrl).forEach(callback)
        } ?: Log.d("Error:", "No sources found in M3U8 data")

        m3u8Data.result.tracks.forEach { track ->
            track.label?.let {
                subtitleCallback(SubtitleFile(it.trim(), track.file))
            }
        }
      }

    data class AnimeKaiResponse(
        @JsonProperty("status") val status: Int,
        @JsonProperty("result") val result: String
    )

    data class AnimeKaiM3U8(
        val status: Long,
        val result: AnimekaiResult,
    )

    data class AnimekaiResult(
        val sources: List<AnimekaiSource>,
        val tracks: List<AnimekaiTrack>,
        val download: String,
    )

    data class AnimekaiSource(
        val file: String,
    )

    data class AnimekaiTrack(
        val file: String,
        val kind: String,
        val label: String? = null,
        val default: Boolean? = null
    )

}