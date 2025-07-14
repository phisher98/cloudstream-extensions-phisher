package com.phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class Videostr : ExtractorApi() {
    override val name = "Videostr"
    override val mainUrl = "https://videostr.net"
    override val requiresReferer = false

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to mainUrl,
            "User-Agent" to USER_AGENT
        )
        val id = url.substringAfterLast("/").substringBefore("?")

        val html= app.get(url, referer = url).text
        val regex = Regex("""\b[a-zA-Z0-9]{48}\b""")
        val hash = regex.find(html)?.value
            ?: throw Exception("No 48-character token found")
        val apiUrl = "$mainUrl/embed-1/v3/e-1/getSources?id=$id&_k=$hash"

        val json = app.get(apiUrl, headers = headers).text
        val response = Gson().fromJson(json, MediaData::class.java)
        val m3u8Url = response.sources.first().file

        val m3u8Headers = mapOf(
            "Referer" to "https://videostr.net/",
        )

        generateM3u8(name, m3u8Url, mainUrl, headers = m3u8Headers).forEach(callback)

        response.tracks
            .filter { it.kind in listOf("captions", "subtitles") }
            .forEach { track ->
                subtitleCallback(SubtitleFile(track.label, track.file))
            }
    }


    data class MediaData(
        val sources: List<Source>,
        val tracks: List<Track>,
        val encrypted: Boolean,
        @JsonProperty("_f")
        val f: String,
        val server: Long,
    )

    data class Source(
        val file: String,
        val type: String,
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val default: Boolean,
        val s: String,
    )
}