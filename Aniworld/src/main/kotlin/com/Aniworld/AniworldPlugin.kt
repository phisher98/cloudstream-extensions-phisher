package com.Aniworld

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@CloudstreamPlugin
class AniworldPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Aniworld())
        registerMainAPI(Serienstream())
        registerExtractorAPI(Dooood())
        registerExtractorAPI(Vidmoly())
        registerExtractorAPI(FileMoon())
    }

    open class ByseSX : ExtractorApi() {
        override var name = "Byse"
        override var mainUrl = "https://byse.sx"
        override val requiresReferer = true

        private fun b64UrlDecode(s: String): ByteArray {
            val fixed = s.replace('-', '+').replace('_', '/')
            val pad = (4 - fixed.length % 4) % 4
            return base64DecodeArray(fixed + "=".repeat(pad))
        }

        private fun getBaseUrl(url: String): String {
            return URI(url).let { "${it.scheme}://${it.host}" }
        }

        private fun getCodeFromUrl(url: String): String {
            val path = URI(url).path ?: ""
            return path.trimEnd('/').substringAfterLast('/')
        }

        private suspend fun getDetails(mainUrl: String): DetailsRoot? {
            val base = getBaseUrl(mainUrl)
            val code = getCodeFromUrl(mainUrl)
            val url = "$base/api/videos/$code/embed/details"
            return app.get(url).parsedSafe<DetailsRoot>()
        }

        private suspend fun getPlayback(mainUrl: String): Pair<PlaybackRoot, String>? {
            val details = getDetails(mainUrl) ?: return null
            val embedFrameUrl = details.embedFrameUrl
            val embedBase = getBaseUrl(embedFrameUrl)
            val code = getCodeFromUrl(embedFrameUrl)
            val playbackUrl = "$embedBase/api/videos/$code/embed/playback"

            val headers = mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "priority" to "u=1, i",
                "referer" to embedFrameUrl,
                "x-embed-parent" to mainUrl.replace("/d/", "/e/")
            )

            val root = app.post(
                playbackUrl,
                headers = headers
            ).parsedSafe<PlaybackRoot>() ?: return null

            return root to embedFrameUrl
        }


        private fun buildAesKey(playback: Playback): ByteArray {
            val p1 = b64UrlDecode(playback.keyParts[0])
            val p2 = b64UrlDecode(playback.keyParts[1])
            return p1 + p2
        }

        private fun decryptPlayback(playback: Playback): String? {
            val keyBytes = buildAesKey(playback)
            val ivBytes = b64UrlDecode(playback.iv)
            val cipherBytes = b64UrlDecode(playback.payload)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, ivBytes)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherBytes)
            var jsonStr = String(plainBytes, StandardCharsets.UTF_8)

            if (jsonStr.startsWith("\uFEFF")) jsonStr = jsonStr.substring(1)

            val root = try {
                tryParseJson<PlaybackDecrypt>((jsonStr))
            } catch (_: Exception) {
                return null
            }

            return root?.sources?.firstOrNull()?.url
        }


        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            val (playbackRoot, m3u8referer) = getPlayback(url) ?: return

            val streamUrl  = decryptPlayback(playbackRoot.playback) ?: return


            val headers = mapOf("Referer" to m3u8referer)
            M3u8Helper.generateM3u8(
                "$referer",
                streamUrl,
                m3u8referer,
                headers = headers
            ).forEach(callback)
        }
    }

    data class DetailsRoot(
        val id: Long,
        val code: String,
        val title: String,
        @JsonProperty("poster_url")
        val posterUrl: String,
        val description: String,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("owner_private")
        val ownerPrivate: Boolean,
        @JsonProperty("embed_frame_url")
        val embedFrameUrl: String,
    )

    data class PlaybackRoot(
        val playback: Playback,
    )

    data class Playback(
        val algorithm: String,
        val iv: String,
        val payload: String,
        @JsonProperty("key_parts")
        val keyParts: List<String>,
        @JsonProperty("expires_at")
        val expiresAt: String,
        @JsonProperty("decrypt_keys")
        val decryptKeys: DecryptKeys,
        val iv2: String,
        val payload2: String,
    )

    data class DecryptKeys(
        @JsonProperty("edge_1")
        val edge1: String,
        @JsonProperty("edge_2")
        val edge2: String,
        @JsonProperty("legacy_fallback")
        val legacyFallback: String,
    )

    data class PlaybackDecrypt(
        val sources: List<PlaybackDecryptSource>,
    )

    data class PlaybackDecryptSource(
        val quality: String,
        val label: String,
        @JsonProperty("mime_type")
        val mimeType: String,
        val url: String,
        @JsonProperty("bitrate_kbps")
        val bitrateKbps: Long,
        val height: Any?,
    )

}