package com.Goojara

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vidguardto
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.HlsPlaylistParser
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI

class Stre4mpay : VidhideExtractor() {
    override var mainUrl = "https://stre4mpay.one"
}

class Streamplay : ExtractorApi() {
    override val name = "Streamplay"
    override val mainUrl = "https://streamplay.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val request = app.get(url, referer = referer)
        val redirectUrl = request.url
        val mainServer = URI(redirectUrl).let {
            "${it.scheme}://${it.host}"
        }
        val key = redirectUrl.substringAfter("embed-").substringBefore(".html")
        val token =
            request.document.select("script").find { it.data().contains("sitekey:") }?.data()
                ?.substringAfterLast("sitekey: '")?.substringBefore("',")?.let { captchaKey ->
                    getCaptchaToken(
                        redirectUrl,
                        captchaKey,
                        referer = "$mainServer/"
                    )
                } ?: throw ErrorLoadingException("can't bypass captcha")
        app.post(
            "$mainServer/player-$key-488x286.html", data = mapOf(
                "op" to "embed",
                "token" to token
            ),
            referer = redirectUrl,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        ).document.select("script").find { script ->
            script.data().contains("eval(function(p,a,c,k,e,d)")
        }?.let {
            val data = getAndUnpack(it.data()).substringAfter("sources=[").substringBefore(",desc")
                .replace("file", "\"file\"")
                .replace("label", "\"label\"")
            tryParseJson<List<Source>>("[$data}]")?.map { res ->
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        res.file ?: return@map null,
                        INFER_TYPE
                    )
                    {
                        this.referer ="$mainServer/"
                        this.quality = when (res.label) {
                            "HD" -> Qualities.P720.value
                            "SD" -> Qualities.P480.value
                            else -> Qualities.Unknown.value
                        }
                        this.headers=mapOf(
                            "Range" to "bytes=0-"
                        )
                    }
                )
            }
        }

    }

    data class Source(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

}

class Wootly : ExtractorApi() {
    override var name = "Wootly"
    override var mainUrl = "https://www.wootly.ch"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val iframe = app.get(url).documentLarge.select("iframe").attr("src")
        val body = FormBody.Builder()
            .add("qdfx", "1")
            .build()

        val iframeResp = app.post(iframe, requestBody = body)
        val iframeHtml = iframeResp.textLarge
        val vdRegex = Regex("""var\s+vd\s*=\s*["']([^"']+)["']""")
        val tkRegex = Regex("""tk\s*=\s*["']([^"']+)["']""")
        val vd = vdRegex.find(iframeHtml)?.groupValues?.get(1)
        val tk = tkRegex.find(iframeHtml)?.groupValues?.get(1)

        if (vd.isNullOrBlank() || tk.isNullOrBlank()) {
            return null
        }
        val iframeurl=app.get("https://web.wootly.ch/grabm?t=$tk&id=$vd").text

        return listOf(
            newExtractorLink(
                this.name,
                this.name,
                url = iframeurl,
                type = INFER_TYPE
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P720.value
            }
        )
    }
}