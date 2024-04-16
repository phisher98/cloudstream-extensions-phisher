package com.hexated

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*

class FMHD : Filesim() {
    override val name = "FMHD"
    override var mainUrl = "https://fmhd.bar/"
    override val requiresReferer = true
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
        val mappers =
                res.selectFirst("script:containsData(sniff\\()")
                        ?.data()
                        ?.substringAfter("sniff(")
                        ?.substringBefore(");")
                        ?: return
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
