package com.Anichi

import com.Anichi.AnichiParser.AnichiVideoApiResponse
import com.Anichi.AnichiParser.LinksQuery
import com.Anichi.AnichiUtils.fixSourceUrls
import com.Anichi.AnichiUtils.fixUrlPath
import com.Anichi.AnichiUtils.getHost
import com.Anichi.AnichiUtils.getM3u8Qualities
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.URI

object AnichiExtractors : Anichi() {

    fun invokeInternalSources(
        hash: String,
        dubStatus: String,
        episode: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = runBlocking {
        val fullApiUrl = """$apiUrl?variables={"showId":"$hash","translationType":"$dubStatus","episodeString":"$episode"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$serverHash"}}"""

        val apiResponse = try {
            app.get(fullApiUrl, headers = headers).parsed<LinksQuery>()
        } catch (e: Exception) {
            e.printStackTrace()
            return@runBlocking
        }

        val sources = apiResponse.data?.episode?.sourceUrls ?: return@runBlocking

        sources.forEach { source ->
            launch {
                safeApiCall {
                    //Log.d("Phisher", "${source.sourceName} ${source.sourceUrl}")

                    val rawLink = source.sourceUrl ?: return@safeApiCall
                    val link = fixSourceUrls(rawLink, source.sourceName) ?: return@safeApiCall

                    if (URI(link).isAbsolute || link.startsWith("//")) {
                        val fixedLink = if (link.startsWith("//")) "https:$link" else link
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
                        val fixedLink = link.fixUrlPath()
                        Log.d("Phisher",fixedLink)

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
                                        host
                                    ).forEach(callback)
                                }

                                server.hls == null -> {
                                    callback.invoke(
                                        newExtractorLink(
                                            "Allanime ${host.capitalize()}",
                                            "Allanime ${host.capitalize()}",
                                            server.link,
                                            INFER_TYPE
                                        )
                                        {
                                            this.quality=Qualities.P1080.value
                                        }
                                    )
                                }

                                server.hls == true -> {
                                    val endpoint = "$apiEndPoint/player?uri=" +
                                            (if (URI(server.link).host.isNotEmpty())
                                                server.link
                                            else apiEndPoint + URI(server.link).path)

                                    getM3u8Qualities(server.link, server.headers?.referer ?: endpoint, host).forEach(callback)
                                }

                                else -> {
                                    server.subtitles?.forEach { sub ->
                                        val lang = SubtitleHelper.fromTwoLettersToLanguage(sub.lang ?: "") ?: sub.lang.orEmpty()
                                        val src = sub.src ?: return@forEach
                                        subtitleCallback(SubtitleFile(lang, httpsify(src)))
                                    }
                                }
                            }
                        }
                    }
                }

                // Handle AllAnime direct download
                /*
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
                */
            }
        }
    }


}

class swiftplayers : StreamWishExtractor() {
    override var mainUrl = "https://swiftplayers.com"
    override var name = "StreamWish"
}
