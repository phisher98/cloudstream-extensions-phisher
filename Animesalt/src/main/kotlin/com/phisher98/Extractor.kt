package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class Pixdrive : Filesim() {
    override var mainUrl = "https://pixdrive.cfd"
}

class Ghbrisk : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://ghbrisk.com"
    override val requiresReferer = true
}

class ascdn21 : AWSStream() {
    override val name = "Zephyrflick"
    override val mainUrl = "https://as-cdn21.top"
    override val requiresReferer = true
}

class Zephyrflick : AWSStream() {
    override val name = "Zephyrflick"
    override val mainUrl = "https://play.zephyrflick.top"
    override val requiresReferer = true
}

class betaAwstream : AWSStream() {
    override val name = "AWSStream"
    override val mainUrl = "https://beta.awstream.net"
    override val requiresReferer = true
}

class Rapid : MegaPlay() {
    override val name = "Rapid"
    override val mainUrl = "https://rapid-cloud.co"
    override val requiresReferer = true
}

class Short : Abyass() {
    override var name = "Short"
    override var mainUrl = "https://short.icu"
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
                Regex(""""kind":\s*"captions"\s*,\s*"file":\s*"(https.*?)"""")
                    .find(unpacked)
                    ?.groupValues
                    ?.get(1)
                    ?.replace("\\", "")
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

open class MegaPlay : ExtractorApi() {
    override val name = "MegaPlay"
    override val mainUrl = "https://megaplay.buzz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainheaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "Connection" to "keep-alive",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )

        try {
            // --- Primary API Method ---
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )

            val id = Regex("/stream/s-\\d+/(\\d+)/")
                .find(
                    app.get(url, headers = headers)
                        .document
                        .selectFirst("iframe.s5-embed")
                        ?.attr("src")
                        ?: return
                )
                ?.groupValues?.get(1)
                ?: return

            val apiUrl = "$mainUrl/stream/getSources?id=$id"

            val response = runCatching {
                app.get(apiUrl, headers).parsedSafe<MegaPlayResponse>()
            }.getOrNull()

            val m3u8 = response?.sources?.file
                ?: throw Exception("No sources found")

            generateM3u8(
                name,
                m3u8,
                mainUrl,
                headers = mainheaders
            ).forEach(callback)

            response.tracks.forEach { track ->
                val file = track.file ?: return@forEach
                val label = track.label ?: "Unknown"

                if (track.kind == "captions" || track.kind == "subtitles") {
                    subtitleCallback(
                        newSubtitleFile(label, file)
                        {
                            this.headers = mapOf(
                                "Referer" to "$mainUrl/"
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // --- Fallback using WebViewResolver ---
            Log.e("Megacloud", "Primary method failed, using fallback: ${e.message}")

            val jsToClickPlay = """
                (() => {
                    const btn = document.querySelector('.jw-icon-display.jw-button-color.jw-reset');
                    if (btn) { btn.click(); return "clicked"; }
                    return "button not found";
                })();
            """.trimIndent()

            val m3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""\.m3u8"""),
                additionalUrls = listOf(Regex("""\.m3u8""")),
                script = jsToClickPlay,
                scriptCallback = { result -> Log.d("Megacloud", "JS Result: $result") },
                useOkhttp = false,
                timeout = 15_000L
            )

            val vttResolver = WebViewResolver(
                interceptUrl = Regex("""\.vtt"""),
                additionalUrls = listOf(Regex("""\.vtt""")),
                script = jsToClickPlay,
                scriptCallback = { result -> Log.d("Megacloud", "Subtitle JS Result: $result") },
                useOkhttp = false,
                timeout = 15_000L
            )

            try {
                val vttResponse = app.get(url = url, referer = mainUrl, interceptor = vttResolver)
                val subtitleUrls = listOf(vttResponse.url)
                    .filter { it.endsWith(".vtt") && !it.contains("thumbnails", ignoreCase = true) }
                subtitleUrls.forEachIndexed { _, subUrl ->
                    subtitleCallback(newSubtitleFile("English", subUrl)
                    {
                        this.headers = mapOf(
                            "Referer" to "$mainUrl/"
                        )
                    })
                }

                val fallbackM3u8 = app.get(url = url, referer = mainUrl, interceptor = m3u8Resolver).url
                generateM3u8(name, fallbackM3u8, mainUrl, headers = mainheaders).forEach(callback)

            } catch (ex: Exception) {
                Log.e("Megacloud", "Fallback also failed: ${ex.message}")
            }
        }
    }

    data class MegaPlayResponse(
        @JsonProperty("sources")
        val sources: Sources? = null,
        @JsonProperty("tracks")
        val tracks: List<Track> = emptyList()
    )

    data class Sources(
        @JsonProperty("file")
        val file: String? = null
    )

    data class Track(
        @JsonProperty("file")
        val file: String? = null,
        @JsonProperty("label")
        val label: String? = null,
        @JsonProperty("kind")
        val kind: String? = null
    )
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


class AnimesaltMulti : ExtractorApi() {
    override var name = "Animesalt Multi"
    override var mainUrl = "https://animesalt.ac"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframe = app.get(url, referer = mainUrl).document.select("div.video-container iframe").attr("src")
        Abyass().getUrl(iframe.replace("https://short.icu","https://abyssplayer.com"),referer,subtitleCallback,callback)
    }

}