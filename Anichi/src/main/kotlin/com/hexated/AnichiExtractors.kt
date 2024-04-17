package com.hexated

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

object AnichiExtractors : Anichi() {

    suspend fun invokeInternalSources(
        hash: String,
        dubStatus: String,
        episode: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val apiUrl =
            """$apiUrl?variables={"showId":"$hash","translationType":"$dubStatus","episodeString":"$episode"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$serverHash"}}"""
        val apiResponse = app.get(apiUrl, headers = headers).parsed<LinksQuery>()

        apiResponse.data?.episode?.sourceUrls?.apmap { source ->
            safeApiCall {
                val link = fixSourceUrls(source.sourceUrl ?: return@safeApiCall, source.sourceName)
                    ?: return@safeApiCall
                if (URI(link).isAbsolute || link.startsWith("//")) {
                    val fixedLink = if (link.startsWith("//")) "https:$link" else link
                    val host = link.getHost()

                    when {
                        fixedLink.contains(Regex("(?i)playtaku|gogo")) || source.sourceName == "Vid-mp4" -> {
                            invokeGogo(fixedLink, subtitleCallback, callback)
                        }

                        embedIsBlacklisted(fixedLink) -> {
                            loadExtractor(fixedLink, subtitleCallback, callback)
                        }

                        URI(fixedLink).path.contains(".m3u") -> {
                            getM3u8Qualities(fixedLink, serverUrl, host).forEach(callback)
                        }

                        else -> {
                            callback(
                                ExtractorLink(
                                    name,
                                    host,
                                    fixedLink,
                                    serverUrl,
                                    Qualities.P1080.value,
                                    false
                                )
                            )
                        }
                    }
                } else {
                    val fixedLink = link.fixUrlPath()
                    val links = app.get(fixedLink).parsedSafe<AnichiVideoApiResponse>()?.links
                        ?: emptyList()
                    links.forEach { server ->
                        val host = server.link.getHost()
                        when {
                            source.sourceName?.contains("Default") == true -> {
                                if (server.resolutionStr == "SUB" || server.resolutionStr == "Alt vo_SUB") {
                                    getM3u8Qualities(
                                        server.link,
                                        "https://static.crunchyroll.com/",
                                        host,
                                    ).forEach(callback)
                                }
                            }

                            server.hls != null && server.hls -> {
                                getM3u8Qualities(
                                    server.link,
                                    "$apiEndPoint/player?uri=" + (if (URI(server.link).host.isNotEmpty()) server.link else apiEndPoint + URI(
                                        server.link
                                    ).path),
                                    host
                                ).forEach(callback)
                            }

                            else -> {
                                callback(
                                    ExtractorLink(
                                        host,
                                        host,
                                        server.link,
                                        "$apiEndPoint/player?uri=" + (if (URI(server.link).host.isNotEmpty()) server.link else apiEndPoint + URI(
                                            server.link
                                        ).path),
                                        server.resolutionStr.removeSuffix("p").toIntOrNull()
                                            ?: Qualities.P1080.value,
                                        false,
                                        isDash = server.resolutionStr == "Dash 1"
                                    )
                                )
                                server.subtitles?.map { sub ->
                                    subtitleCallback.invoke(
                                        SubtitleFile(
                                            SubtitleHelper.fromTwoLettersToLanguage(sub.lang ?: "")
                                                ?: sub.lang ?: "",
                                            httpsify(sub.src ?: return@map)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun invokeGogo(
        link: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframe = app.get(link)
        val iframeDoc = iframe.document
        argamap({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    val status = element.attr("data-status") ?: return@forEach
                    if (status != "1") return@forEach
                    val extractorData = element.attr("data-video") ?: return@forEach
                    loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            val iv = "3134003223491201"
            val secretKey = "37911490979715163134003223491201"
            val secretDecryptKey = "54674138327930866480207815084989"
            GogoHelper.extractVidstream(
                iframe.url,
                "Gogoanime",
                callback,
                iv,
                secretKey,
                secretDecryptKey,
                isUsingAdaptiveKeys = false,
                isUsingAdaptiveData = true,
                iframeDocument = iframeDoc
            )
        })
    }

}