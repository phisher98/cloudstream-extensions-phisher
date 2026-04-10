package com.phisher98

import android.annotation.SuppressLint
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.XDMovies.Companion.TMDBIMAGEBASEURL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

class Hubdrive : ExtractorApi() {
    override val name = "Hubdrive"
    override val mainUrl = "https://hubdrive.*"
    override val requiresReferer = false

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url, timeout = 2000).document.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud",ignoreCase = true)) HubCloud().getUrl(href,"HubDrive",subtitleCallback,callback)
        else loadExtractor(href,"HubDrive",subtitleCallback, callback)
    }
}

class HubCloud : ExtractorApi() {

    override val name = "Hub-Cloud"
    override var mainUrl: String = runBlocking {
        XDMoviesProvider.getDomains()?.hubcloud ?: "https://hubcloud.foo"
    }
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val tag = "HubCloud"
        val ref = referer.orEmpty()

        val uri = runCatching { URI(url) }.getOrElse {
            Log.e(tag, "Invalid URL: ${it.message}")
            return
        }

        val realUrl = uri.toString()
        val baseUrl = "${uri.scheme}://${uri.host}"

        val href = runCatching {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val raw = app.get(realUrl).document
                    .selectFirst("#download")
                    ?.attr("href")
                    .orEmpty()

                if (raw.startsWith("http", true)) raw
                else baseUrl.trimEnd('/') + "/" + raw.trimStart('/')
            }
        }.getOrElse {
            Log.e(tag, "Failed to extract href: ${it.message}")
            ""
        }

        if (href.isBlank()) return

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()

        val headerDetails = cleanTitle(header)
        val quality = getIndexQuality(header)

        val labelExtras = buildString {
            if (headerDetails.isNotEmpty()) append("[$headerDetails]")
            if (size.isNotEmpty()) append("[$size]")
        }

        document.select("a.btn").forEach { element ->
            val link = element.attr("href")
            val text = element.ownText()
            val label = text.lowercase()

            when {
                "fsl server" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [FSL Server]",
                            "$ref [FSL Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "download file" in label -> {
                    callback(
                        newExtractorLink(
                            ref,
                            "$ref $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "buzzserver" in label -> {
                    val resp = app.get("$link/download", referer = link, allowRedirects = false)
                    val dlink = resp.headers["hx-redirect"]
                        ?: resp.headers["HX-Redirect"].orEmpty()

                    if (dlink.isNotBlank()) {
                        callback(
                            newExtractorLink(
                                "$ref [BuzzServer]",
                                "$ref [BuzzServer] $labelExtras",
                                dlink
                            ) { this.quality = quality }
                        )
                    } else {
                        Log.w(tag, "BuzzServer: No redirect")
                    }
                }

                "pixeldra" in label || "pixelserver" in label || "pixel server" in label || "pixeldrain" in label -> {
                    val base = getBaseUrl(link)
                    val finalUrl =
                        if ("download" in link) link
                        else "$base/api/file/${link.substringAfterLast("/")}?download"

                    callback(
                        newExtractorLink(
                            "$ref Pixeldrain",
                            "$ref Pixeldrain $labelExtras",
                            finalUrl
                        ) { this.quality = quality }
                    )
                }

                "s3 server" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [S3 Server]",
                            "$ref [S3 Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "fslv2" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [FSLv2]",
                            "$ref [FSLv2] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }

                "mega server" in label -> {
                    callback(
                        newExtractorLink(
                            "$ref [Mega Server]",
                            "$ref [Mega Server] $labelExtras",
                            link
                        ) { this.quality = quality }
                    )
                }
                /*
                "10gbps" in label -> {
                    var current = link

                    repeat(3) {
                        val resp = app.get(current, allowRedirects = false)
                        val loc = resp.headers["location"] ?: return@repeat

                        if ("link=" in loc) {
                            callback(
                                newExtractorLink(
                                    "$ref 10Gbps [Download]",
                                    "$ref 10Gbps [Download] $labelExtras",
                                    loc.substringAfter("link=")
                                ) { this.quality = quality }
                            )
                        }
                        current = loc
                    }

                    Log.e(tag, "10Gbps: Redirect limit reached")
                }

                 */

                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]")
            .find(str.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Qualities.P2160.value
    }

    private fun getBaseUrl(url: String): String {
        return runCatching {
            URI(url).let { "${it.scheme}://${it.host}" }
        }.getOrDefault("")
    }

    private fun cleanTitle(title: String): String {

        val name = title.replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")

        val normalized = name
            .replace(Regex("WEB[-_. ]?DL", RegexOption.IGNORE_CASE), "WEB-DL")
            .replace(Regex("WEB[-_. ]?RIP", RegexOption.IGNORE_CASE), "WEBRIP")
            .replace(Regex("H[ .]?265", RegexOption.IGNORE_CASE), "H265")
            .replace(Regex("H[ .]?264", RegexOption.IGNORE_CASE), "H264")
            .replace(Regex("DDP[ .]?([0-9]\\.[0-9])", RegexOption.IGNORE_CASE), "DDP$1")

        val parts = normalized.split(" ", "_", ".")

        val sourceTags = setOf(
            "WEB-DL", "WEBRIP", "BLURAY", "HDRIP",
            "DVDRIP", "HDTV", "CAM", "TS", "BRRIP", "BDRIP"
        )

        val codecTags = setOf("H264", "H265", "X264", "X265", "HEVC", "AVC")

        val audioTags = setOf("AAC", "AC3", "DTS", "MP3", "FLAC", "DD", "DDP", "EAC3")

        val audioExtras = setOf("ATMOS")

        val hdrTags = setOf("SDR","HDR", "HDR10", "HDR10+", "DV", "DOLBYVISION")

        val filtered = parts.mapNotNull { part ->
            val p = part.uppercase()

            when {
                sourceTags.contains(p) -> p
                codecTags.contains(p) -> p
                audioTags.any { p.startsWith(it) } -> p
                audioExtras.contains(p) -> p
                hdrTags.contains(p) -> {
                    when (p) {
                        "DV", "DOLBYVISION" -> "DOLBYVISION"
                        else -> p
                    }
                }
                p == "NF" || p == "CR" -> p
                else -> null
            }
        }

        return filtered.distinct().joinToString(" ")
    }
}


class XdMoviesExtractor : ExtractorApi() {

    override val name = "XdMoviesExtractor"
    override val mainUrl = "https://link.xdmovies.wtf"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val redirect = bypassXD(url) ?: return
        loadExtractor(redirect, "HubCloud", subtitleCallback, callback)
    }
}


fun parseTmdbActors(jsonText: String?): List<ActorData> {
    if (jsonText.isNullOrBlank()) return emptyList()

    val list = mutableListOf<ActorData>()
    val root = JSONObject(jsonText)
    val castArr = root.optJSONArray("cast") ?: return emptyList()

    for (i in 0 until castArr.length()) {
        val c = castArr.optJSONObject(i) ?: continue

        val name = c.optString("name").takeIf { it.isNotBlank() }
            ?: c.optString("original_name").orEmpty()

        val img = c.optString("profile_path")
            .takeIf { it.isNotBlank() }
            ?.let { "$TMDBIMAGEBASEURL$it" }

        val role = c.optString("character").takeIf { it.isNotBlank() }

        list += ActorData(
            Actor(name, img),
            roleString = role
        )
    }
    return list
}


suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {

    if (tmdbId == null) return null

    val url = if (type == TvType.Movie)
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    else
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()

    fun path(o: JSONObject) = o.optString("file_path")
    fun isSvg(o: JSONObject) = path(o).endsWith(".svg", true)
    fun urlOf(o: JSONObject) = "https://image.tmdb.org/t/p/w500${path(o)}"

    // Language match
    var svgFallback: JSONObject? = null

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        val p = path(logo)
        if (p.isBlank()) continue

        val l = logo.optString("iso_639_1").trim().lowercase()
        if (l == lang) {
            if (!isSvg(logo)) return urlOf(logo)
            if (svgFallback == null) svgFallback = logo
        }
    }
    svgFallback?.let { return urlOf(it) }

    // Highest voted fallback
    var best: JSONObject? = null
    var bestSvg: JSONObject? = null

    fun voted(o: JSONObject) = o.optDouble("vote_average", 0.0) > 0 && o.optInt("vote_count", 0) > 0

    fun better(a: JSONObject?, b: JSONObject): Boolean {
        if (a == null) return true
        val aAvg = a.optDouble("vote_average", 0.0)
        val aCnt = a.optInt("vote_count", 0)
        val bAvg = b.optDouble("vote_average", 0.0)
        val bCnt = b.optInt("vote_count", 0)
        return bAvg > aAvg || (bAvg == aAvg && bCnt > aCnt)
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (!voted(logo)) continue

        if (isSvg(logo)) {
            if (better(bestSvg, logo)) bestSvg = logo
        } else {
            if (better(best, logo)) best = logo
        }
    }

    best?.let { return urlOf(it) }
    bestSvg?.let { return urlOf(it) }

    // No language match & no voted logos
    return null
}

fun generateBrowserFingerprint(): String {
    val components = listOf(
        "1920x1080x24",
        "Asia/Kolkata",
        "en-US",
        "Win32",
        "8",
        "8",
        "canvas_stub_xdmovies",
        "ANGLE (NVIDIA)",
        "no_touch",
        "3",
        "true",
        "unset"
    )

    val raw = components.joinToString("|||")
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }.take(32)
}

private fun getBaseUrl(url: String): String {
    return URI(url).let { "${it.scheme}://${it.host}" }
}

suspend fun bypassXD(url: String): String? {
    // Follow initial redirect to get actual bypass URL
    val redirect = app.get(url, allowRedirects = false)
        .headers["location"] ?: return null

    val baseUrl = getBaseUrl(redirect)
    val code = redirect.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: return null
    val fingerprint = generateBrowserFingerprint()

    val mouseData = mapOf(
        "eventCount"    to 220,
        "moveCount"     to 185,
        "clickCount"    to 3,
        "totalDistance" to 3800,
        "hasMovement"   to true,
        "duration"      to 27000
    )

    val baseHeaders = mapOf(
        "User-Agent"      to USER_AGENT,
        "Accept"          to "*/*",
        "Origin"          to baseUrl,
        "Referer"         to "$baseUrl/r/$code",
        "sec-fetch-site"  to "same-origin",
        "sec-fetch-mode"  to "cors",
        "sec-fetch-dest"  to "empty"
    )

    // ── STEP 1: Create session ────────────────────────────────────────────────
    val sessionJson = try {
        JSONObject(
            app.post(
                "$baseUrl/api/session",
                json = mapOf(
                    "code"        to code,
                    "fingerprint" to fingerprint,
                    "mouseData"   to mouseData
                ),
                headers = baseHeaders
            ).text
        )
    } catch (_: Exception) { return null }

    val sessionId  = sessionJson.optString("sessionId").takeIf { it.isNotEmpty() } ?: return null

    val cookieHeaders = baseHeaders + mapOf("Cookie" to "sid=$sessionId")

    // ── STEP 2: Rebind (simulates step-2 page reload) ────────────────────────
    val rebindJson = try {
        JSONObject(
            app.post(
                "$baseUrl/api/session/rebind",
                json = mapOf("fingerprint" to fingerprint),
                headers = cookieHeaders
            ).text
        )
    } catch (_: Exception) { return null }

    val rebindToken = rebindJson.optString("token").takeIf { it.isNotEmpty() } ?: return null

    // ── STEP 3: WebSocket heartbeats ─────────────────────────────────────────
    // Server only advances visible-time counter when it receives
    // "heartbeat" events over the Socket.IO WebSocket while
    // visibility is "visible". A plain delay() does nothing.
    val wsBaseUrl = baseUrl
        .replace("https://", "wss://")
        .replace("http://",  "ws://")

    val visibleTimeDone = CompletableDeferred<Unit>()
    val okHttpClient    = OkHttpClient()

    val wsRequest = Request.Builder()
        .url("$wsBaseUrl/socket.io/?EIO=4&transport=websocket")
        .addHeader("Origin",     baseUrl)
        .addHeader("Cookie",     "sid=$sessionId")
        .addHeader("User-Agent", USER_AGENT)
        .build()

    var heartbeatJob: kotlinx.coroutines.Job? = null

    val webSocket = okHttpClient.newWebSocket(wsRequest, object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Socket.IO: connect to default namespace
            webSocket.send("40")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when {
                // Socket.IO ping → reply with pong to keep connection alive
                text == "2" -> webSocket.send("3")

                // Namespace connected → bind session + mark visible + start heartbeats
                text.startsWith("40") -> {
                    webSocket.send("""42["bind","$rebindToken"]""")
                    webSocket.send("""42["visibility","visible"]""")

                    heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
                        var elapsed = 0
                        while (elapsed < 28) {
                            delay(1000)
                            elapsed++

                            webSocket.send("""42["heartbeat"]""")
                            webSocket.send(
                                """42["mouseActivity",${
                                    JSONObject(
                                        mouseData.toMutableMap().apply {
                                            put("duration", elapsed * 1000)
                                        }
                                    )
                                }]"""
                            )
                        }
                        visibleTimeDone.complete(Unit)
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            visibleTimeDone.completeExceptionally(t)
        }
    })

    // Wait for 28 heartbeats (≈ 28 seconds of visible time)
    try {
        withTimeout(40_000) { visibleTimeDone.await() }
    } catch (_: Exception) {
        return null
    } finally {
        heartbeatJob?.cancel()
        webSocket.close(1000, null)
        okHttpClient.dispatcher.executorService.shutdown()
    }

    // ── STEP 4: Complete session — retry until token returned ─────────────────
    var finalToken: String? = null

    repeat(5) { attempt ->
        if (finalToken != null) return@repeat
        try {
            val json = JSONObject(
                app.post(
                    "$baseUrl/api/session/complete",
                    json = mapOf(
                        "fingerprint" to fingerprint,
                        "mouseData"   to mouseData.toMutableMap().apply {
                            put("duration", 28000 + attempt * 2000)
                        },
                        "honeypot"    to ""   // must be empty — bots fill this
                    ),
                    headers = cookieHeaders
                ).text
            )
            json.optString("token").takeIf { it.isNotEmpty() }?.let { finalToken = it }
        } catch (_: Exception) { }

        if (finalToken == null) delay(2000)
    }

    val token = finalToken ?: return null

    // ── STEP 5: Final redirect ────────────────────────────────────────────────
    return app.get(
        "$baseUrl/go/$sessionId?t=$token",
        allowRedirects = false,
        headers = cookieHeaders
    ).headers["location"]
}