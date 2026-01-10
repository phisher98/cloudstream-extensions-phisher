package com.phisher98

import android.annotation.SuppressLint
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.XDMovies.Companion.TMDBIMAGEBASEURL
import org.json.JSONObject
import java.net.URI

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
        val href=app.get(url, timeout = 2000).documentLarge.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud",ignoreCase = true)) HubCloud().getUrl(href,"HubDrive",subtitleCallback,callback)
        else loadExtractor(href,"HubDrive",subtitleCallback, callback)
    }
}

class HubCloud : ExtractorApi() {

    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.*"
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
        val parts = title.split(".", "-", "_")

        val qualityTags = listOf(
            "WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV",
            "CAM", "TS", "R5", "DVDScr", "BRRip", "BDRip", "DVD", "PDTV", "HD"
        )

        val audioTags = listOf("AAC", "AC3", "DTS", "MP3", "FLAC", "DD5", "EAC3", "Atmos")
        val subTags = listOf("ESub", "ESubs", "Subs", "MultiSub", "NoSub", "EnglishSub", "HindiSub")
        val codecTags = listOf("x264", "x265", "H264", "HEVC", "AVC")

        val startIndex = parts.indexOfFirst { part ->
            qualityTags.any { part.contains(it, true) }
        }

        val endIndex = parts.indexOfLast { part ->
            subTags.any { part.contains(it, true) } ||
                    audioTags.any { part.contains(it, true) } ||
                    codecTags.any { part.contains(it, true) }
        }

        return when {
            startIndex != -1 && endIndex != -1 && endIndex >= startIndex ->
                parts.subList(startIndex, endIndex + 1).joinToString(".")

            startIndex != -1 ->
                parts.subList(startIndex, parts.size).joinToString(".")

            else ->
                parts.takeLast(3).joinToString(".")
        }
    }
}


class XdMoviesExtractor : ExtractorApi() {

    override val name = "XdMoviesExtractor"
    override val mainUrl = "https://link.xdmovies.site"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val redirect = runCatching {
            app.get(url, allowRedirects = false).headers["location"]
        }.getOrNull()

        redirect?.let {
            loadExtractor(it, "HubCloud", subtitleCallback, callback)
        }
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

    val appLang = appLangCode
        ?.substringBefore("-")
        ?.lowercase()

    val url = if (type == TvType.Movie) {
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    } else {
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"
    }

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull()
        ?: return null

    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    fun logoUrlAt(i: Int): String = "https://image.tmdb.org/t/p/w500${logos.getJSONObject(i).optString("file_path")}"

    if (!appLang.isNullOrBlank()) {
        for (i in 0 until logos.length()) {
            val logo = logos.optJSONObject(i) ?: continue
            if (logo.optString("iso_639_1") == appLang) {
                return logoUrlAt(i)
            }
        }
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (logo.optString("iso_639_1") == "en") {
            return logoUrlAt(i)
        }
    }

    return logoUrlAt(0)
}