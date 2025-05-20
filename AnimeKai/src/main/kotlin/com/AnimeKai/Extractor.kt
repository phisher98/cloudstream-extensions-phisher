package com.AnimeKai

import android.os.Build
import androidx.annotation.RequiresApi
import com.AnimeKai.AnimeKai.Companion.getHomeKeys
import com.AnimeKai.AnimeKai.Companion.getMegaKeys
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper


class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.cc"
    override val requiresReferer = true

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val mediaUrl = url.replace("/e/", "/media/").replace("/e2/", "/media/")
        val displayName = referer ?: this.name
        val encodedResult = runCatching {
            app.get(mediaUrl, headers = HEADERS)
                .parsedSafe<AnimeKai.Response>()?.result
        }.getOrNull()

        if (encodedResult == null) {
            Log.d("Phisher", "Encoded result is null")
            return
        }
        val megaKeysSrc= getMegaKeys()
        val decodedJson = AnimekaiDecoder().decode(encodedResult, megaKeysSrc).replace("\\", "")

        val m3u8Data = runCatching {
            Gson().fromJson(decodedJson, AnimeKai.M3U8::class.java)
        }.getOrNull()

        if (m3u8Data == null) {
            Log.d("Error:", "M3U8 data is null")
            return
        }

        m3u8Data.sources.firstOrNull()?.file?.let { m3u8 ->
            M3u8Helper.generateM3u8(displayName, m3u8, mainUrl).forEach(callback)
        } ?: Log.d("Error:", "No sources found in M3U8 data")

        m3u8Data.tracks.forEach { track ->
            track.label?.let {
                subtitleCallback(SubtitleFile(it, track.file))
            }
        }
    }

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
}

data class AutoKai(
    val kai: List<List<String>>,
    val mega: List<List<String>>,
)
