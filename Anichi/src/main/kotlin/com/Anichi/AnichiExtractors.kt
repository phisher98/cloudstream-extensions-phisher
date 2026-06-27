package com.Anichi

import com.Anichi.AnichiParser.AnichiVideoApiResponse
import com.Anichi.AnichiParser.LinksQuery
import com.Anichi.AnichiUtils.fixSourceUrls
import com.Anichi.AnichiUtils.fixUrlPath
import com.Anichi.AnichiUtils.getHost
import com.Anichi.AnichiUtils.getM3u8Qualities
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.app
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AnichiExtractors : Anichi() {

    suspend fun showTurnstileDialogAndWait(url: String): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val activity = CommonActivity.activity as? AppCompatActivity
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                var resumed = false
                fun safeResume(token: String?) {
                    if (!resumed) { resumed = true; cont.resume(token) }
                }
                val dialog = AnichiTurnstileDialog(
                    targetUrl = url,
                    onFinished = { token -> safeResume(token) }
                )
                cont.invokeOnCancellation {
                    activity.runOnUiThread { runCatching { dialog.dismissAllowingStateLoss() } }
                }
                dialog.show(activity.supportFragmentManager, "anichi_turnstile")
            }
        }

    suspend fun invokeInternalSources(
        hash: String,
        dubStatus: String,
        episode: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = coroutineScope {
        val fullApiUrl = """$apiUrl?variables={"showId":"$hash","translationType":"$dubStatus","episodeString":"$episode"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$serverHash"}}"""

        val responseText = try {
            val response = app.get(fullApiUrl, headers = headers)
            if (response.code == 403 || response.code == 503 || !response.text.trim().startsWith("{")) {
                Log.d("Anichi", "Cloudflare block detected on GET. Triggering Turnstile Dialog...")
                val token = showTurnstileDialogAndWait("https://allmanga.to/")
                if (token != null) {
                    val postBody = """
                        {
                          "query": "                query(\n                  ${'$'}showId: String!\n                  ${'$'}translationType: VaildTranslationTypeEnumType!\n                  ${'$'}episodeString: String!\n                  ${'$'}search: SearchInput\n                ) {\n                  episode(\n                    showId: ${'$'}showId\n                    translationType: ${'$'}translationType\n                    episodeString: ${'$'}episodeString\n                    search: ${'$'}search\n                  ) {\n                    episodeString\n                    uploadDate\n                    sourceUrls\n                    thumbnail\n                    notes\n                    versionFix\n                    show{\n                      _id\n                        name\n                        englishName\n                        nativeName\n                        airedEnd\n                        thumbnail\n                        airedStart \n                        availableEpisodes\n                                    lastEpisodeInfo\n            lastEpisodeDate\n            type\n            season\n            score\n            episodeDuration\n            disqusIds\n            episodeCount\n\ndescription\nthumbnails\nstatus\naltNames\naverageScore\nrating\nbroadcastInterval\nbanner\nstudios\navailableEpisodesDetail\nnameOnlyString\ngenres\ntags\ncountryOfOrigin\ncharacterCount\nmalId\naniListId\nfranchiseKey\nfranchiseName\n                    }\n                    pageStatus{\n                         _id\nnotes\npageId\nshowId\n    views\n    likesCount\n    commentCount\n    dislikesCount\n    reviewCount\n    userScoreCount\n    userScoreTotalValue\n    userScoreAverValue\n     viewers{\n        firstViewers{\n          viewCount\n          lastWatchedDate\n          user{\n            _id\ndisplayName\npicture\nhideMe\nbrief\ncreatedAt\nbadges\nreputation\n\n          }\n          \n        }\n      recViewers{\n        viewCount\n          lastWatchedDate\n          user{\n            _id\ndisplayName\npicture\nhideMe\nbrief\ncreatedAt\nbadges\nreputation\n\n          }\n          \n       }\n    }\n    \n                    }\n                    \n                  }\n                }\n              ",
                          "variables": {
                            "showId": "$hash",
                            "translationType": "$dubStatus",
                            "episodeString": "$episode",
                            "search": {
                              "allowAdult": false,
                              "allowUnknown": false
                            }
                          },
                          "extensions": {
                            "persistedQuery": {
                              "version": 1,
                              "sha256Hash": "$serverHash"
                            },
                            "captcha": {
                              "token": "$token",
                              "provider": "turnstile"
                            }
                          }
                        }
                    """.trimIndent()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val reqBody = postBody.toRequestBody(mediaType)
                    val postHeaders = headers + mapOf("Content-Type" to "application/json")
                    Log.d("Anichi", "Sending POST request with Turnstile token")
                    app.post(apiUrl, headers = postHeaders, requestBody = reqBody).text
                } else {
                    response.text
                }
            } else {
                response.text
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@coroutineScope
        }

        val sources = try {
            val encrypted = tryParseJson<EncryptedResponse>(responseText)
                ?.data
                ?.tobeparsed

            val finalJson = encrypted
                ?.let { decodeToBeParsed(it) }
                ?: responseText

            tryParseJson<LinksQuery>(finalJson)?.let {
                it.data?.episode?.sourceUrls ?: it.episode?.sourceUrls
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return@coroutineScope

        sources.forEach { source ->
            launch {
                safeApiCall {
                    Log.d("Phisher", "${source.sourceName} ${source.sourceUrl}")

                    val rawLink = source.sourceUrl ?: return@safeApiCall
                    val link = fixSourceUrls(rawLink, source.sourceName) ?: return@safeApiCall

                    if (URI(link).isAbsolute || link.startsWith("//")) {
                        val fixedLink = if (link.startsWith("//")) "https:$link" else link
                        loadCustomExtractor(
                            "Allanime ${source.sourceName}",
                            fixedLink,
                            "",
                            subtitleCallback,
                            callback
                        )
                        loadExtractor(fixedLink, subtitleCallback, callback)
                        /*
                        when {
                            URI(fixedLink).path.contains(".m3u") -> {
                                getM3u8Qualities(fixedLink, serverUrl, host).forEach(callback)
                            }
                            else -> {

                            }
                        }
                         */
                    } else {
                        val decodedlink=if (link.startsWith("--"))
                        {
                            decrypthex(link)
                        }
                        else link
                        val fixedLink = decodedlink.fixUrlPath()
                        val links = try {
                            app.get(fixedLink, headers=headers).parsedSafe<AnichiVideoApiResponse>()?.links ?: emptyList()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            return@safeApiCall
                        }
                        links.forEach { server ->
                            val host = server.link.getHost()
                            when {
                                source.sourceName?.contains("Default") == true &&
                                        (server.resolutionStr == "SUB" || server.resolutionStr == "Alt vo_SUB") -> {
                                    getM3u8Qualities(
                                        server.link,
                                        "https://static.crunchyroll.com/",
                                        source.sourceName
                                    ).forEach(callback)
                                }

                                source.sourceName?.contains("Uns") == true -> {
                                    loadCustomExtractor(
                                        "Allanime ${source.sourceName}",
                                        fixedLink,
                                        "",
                                        subtitleCallback,
                                        callback
                                    )
                                }

                                server.hls == null -> {
                                    callback.invoke(
                                        newExtractorLink(
                                            "Allanime ${host.capitalize()} ${source.sourceName}",
                                            "Allanime ${host.capitalize()} ${source.sourceName}",
                                            server.link,
                                            INFER_TYPE
                                        )
                                        {
                                            this.quality=Qualities.P1080.value
                                        }
                                    )
                                }

                                server.hls -> {
                                    val endpoint = "$apiEndPoint/player?uri=" +
                                            (if (URI(server.link).host.isNotEmpty())
                                                server.link
                                            else apiEndPoint + URI(server.link).path)
                                    getM3u8Qualities(server.link, server.headers?.referer ?: endpoint, host).forEach(callback)
                                }

                                else -> {
                                    server.subtitles?.forEach { sub ->
                                        val lang = SubtitleHelper.fromTagToEnglishLanguageName(sub.lang ?: "") ?: sub.lang.orEmpty()
                                        val src = sub.src ?: return@forEach
                                        subtitleCallback(newSubtitleFile(lang, httpsify(src)))
                                    }
                                }
                            }
                        }
                    }
                }

                // Handle AllAnime direct download

                val downloadUrl = source.downloads?.downloadUrl
                if (!downloadUrl.isNullOrEmpty() && downloadUrl.startsWith("http")) {
                    val downloadId = downloadUrl.substringAfter("id=", "")
                    if (downloadId.isNotEmpty()) {
                        val sourcename = downloadUrl.getHost()
                        val clockApi = "https://allanime.day/apivtwo/clock.json?id=$downloadId"
                        try {
                            val downloads = app.get(clockApi).parsedSafe<AnichiDownload>()?.links ?: emptyList()
                            downloads.forEach { item ->
                                callback.invoke(
                                    newExtractorLink(
                                        "Allanime [${dubStatus.uppercase()}] [$sourcename]",
                                        "Allanime [${dubStatus.uppercase()}] [$sourcename]",
                                        item.link,
                                        INFER_TYPE
                                    )
                                    {
                                        this.quality=Qualities.P1080.value
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun decrypthex(inputStr: String): String {
        val hexString = if (inputStr.startsWith("-")) {
            inputStr.substringAfterLast("-")
        } else {
            inputStr
        }

        val bytes = ByteArray(hexString.length / 2) { i ->
            val hexByte = hexString.substring(i * 2, i * 2 + 2)
            (hexByte.toInt(16) and 0xFF).toByte()
        }

        return bytes.joinToString("") { (it.toInt() xor 56).toChar().toString() }
    }

}

class swiftplayers : StreamWishExtractor() {
    override var mainUrl = "https://swiftplayers.com"
    override var name = "StreamWish"
}


open class StreamWishExtractor : ExtractorApi() {
    override val name = "Streamwish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Referer" to "$mainUrl/",
            "Origin" to "$mainUrl/",
            "User-Agent" to USER_AGENT
        )

        val response = app.get(getEmbedUrl(url), referer = referer)

        val script = when {
            !getPacked(response.text).isNullOrEmpty() -> getAndUnpack(response.text)
            response.document.select("script").any { it.html().contains("jwplayer(\"vplayer\").setup(") } ->
                response.document.select("script").firstOrNull {
                    it.html().contains("jwplayer(\"vplayer\").setup(")
                }?.html()
            else -> response.document.selectFirst("script:containsData(sources:)")?.data()
        }

        var m3u8: String? = null
        if (script != null) {
            m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1)
        }

        if (m3u8 != null) {
            M3u8Helper.generateM3u8(
                name,
                m3u8,
                mainUrl,
                headers = headers
            ).forEach(callback)
        } else {
            val m3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""txt|m3u8"""),
                additionalUrls = listOf(Regex("""txt|m3u8""")),
                useOkhttp = false,
                timeout = 15_000L
            )


            val intercepted = app.get(
                url,
                referer = referer,
                interceptor = m3u8Resolver
            ).url

            if (intercepted.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    intercepted,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
            } else {
                Log.d("Error:", "No m3u8 found in fallback either.")
            }
        }
    }

    private fun getEmbedUrl(url: String): String {
        return if (url.contains("/f/")) {
            val videoId = url.substringAfter("/f/")
            "$mainUrl/$videoId"
        } else {
            url
        }
    }
}


class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Referer" to url,
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:137.0) Gecko/20100101 Firefox/137.0"
        )


        val href = app.get(url,headers).document.selectFirst("iframe")?.attr("src") ?: ""
        val scriptContent = app.get(
            href,
            headers = mapOf("Accept-Language" to "en-US,en;q=0.5", "sec-fetch-dest" to "iframe")
        ).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()

        val m3u8 = JsUnpacker(scriptContent).unpack()?.let { unpacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unpacked)?.groupValues?.get(1)
        }

        if (m3u8 != null) {
            M3u8Helper.generateM3u8(
                name,
                m3u8,
                mainUrl,
                headers = headers
            ).forEach(callback)
        } else {
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val m3u82 = app.get(
                href,
                referer = referer,
                interceptor = resolver
            ).url

            if (m3u82.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    m3u82,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
            } else {
                Log.d("Error", "No m3u8 intercepted in fallback.")
            }
        }
    }
}

class Allanimeups : VidStack() {
    override var mainUrl = "https://allanime.uns.bio"
}

class Bysekoze  : ByseSX() {
    override var name = "Bysekoze"
    override var mainUrl = "https://bysekoze.com"
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

    private suspend fun getPlayback(mainUrl: String): PlaybackRoot? {
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
            "x-embed-parent" to embedFrameUrl,
        )

        val postheaders = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Content-Type" to "application/json",
            "Referer" to embedFrameUrl,
            "X-Embed-Parent" to mainUrl,
            "Priority" to "u=4",
        )

        val response = app.get(playbackUrl, headers = headers)

        return if (response.code == 200) {
            response.parsedSafe<PlaybackRoot>()
        } else {
            val json = """{
  "fingerprint": {}
}"""
            app.post(playbackUrl, headers = postheaders, requestBody = json.toRequestBody())
                .parsedSafe<PlaybackRoot>()
        }
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
        val refererUrl = getBaseUrl(url)
        val playbackRoot = getPlayback(url) ?: return
        val streamUrl  = decryptPlayback(playbackRoot.playback) ?: return


        val headers = mapOf("Referer" to refererUrl)
        M3u8Helper.generateM3u8(
            name,
            streamUrl,
            mainUrl,
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