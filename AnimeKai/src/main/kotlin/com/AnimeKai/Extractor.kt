package com.AnimeKai

import com.google.gson.Gson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.cc"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href = url.replace("/e/", "/media/").replace("/e2/", "/media/")
        val name = referer ?: this.name

        val response = runCatching {
            app.get(href, headers = HEADERS).parsedSafe<AnimeKai.Response>()?.result
        }.getOrNull() ?: return

        val decoded = AnimekaiDecoder().decode(response).replace("\\", "")
        val m3u8Data = runCatching { Gson().fromJson(decoded, AnimeKai.M3U8::class.java) }.getOrNull() ?: return
        val m3u8 = m3u8Data.sources.firstOrNull()?.file ?: return

        M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)

        m3u8Data.tracks.forEach { track ->
            track.label?.let { subtitleCallback(SubtitleFile(it, track.file)) }
        }
    }

    companion object {
        private val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
            "Accept" to "text/html, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-GPC" to "1",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Priority" to "u=0",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "referer" to "https://animekai.to/",
            "Cookie" to "usertype=guest; session=your-session-id; cf_clearance=your-clearance-token"
        )
    }
}
