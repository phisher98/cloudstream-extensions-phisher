package com.phisher98

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
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
        val responsenonce = app.get(url, headers = headers).text
        val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(responsenonce)
        val match2 = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""")
            .find(responsenonce)

        val nonce = match1?.value ?: match2?.let {
            it.groupValues[1] + it.groupValues[2] + it.groupValues[3]
        } ?: throw Exception("Nonce not found")

        val apiUrl = "$mainUrl/embed-1/v3/e-1/getSources?id=$id&_k=$nonce"
        val gson = Gson()
        val response = try {
            val json = app.get(apiUrl, headers).text
            gson.fromJson(json, VideostrResponse::class.java)
        } catch (e: Exception) {
            throw Exception("Failed to parse VideostrResponse: ${e.message}")
        }

        Log.d("Videostr", "Parsed VideostrResponse: $response")

        val key = try {
            val keyJson = app.get(
                "https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json"
            ).text
            gson.fromJson(keyJson, Megakey::class.java)?.vidstr
        } catch (e: Exception) {
            throw Exception("Failed to parse Megakey: ${e.message}")
        } ?: throw Exception("Decryption key not found")

        val encodedSource = response.sources.firstOrNull()?.file
            ?: throw Exception("No sources found in response")

        val m3u8: String = if (".m3u8" in encodedSource) {
            encodedSource
        } else {
            val decodeUrl =
                "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"

            val fullUrl = buildString {
                append(decodeUrl)
                append("?encrypted_data=").append(URLEncoder.encode(encodedSource, "UTF-8"))
                append("&nonce=").append(URLEncoder.encode(nonce, "UTF-8"))
                append("&secret=").append(URLEncoder.encode(key, "UTF-8"))
            }

            val decryptedResponse = app.get(fullUrl).text
            Regex("\"file\":\"(.*?)\"")
                .find(decryptedResponse)
                ?.groupValues?.get(1)
                ?: throw Exception("Video URL not found in decrypted response")
        }

        val m3u8headers = mapOf(
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl
        )

        try {
            generateM3u8(name, m3u8, mainUrl, headers = m3u8headers).forEach(callback)
        } catch (e: Exception) {
            Log.e("Videostr", "Error generating M3U8: ${e.message}")
        }

        response.tracks.forEach { track ->
            if (track.kind == "captions" || track.kind == "subtitles") {
                subtitleCallback(
                    newSubtitleFile(
                        track.label,
                        track.file
                    )
                )
            }
        }
    }

    data class VideostrResponse(
        val sources: List<VideostrSource>,
        val tracks: List<Track>,
        val encrypted: Boolean,
        @SerializedName("_f") val f: String,
        val server: Long,
    )

    data class VideostrSource(
        val file: String,
        val type: String,
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val s: String,
        val default: Boolean?,
    )

    data class Megakey(
        val rabbit: String,
        val mega: String,
        val vidstr: String
    )
}
