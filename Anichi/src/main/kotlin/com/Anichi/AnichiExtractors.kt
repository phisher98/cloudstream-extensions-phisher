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
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
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
                    Log.d("Phisher", "${source.sourceName} ${source.sourceUrl}")

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
