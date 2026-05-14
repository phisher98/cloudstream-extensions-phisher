package com.phisher98

import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URI

class vidcloudupns : VidStack() {
    override var mainUrl = "https://vidcloud.upns.ink"
}

class Techinmind: GDMirrorbot() {
    override var name = "Techinmind Cloud AIO"
    override var mainUrl = "https://stream.techinmind.space"
    override var requiresReferer = true
}

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (sid, host) = if (!url.contains("key=")) {
            Pair(url.substringAfterLast("embed/"), getBaseUrl(app.get(url).url))
        } else {
            var pageText = app.get(url).text
            val finalId = Regex("""FinalID\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val myKey = Regex("""myKey\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val idType = Regex("""idType\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1) ?: "imdbid"
            val baseUrl = Regex("""let\s+baseUrl\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val hostUrl = baseUrl?.let { getBaseUrl(it) }

            if (finalId != null && myKey != null) {
                val apiUrl = if (url.contains("/tv/")) {
                    val season = Regex("""/tv/\d+/(\d+)/""").find(url)?.groupValues?.get(1) ?: "1"
                    val episode = Regex("""/tv/\d+/\d+/(\d+)""").find(url)?.groupValues?.get(1) ?: "1"
                    "$mainUrl/myseriesapi?tmdbid=$finalId&season=$season&epname=$episode&key=$myKey"
                } else {
                    "$mainUrl/mymovieapi?$idType=$finalId&key=$myKey"
                }
                pageText = app.get(apiUrl).text
            }

            val jsonElement = JsonParser.parseString(pageText)
            if (!jsonElement.isJsonObject) return
            val jsonObject = jsonElement.asJsonObject

            val embedId = url.substringAfterLast("/")
            val sidValue = jsonObject["data"]?.asJsonArray
                ?.takeIf { it.size() > 0 }
                ?.get(0)?.asJsonObject
                ?.get("fileslug")?.asString
                ?.takeIf { it.isNotBlank() } ?: embedId

            Pair(sidValue, hostUrl)
        }

        val postData = mapOf("sid" to sid)
        val responseText = app.post("$host/embedhelper.php", data = postData).text

        val rootElement = JsonParser.parseString(responseText)
        if (!rootElement.isJsonObject) return
        val root = rootElement.asJsonObject

        val siteUrls = root["siteUrls"]?.asJsonObject ?: return
        val siteFriendlyNames = root["siteFriendlyNames"]?.asJsonObject

        val decodedMresult = when {
            root["mresult"]?.isJsonObject == true -> root["mresult"]!!.asJsonObject
            root["mresult"]?.isJsonPrimitive == true -> try {
                base64Decode(root["mresult"]!!.asString)
                    .let { JsonParser.parseString(it).asJsonObject }
            } catch (e: Exception) {
                Log.e("Phisher", "Failed to decode mresult: $e")
                return
            }
            else -> return
        }

        siteUrls.keySet().intersect(decodedMresult.keySet()).forEach { key ->
            val base = siteUrls[key]?.asString?.trimEnd('/') ?: return@forEach
            val path = decodedMresult[key]?.asString?.trimStart('/') ?: return@forEach
            val fullUrl = "$base/$path"
            val friendlyName = siteFriendlyNames?.get(key)?.asString ?: key

            try {
                Log.d("Phisher","$friendlyName $fullUrl")
                when (friendlyName) {
                    "StreamHG","EarnVids" -> VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)
                    "RpmShare", "UpnShare", "StreamP2p" -> VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)
                    else -> loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                Log.e("Phisher", "Failed to extract from $friendlyName at $fullUrl: $e")
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}

class ascdn21 : AWSStream() {
    override val name = "Zephyrflick"
    override val mainUrl = "https://as-cdn21.top"
    override val requiresReferer = true
}


open class AWSStream : ExtractorApi() {
    override val name = "AWSStream"
    override val mainUrl = "https://z.awstream.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extractedHash = url.substringAfterLast("/")
        val doc = app.get(url).document
        val m3u8Url = "$mainUrl/player/index.php?data=$extractedHash&do=getVideo"
        val header = mapOf("x-requested-with" to "XMLHttpRequest")
        val formdata = mapOf("hash" to extractedHash, "r" to mainUrl)
        val response = app.post(m3u8Url, headers = header, data = formdata).parsedSafe<Response>()
        response?.videoSource?.let { m3u8 ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
            val extractedPack = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().orEmpty()

            JsUnpacker(extractedPack).unpack()?.let { unpacked ->
                Regex(""""kind":\s*"captions"\s*,\s*"file":\s*"(https.*?\.srt)""")
                    .find(unpacked)
                    ?.groupValues
                    ?.get(1)
                    ?.let { subtitleUrl ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                "English",
                                subtitleUrl
                            )
                        )
                    }
            }
        }
    }

    data class Response(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<Any?>,
        val attachmentLinks: List<Any?>,
        val ck: String,
    )
}

class Multimovies: StreamWishExtractor() {
    override var name = "Multimovies Cloud"
    override var mainUrl = "https://multimovies.cloud"
    override var requiresReferer = true
}

class FileMoonNL : Filesim() {
    override val mainUrl = "https://filemoon.nl"
    override val name = "FileMoon"
}

class Vidmolynet : Vidmoly() {
    override val mainUrl = "https://vidmoly.net"
}

class Cdnwish : StreamWishExtractor() {
    override var name = "Streamwish"
    override var mainUrl = "https://cdnwish.com"
}

class Cloudy : VidStack() {
    override var mainUrl = "https://cloudy.upns.one"
}

class Animezia : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://animezia.cloud"
    override var requiresReferer = true
}

data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)


class Animedekhoco : ExtractorApi() {
    override val name = "Animedekhoco"
    override val mainUrl = "https://animedekho.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc: Document? = if (url.contains("url=")) app.get(url).document else null
        val text: String? = if (!url.contains("url=")) app.get(url).text else null

        val links = mutableListOf<Pair<String, String>>()

        doc?.select("select#serverSelector option")?.forEach { option ->
            val link = option.attr("value")
            val name = option.text().ifBlank { "Unknown" }
            if (link.isNotBlank()) links.add(name to link)
        }

        text?.let {
            val regex = Regex("""file\s*:\s*"([^"]+)"""")
            regex.find(it)?.groupValues?.get(1)?.let { link ->
                links.add("Player File" to link)
            }
        }

        links.forEach { (serverName, serverUrl) ->
            callback.invoke(
                newExtractorLink(
                    serverName,
                    serverName,
                    url = serverUrl,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://rubystm.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanedUrl = url.replace("/e", "")
        val response = app.get(
            cleanedUrl,
            referer = cleanedUrl,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document

        val scriptData = response.selectFirst("script:containsData(vplayer)")?.data().orEmpty()

        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to cleanedUrl,
        )

        Regex("file:\"(.*)\"").find(scriptData)?.groupValues?.getOrNull(1)?.let { link ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = link,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                    this.headers = headers
                }
            )
        }
    }
}


class Blakiteapi : ExtractorApi() {
    override val name = "Blakiteapi"
    override val mainUrl = "https://blakiteapi.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiurl = "$mainUrl/api/get.php?id=${url.substringAfterLast("/")}&tmdbId=${url.substringAfter("embed/").substringBefore("/")}"

        val responseText = app.get(apiurl).text

        val json = JSONObject(responseText)
        val success = json.optBoolean("success", false)

        if (success) {
            val data = json.getJSONObject("data")

            val quality = data.optString("quality", "480p")
            val format = data.optString("format", "MP4")
            val dataId = data.optString("dataId", "")
            val streamUrl = "$mainUrl/stream/$dataId.$format"
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    streamUrl,
                    INFER_TYPE
                )
                {
                    this.quality=getQualityFromString(quality)
                }
            )
        }
    }

    private fun getQualityFromString(q: String): Int {
        return when {
            q.contains("1080", true) -> Qualities.P1080.value
            q.contains("720", true) -> Qualities.P720.value
            q.contains("480", true) -> Qualities.P480.value
            q.contains("360", true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}


open class Abyass : ExtractorApi() {
    override var name = "Abyass"
    override var mainUrl = "https://abyssplayer.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Origin" to "https://playhydrax.com",
            "Referer" to "https://playhydrax.com/"
        )

        val document = app.get(url, headers = headers).document

        val scripts = document.select("script").joinToString("\n") { it.data() }

        val encrypted = Regex("""const\s+datas\s*=\s*"([^"]*)"""")
            .find(scripts)
            ?.groupValues
            ?.getOrNull(1)
            ?: return


        //val test = "eyJzbHVnIjoiSzhSNk9PalM3IiwibWQ1X2lkIjoyMzEwMzc0MiwidXNlcl9pZCI6MTM3NzEsIm1lZGlhIjoiimRoOStcdTAwMDfmauoseV7YXHUwMDEyt4PtbKHAXaGTdiZcdTAwMDbaSdVmPL7IdkBDi4Sm089cdTAwMTldMVx1MDAwMp9aina33lohk22ysDx2Xod6tYz+IZvc4zS4wqZ4vI2VrennXHUwMDAxY+9mhmTl1eBv03c0YLZcZnMhRib9XHUwMDFhjaVoKLODknrRMYiz/8FUW1xymWVTv2O5QkCQ5WXkXHUwMDFivFN6a1x1MDAxNXSbU/Yq+nVYxWdi9vdjUsRT1X2KKoeE9Oxw5uVdXHUwMDExM1x1MDAxYuFcdTAwMDcxMlx1MDAwNlxiiTGgeNslXHUwMDFm1lx1MDAxZbouhUNcdTAwMDZccpeEXHUwMDE3w7pvZ2WySOXfpYT4NNFs4UxrsZygLNWCOWtcdTAwMDY/Mitfdy9XcKRcdTAwMWbFdPAwfVx1MDAxMvaUYlx1MDAwYst+v+o/2mtlXHUwMDE37S4tXHUwMDBmyFwiSlxmXHUwMDBmXHUwMDE3yUNJfHlcdTAwMTZ8TWVcdTAwMDKj2MNcdTAwMTJ08Lj8sYxcdTAwMDBoXHUwMDBi4u1cdTAwMDFAw21cdTAwMWXFXHUwMDAxXHUwMDA1NKAt1S232P/YXHUwMDFhlDJy7UVJh7125rRvo+RcdTAwMDRcdTAwMDUhXpbFPjeRIY6YJ9H06G9JqjC+nHg1kMaKrdSIXGbFXHUwMDA14lNOXd9u1KUsfKxcdTAwMTBJNELvXHUwMDE2xVBcdTAwMWRcdTAwMTXKhCF7bpLPr19miUnSULGILFx1MDAxYzh53IbHIVx1MDAxYfa7eFRcdTAwMTj8pVx1MDAwZVx1MDAxYZZGYUY/laI1lrGs0eM6XHUwMDEyQrBcdTAwMWJfXHUwMDA3pG7ch1Xgs1x1MDAwNPZcdTAwMDRRcKPRYGGgd5YgXHUwMDE2XHUwMDEy0Fx1MDAxY1x1MDAwYvLHU1x1MDAwMONao1x1MDAwNG6nN1x1MDAwYqRo4DB4clppL1x1MDAxYjq8wHSIXG41Z/mLvLVbXHUwMDFj8lx1MDAxNlx1MDAxNWtXQLlEmzeat7lcXLpcbqluWNHtpejrpGsk6DqVuOlLS6LM51x1MDAxOFx1MDAwYudnyFx1MDAwNVp9ipNcdTAwMWJcdTAwMThcdTAwMDPmrcjRXHUwMDEx6al+v/JcdTAwMGK7I+qCLD7e9U/EXFxTXHUwMDE0ii1QXHInc3qAif/atJ5Dslx1MDAxMF0gOp2yUNCGXHUwMDAyoyFvLMeRzXiyXHUwMDFm2kVdZ6dbde0us47TutNcdTAwMGJooNPEjcv+QMr/N65Lq5W/t1x1MDAwN330KyFqhokqZuO6Kzcg6Saz5KdZQCYj91tK0lj0jNtoXHRMcKbsXHUwMDEzXHUwMDAyXHUwMDEyVTHkd6DtpVsxPsvZ9JMu131cdTAwMTV4W1x1MDAxY5RcdTAwMDJEO1x1MDAxZCtcdTAwMTeWXHUwMDEzXHUwMDE2doy6XHUwMDE0Z5XvXHUwMDE0O1hddd7Q9FHbOdBcXNfifeJXJ5/IXHUwMDBmLGxcdTAwMDN+M5fvXHUwMDE0XHUwMDE5TDMhkvhD3ORMZrymxSq4sVtcdTAwMDaqe40yh9kpYJyVYkZzkHKUto2ktK7R5mtTha4yXHUwMDE2/4KxWYay1PVcdTAwMDKYmLs+KoyR1L+ijLlXjVx1MDAwZZedU3WBQydvaHJcdTAwMWbjXHQ+teMhXHUwMDA1Y6BLxsBcdTAwMDc5XHUwMDFmvF7YiZKWcfnsL8Zty/Ul7cZad5s4pFxyWfBcdTAwMTFcdTAwMGWTJ1xi71x1MDAxMIOWRt+n2Fx1MDAxOVx1MDAxMbSsTUSqd2zsXHUwMDFkTuh7XHUwMDFmN7zyXHUwMDE2+TxcdTAwMGZxXHUwMDFlLYqVPmRdXHUwMDA1NvtdptpByaSHXGbokEfxp2fOqT5UY/8y5aJCMiBRzFx1MDAwNWjQfrhcdTAwMTWMV4XGg3nywWGZr3q2s8K7d+RG0tOHXHUwMDA2Q9Z3vVK7qsFvhOFb67at9C28+pes5Vx1MDAwMShcdTAwMDGVm5d/XHUwMDEyXHUwMDFkU9h74sZtKop6cVTM68mDXFxPPqvV7FxcXHUwMDExf/5cdTAwMGJrWnIk+bq9Src504nYy7JSvG9cdTAwMTbJ5oaHliMr7uPVSI8lXHUwMDA2mpIzr1x1MDAwZenYRsXG8I5oXHUwMDA2+KhunzVcdTAwMTWYNlRdm8Jqj2x/ljOa6zlcdTAwMTU3oXxcdTAwMTPZm02FcO5Mmz9PcrHpXHUwMDBmXHUwMDBmNjRnmdGBM9HqU22z2Fx1MDAxMmNcYqp0XHUwMDAxmOrCr1x1MDAwMNzqkvKH1mVfXHUwMDA3hmPzXHUwMDE5jl6U8O9CXHUwMDA3LNVcdTAwMDKw2K/LyNdl+VN25HlFXHUwMDA1pVxunypdpz3BqZBuXHUwMDBl4jZ5uOb6Srb3qzeCXHUwMDEy1PJrSP2iT1x1MDAwMLCVXG6oz0yc3zVcdTAwMWZ9cv5wn17hXFyrzT+4ukPJRzZcdTAwMDAjXHUwMDFjoNkuKVx1MDAxY+d3TJGJOdyc3zCoXHUwMDFlS9NcdTAwMWZrXHUwMDE2yfdcdTAwMTjgjVx1MDAwZuhk3WY38lx1MDAxZlx1MDAwNsQ0M7rpZXLo6E5UPrOTXHUwMDFiZZqLyT6NXHUwMDE2fFx1MDAxOTZLx8RcdTAwMDXvmJpJJj/9LfiNn4T2M7L9NGLqMVx1MDAwYt+2l3J9j6k23ZBeSGOm3ukz/IDri4DDhj9cdTAwMDVtbVx1MDAxMH10XHUwMDE4gktRuc5xheXiXHUwMDE1tPDg+oS6RrKEyVx1MDAxNVx1MDAxMIhfLKReh14npSR4nW+AM/zKlY5u2v0h2ddcdTAwMWTePr+jwVx1MDAxMYJcdTAwMWY0XHUwMDA3J+Hf+8YrJjfPN8F/XHUwMDBmXHUwMDEwXHUwMDE4kFJcdTAwMWZcbkgpQ1HSb2NcdTAwMTc48E/+tFx1MDAxNHqSXHUwMDAwXHUwMDA36VIxl13LjiQ4hpOyQppcdTAwMTEmb0owmEFglKe53YVhOpTuXHUwMDE0Xcw6KTyEMUpcdTAwMGacZWZTeGvI9+g1t0ncIbQsc1PKoq9BYniVJVm2dos3zVx1MDAwZlx1MDAxNfuJXHUwMDE04G1cZmJcdTAwMTAvf9B75pD+2ydcdTAwMWKA1E/RqO+bpEHbZnDTJMmuUFTuZ4rhob9lKoSyQL32zYN5T4HCVLdrXHUwMDA2slx1MDAxMOaOVe+2XHUwMDBiXHUwMDBlXHUwMDBmysPvOU6VpFx1MDAxYppu6ITy4uu0rlx1MDAxYlx1MDAxNFx1MDAxYiOWy1IjMFZcXCj9h1PO7dfBXaWjXHUwMDA1wv38LSGirWTdXHUwMDE2SFx1MDAwM1x1MDAxYdtcXCPLclx1MDAxOVkhbzWbXGLXzpGTjV1cdTAwMTltIc6URZqOtlx1MDAxMEK+lJN2XHUwMDA0lPZhcDSQivhpr1mo2y48925cdTAwMTKekFdcdTAwMTfTXHUwMDA1XG5x4bg7qdY/NVxiULD1viioWaKK61x1MDAxMVrobd7P86nDR1x1MDAwMJfHhGBC9zFcYjRnhiCu4Fx1MDAxYVx1MDAxY/z/MY79KVx1MDAxNi5SP1x1MDAxMvb9gobvQJVyoqdRPW3RXFx6ckhknPVcdTAwMDHUlzNA+iygTCBvRJ3ZXHUwMDA203A3SVJLzkMp0GI0rcdcdTAwMDJ0zbOSUkBcXNUpV4RP/ifO4t7IXHUwMDAweYVk+nY8X6lcdTAwMTjAj4hcbvkxy05cZlx1MDAxN65cdTAwMWTdXHUwMDEykjGEdOmPXHUwMDFh11x1MDAxOO2kgD90etfk01x1MDAwMMhcdTAwMTBcdTAwMDOVmNOk+qIrW8HszU1jlmIgXHUwMDE310dcdTAwMTdfXHUwMDAxrWL7IF103Vx1MDAwNUxcdTAwMTNcdTAwMGLws2ScXHUwMDAxIEszXGZxvftHmU1kivRn1GF77Vx1MDAxNPKkT5hcdTAwMTZcXL/J275NXGL3mK3YXHUwMDAwwZW0h95YqZpcdTAwMTDOXHJWqt/ZUURtkzWZyduh5tFcdTAwMTXGXHUwMDEyi0vZiFRcdKBgKq81MoEmpdT5VcHHN/vgeVx1MDAxN2tSKnc7t1x1MDAxYzFcdTAwMTHtRJbrlzaef3VyXHUwMDAzjD7hOiB0XHJf5ON+XHUwMDEwS/E816Q1dzBA66hMXHUwMDA0IcLC0lx1MDAwNjelNa0seOhrnKrbQslcdTAwMTlgniRSjKY3vsTgjMtwzoM4iEGl6Fx1MDAxOERcdTAwMTBxNsBrU1ftc3+f6j2WVdHeXHUwMDE2bVxuXHUwMDE2xI9oWGlTjlx1MDAxZnLdXHJf/JZXMzpcdTAwMDJuXedI4o5cIiOFs5vHXHKfXHUwMDA0XHUwMDBmXFxcdTAwMThbVm9cdTAwMTmSvGJcdTAwMDOJXHUwMDE2KLs5rLpnWkpbtiuerzXmTUpcdTAwMWH+dX2fRv2Oxrfq2rstaczJ/pqie1x1MDAwMcepK6/fMFx1MDAxObfijSiHblFwaXRKsFx1MDAxNWyxy6Xz8u1pLCMlxHfXfziV4yvltoOTZvKOmqylXHUwMDBi5uVUOVx0rFxmpd7sx6Wu4pUhbVx1MDAxMe1eXHUwMDA1/e97PVYviUfZZXVSyIBcdTAwMTNcdTAwMDX88s2ndEPXsYjZgn1bqFxyq9ZcdTAwMDGFmtFGYq/DllDNXHUwMDAzsmt4ZXVRxHj5VM9cdTAwMDaxfI1cdTAwMWGcmodcdTAwMTDyi4PfNWRcdTAwMTjZMnK8ilYw5Dnmem/BrV7+upKhT3zmUZf3RlaG7np7PUi9fY/kjipcdTAwMGZm8L3C8iIsImNvbmZpZyI6eyJwb3N0ZXIiOnRydWUsInByZXZpZXciOnRydWV9LCJkYW5tdSI6eyJ2aWRlb0lkIjoiUDAzdmZNNHVQX25qd0JvWmM3OGROWHE4UV9Iei1oazRwV2JId0VReTluNlNfSVRONFN4d1g5cFdoU0lvdkJya0NCWXNpazJsdGFlQWJabm9vemxpTUxzeWMtVncyUVFoIn19"
        val decrypted = app.post(
            url = "https://enc-dec.app/api/dec-abyss",
            headers = headers,
            requestBody = """
        {
            "text": "$encrypted"
        }
    """.trimIndent().toRequestBody(
                "application/json".toMediaType()
            )
        ).parsedSafe<AbyssResponse>()?.result ?: return

        decrypted.sources
            .filter { it.status }
            .forEach { source ->

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name [${source.codec.uppercase()}]",
                        url = source.url,
                        type = INFER_TYPE
                    ) {
                        this.quality = getQualityFromName(source.type)
                        this.headers = mapOf(
                            "Referer" to "https://playhydrax.com/"
                        )
                    }
                )
            }
    }

    data class AbyssResponse(
        val status: Long,
        val result: Result,
    )

    data class Result(
        val sources: List<AbyssSource>,
    )

    data class AbyssSource(
        val url: String,
        val size: Long,
        val type: String,
        val codec: String,
        val status: Boolean,
    )


}