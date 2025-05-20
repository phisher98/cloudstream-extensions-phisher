package com.Phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL
import kotlin.text.Regex

class Hubdrive : ExtractorApi() {
    override val name = "Hubdrive"
    override val mainUrl = "https://hubdrive.fit"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url).document.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud"))
        {
            HubCloud().getUrl(href,"HubDrive",subtitleCallback, callback)
        }
        else
        loadExtractor(href,"HubDrive",subtitleCallback, callback)
    }
}


open class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.ink"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val realUrl = replaceHubclouddomain(url)
        val href = if (realUrl.contains("hubcloud.php")) {
            realUrl
        } else {
            val regex = "var url = '([^']*)'".toRegex()
            val regexdata=app.get(realUrl).document.selectFirst("script:containsData(url)")?.toString() ?: ""
            regex.find(regexdata)?.groupValues?.get(1).orEmpty()
        }
        if (href.isEmpty()) {
            Log.d("Error", "Not Found")
            return
        }

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text()
        val header = document.selectFirst("div.card-header")?.text()

        document.select("div.card-body a.btn").forEach { linkElement ->
            val link = linkElement.attr("href")
            val quality = getIndexQuality(header)

            when {
                link.contains("www-google-com") -> Log.d("Error:", "Not Found")

                link.contains("technorozen.workers.dev") -> {
                    callback(
                        newExtractorLink(
                            "$source 10GB Server",
                            "$source 10GB Server $size",
                            url = getGBurl(link)
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("pixeldra.in") || link.contains("pixeldrain") -> {
                    callback(
                        newExtractorLink(
                            "$source Pixeldrain",
                            "$source Pixeldrain $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("buzzheavier") -> {
                    callback(
                        newExtractorLink(
                            "$source Buzzheavier",
                            "$source Buzzheavier $size",
                            url = "$link/download"
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains(".dev") -> {
                    callback(
                        newExtractorLink(
                            "$source Hub-Cloud",
                            "$source Hub-Cloud $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("fastdl.lol") -> {
                    callback(
                        newExtractorLink(
                            "$source [FSL] Hub-Cloud",
                            "$source [FSL] Hub-Cloud $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("hubcdn.xyz") -> {
                    callback(
                        newExtractorLink(
                            "$source [File] Hub-Cloud",
                            "$source [File] Hub-Cloud $size",
                            url = link
                        ) {
                            this.quality = quality
                        }
                    )
                }

                link.contains("gofile.io") -> {
                    loadCustomExtractor(source.orEmpty(), link, "Pixeldrain", subtitleCallback, callback)
                }

                else -> Log.d("Error:", "No Server Match Found")
            }
        }
    }

    private fun getIndexQuality(str: String?) =
        Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P2160.value

    private suspend fun getGBurl(url: String): String =
        app.get(url).document.selectFirst("#vd")?.attr("href").orEmpty()
}

fun replaceHubclouddomain(url: String): String {
    return try {
        val originalUrl = URL(url)
        val domainParts = originalUrl.host.split(".").toMutableList()
        if (domainParts.size > 1) {
            domainParts[domainParts.lastIndex] = "dad"
            val newDomain = domainParts.joinToString(".")

            // Construct the new URL with the updated domain
            URL(originalUrl.protocol, newDomain, originalUrl.port, originalUrl.file).toString()
        } else {
            throw IllegalArgumentException("Invalid domain structure in URL")
        }
    } catch (e: Exception) {
        "Invalid URL: ${e.message}"
    }
}


suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
                    link.url,
                ) {
                    this.quality = when {
                        link.name == "VidSrc" -> Qualities.P1080.value
                        link.type == ExtractorLinkType.M3U8 -> link.quality
                        else -> quality ?: link.quality
                    }
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}