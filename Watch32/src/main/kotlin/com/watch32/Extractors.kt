package com.watch32

import com.google.gson.Gson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URLEncoder

class Videostr : ExtractorApi() {
    override val name = "Videostr"
    override val mainUrl = "https://videostr.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to mainUrl
        )

        val id = url.substringAfterLast("/").substringBefore("?")
        val html = app.get(url, headers = headers).text

        val nonce = Regex("""\b[a-zA-Z0-9]{48}\b""").find(html)?.value
            ?: Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""")
                .find(html)?.let { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            ?: throw Exception("Nonce not found")

        val apiUrl = "$mainUrl/embed-1/v3/e-1/getSources?id=$id&_k=$nonce"
        val response = Gson().fromJson(
            app.get(apiUrl, headers).text,
            VideostrResponse::class.java
        )

        val encodedSource = response.sources.firstOrNull()?.file
            ?: throw Exception("No sources found")

        val m3u8 = if (".m3u8" in encodedSource) {
            encodedSource
        } else {
            val key = Gson().fromJson(
                app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json").text,
                Megakey::class.java
            ).vidstr

            val decodeUrl =
                "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"

            val fullUrl =
                "$decodeUrl?encrypted_data=${URLEncoder.encode(encodedSource,"UTF-8")}&nonce=${URLEncoder.encode(nonce,"UTF-8")}&secret=${URLEncoder.encode(key,"UTF-8")}"

            Regex("\"file\":\"(.*?)\"")
                .find(app.get(fullUrl).text)
                ?.groupValues?.get(1)
                ?: throw Exception("Video URL not found")
        }

        generateM3u8(
            name,
            m3u8,
            mainUrl,
            headers = mapOf("Referer" to "$mainUrl/", "Origin" to mainUrl)
        ).forEach(callback)

        response.tracks.forEach {
            if (it.kind == "captions" || it.kind == "subtitles") {
                subtitleCallback(newSubtitleFile(it.label, it.file))
            }
        }
    }

    data class VideostrResponse(
        val sources: List<VideostrSource>,
        val tracks: List<Track>
    )

    data class VideostrSource(
        val file: String
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String
    )

    data class Megakey(
        val vidstr: String
    )
}