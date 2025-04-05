package com.hikaritv

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray

class Ghbrisk : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://ghbrisk.com"
}

class Swishsrv : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://swishsrv.com"
}


class Boosterx : Chillx() {
    override val name = "Boosterx"
    override val mainUrl = "https://boosterx.stream"
}

// Why are so mad at us Cracking it
open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "priority" to "u=0, i",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        try {
            val res = app.get(url, referer = mainUrl, headers = headers).toString()

            val encodedString =
                Regex("(?:const|let|var|\\bwindow\\.\\w+)\\s+\\w*\\s*=\\s*'([^']*)'").find(
                    res
                )?.groupValues?.get(1)?.trim() ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }

            // Decrypt the encoded string
            val payload = """
             {
                "input": "$encodedString",
                "key": "ojl,&[y^-{cH!ux1"
             }
            """.trimIndent().toRequestBody("application/json".toMediaTypeOrNull())
            val decryptedData = app.post("https://interesting-zebra-51.deno.dev", requestBody  = payload, headers= mapOf("Content-Type" to "application/json")).text
            // Extract the m3u8 URL from decrypted data
            val m3u8 = Regex("(?:file\\s*:\\s*\\\\|\"file\"\\s*:\\s*)\\s*\"(\\bhttps?://[^\"]+)\\\\\"").find(decryptedData)?.groupValues?.get(1)?.trim() ?: ""
            if (m3u8.isEmpty()) {
                throw Exception("m3u8 URL not found")
            }

            // Prepare headers
            val header = mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT
            )

            // Return the extractor link
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                    this.headers = header
                }
            )

            // Extract and return subtitles
            val subtitles = extractSrtSubtitles(decryptedData)
            subtitles.forEachIndexed { _, (language, url) ->
                subtitleCallback.invoke(SubtitleFile(language, url))
            }

        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """\[(.*?)](https?://[^\s,"]+\.srt)""".toRegex()
        val matches = regex.findAll(subtitle)

        return matches.map { match ->
            val label = match.groupValues[1]
            val file = match.groupValues[2]
            label to file
        }.toList()
    }

    /** End **/
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
        val href=app.get(url).document.selectFirst("iframe")?.attr("src") ?:""
        val res= app.get(href, headers = mapOf("Accept-Language" to "en-US,en;q=0.5","sec-fetch-dest" to "iframe")).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        val m3u8= JsUnpacker(res).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)
        }
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?:"",
                url,
                Qualities.P1080.value,
                type = ExtractorLinkType.M3U8,
            )
        )
    }
}


open class Filesim : ExtractorApi() {
    override val name = "Filesim"
    override val mainUrl = "https://files.im"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var response = app.get(url.replace("/download/", "/e/"), referer = referer)
        val iframe = response.document.selectFirst("iframe")
        if (iframe != null) {
            response = app.get(
                iframe.attr("src"), headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ), referer = response.url
            )
        }

        var script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }

        if (script == null) {
            val iframeUrl = Regex("""<iframe src="(.*?)"""").find(response.text, 0)?.groupValues?.getOrNull(1)
            if (iframeUrl != null) {
                val iframeResponse = app.get(iframeUrl, referer = null, headers = mapOf("Accept-Language" to "en-US,en;q=0.5"))
                script = if (!getPacked(iframeResponse.text).isNullOrEmpty()) {
                    getAndUnpack(iframeResponse.text)
                } else return
            } else return
        }

        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1)
        generateM3u8(name, m3u8 ?: return, mainUrl).forEach(callback)

        // Extract subtitles
        val tracksJson = Regex("tracks:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(script)?.groupValues?.getOrNull(1) ?: return
        val tracksArray = JSONArray("[$tracksJson]")

        for (i in 0 until tracksArray.length()) {
            val track = tracksArray.getJSONObject(i)
            if (track.optString("kind") == "captions") {
                subtitleCallback(
                    SubtitleFile(
                        track.optString("label"),
                        track.optString("file")
                    )
                )
            }
        }
    }
}