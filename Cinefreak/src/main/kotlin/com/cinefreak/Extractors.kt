package com.cinefreak

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.PixelDrain
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import java.net.URI

class PixelDrainDev : PixelDrain(){
    override var mainUrl = "https://pixeldrain.dev"
}

class Hubcloudone : HubCloud(){
    override var mainUrl = "https://hubcloud.one"
}

class CineCloud : ExtractorApi() {
    override val name = "CineCloud"
    override val mainUrl = "https://new5.cinecloud.site"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document

        val fileSize = doc
            .select("tr")
            .find {
                it.selectFirst("td")
                    ?.text()
                    ?.contains("File Size", true) == true
            }
            ?.select("td.text-right")
            ?.lastOrNull()
            ?.text()
            ?.trim()
            .orEmpty()

        doc.select("a[href]").forEach { a ->

            val text = a.text().trim()

            val baseUrl = getBaseUrl(url)

            val href = a.attr("href").let {
                if (it.startsWith("http")) it
                else baseUrl + it
            }

            when {

                text.contains("FAST CLOUD", true) ||
                        text.contains("[FSL]", true) -> {

                    callback(
                        newExtractorLink(
                            source = "[FSL]",
                            name = "$name [FSL] $fileSize",
                            url = href,
                            type = INFER_TYPE
                        ) {
                            this.quality = getIndexQuality(referer)
                        }
                    )
                }

                text.contains("Cloud [Resumable]", true) -> {

                    val generatedDoc = runCatching {
                        app.get(href).document
                    }.getOrNull() ?: return@forEach

                    generatedDoc.select("a.download-now[href]")
                        .forEach { dl ->

                            val finalLink = dl.absUrl("href")
                                .ifBlank { dl.attr("href") }

                            callback(
                                newExtractorLink(
                                    source = "[ResumeCloud]",
                                    name = "$name [ResumeCloud] $fileSize",
                                    url = finalLink,
                                    type = INFER_TYPE
                                ) {
                                    this.quality = getIndexQuality(referer)
                                }
                            )
                        }
                }

                text.contains("Pixeldrain", true) -> {
                    loadExtractor(
                        href,
                        mainUrl,
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }
}


class Hubdrive : ExtractorApi() {
    override val name = "Hubdrive"
    override val mainUrl = "https://hubdrive.space"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url, timeout = 5000L).document.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud",ignoreCase = true)) HubCloud().getUrl(href,"HubDrive",subtitleCallback,callback)
        else loadExtractor(href,"HubDrive",subtitleCallback, callback)
    }
}


class Neodrive : ExtractorApi() {

    override val name = "Neodrive"
    override val mainUrl = "https://new.neodrive.site"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document

        val quality = getIndexQuality(doc.select("div.mb-8 h2").text())

        doc.select("div.grid a").forEach { element ->

            val text = element.text().trim()
            val href = element.attr("href").trim()

            val fileSize = doc.select("td.info-label")
                .find {
                    it.text().contains("File Size", true)
                }
                ?.nextElementSibling()
                ?.text()
                ?.trim()
                .orEmpty()

            if (href.isBlank()) return@forEach

            when {

                text.contains("FSL", true) -> {

                    callback.invoke(
                        newExtractorLink(
                            source = "FSL",
                            name = "$name [FSL] $fileSize",
                            url = href,
                        ) {
                            this.quality = quality
                        }
                    )
                }

                text.equals("Cloud Download", true) -> {

                    val secondDoc = app.get(
                        "$mainUrl$href",
                        referer = url
                    ).document

                    val finalUrl = Regex(
                        """const\s+downloadUrl\s*=\s*"([^"]+)""""
                    ).find(secondDoc.html())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?: return@forEach

                    callback.invoke(
                        newExtractorLink(
                            source = "Cloud",
                            name = "$name [Cloud] $fileSize",
                            url = finalUrl,
                        ) {
                            this.quality = quality
                        }
                    )
                }
            }
        }
    }
}

open class HubCloud : ExtractorApi() {

    override val name = "Hub-Cloud"
    override var mainUrl: String = runBlocking {
        CinefreakPlugin.getDomains()?.hubcloud ?: "https://hubcloud.foo"
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
            Log.d("Phisher",label)
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



class HUBCDN : ExtractorApi() {
    override val name = "HUBCDN"
    override val mainUrl = "https://hubcdn.*"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        val scriptText = doc.selectFirst("script:containsData(var reurl)")?.data()

        val encodedUrl = Regex("reurl\\s*=\\s*\"([^\"]+)\"")
            .find(scriptText ?: "")
            ?.groupValues?.get(1)
            ?.substringAfter("?r=")

        val decodedUrl = encodedUrl?.let { base64Decode(it) }?.substringAfterLast("link=")


        if (decodedUrl != null) {
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    decodedUrl,
                    INFER_TYPE,
                )
                {
                    this.quality=Qualities.Unknown.value
                }
            )
        }
    }
}

