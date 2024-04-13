package com.hexated

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.utils.*

class Sbnmp : ExtractorApi() {
    override val name = "FMHD"
    override var mainUrl = "https://fmhd.bar/"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 =
            Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?: return,
                mainUrl,
                Qualities.Unknown.value,
                INFER_TYPE,
            )
        )
    }
}

open class Akamaicdn : ExtractorApi() {
    override val name = "Akamaicdn"
    override val mainUrl = "https://akamaicdn.life"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val mappers = res.selectFirst("script:containsData(sniff\\()")?.data()?.substringAfter("sniff(")
            ?.substringBefore(");") ?: return
        val ids = mappers.split(",").map { it.replace("\"", "") }
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                "$mainUrl/m3u8/${ids[1]}/${ids[2]}/master.txt?s=1&cache=1",
                url,
                Qualities.Unknown.value,
                isM3u8 = true,
            )
        )
    }
}

open class Vidsrc : ExtractorApi() {
    override val name = "Vidsrc"
    override val mainUrl = "https://vidsrc.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Test url",url)
        val iframedoc =
            app.get(url).document.select("iframe#player_iframe").attr("src").let { httpsify(it) }
        Log.d("Test url",iframedoc)
        val doc = app.get(iframedoc, referer = url).document

        val index = doc.select("body").attr("data-i")
        val hash = doc.select("div#hidden").attr("data-h")
        val srcrcp = deobfstr(hash, index)

        val script = app.get(
            httpsify(srcrcp),
            referer = iframedoc
        ).document.selectFirst("script:containsData(Playerjs)")?.data()
        val video = script?.substringAfter("file:\"#9")?.substringBefore("\"")
            ?.replace(Regex("/@#@\\S+?=?="), "")?.let { base64Decode(it) }

        callback.invoke(
            ExtractorLink(
                "Vidsrc", "Vidsrc", video
                    ?: return, "https://vidsrc.stream/", Qualities.P1080.value, INFER_TYPE
            )
        )
    }
}


class AnimesagaStream : Chillx() {
    override val name = "AnimesagaStream"
    override val mainUrl = "https://stream.anplay.in"
}

fun deobfstr(hash: String, index: String): String {
    var result = ""
    for (i in hash.indices step 2) {
        val j = hash.substring(i, i + 2)
        result += (j.toInt(16) xor index[(i / 2) % index.length].code).toChar()
    }
    return result
}