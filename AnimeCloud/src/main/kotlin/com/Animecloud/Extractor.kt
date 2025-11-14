package com.Animecloud

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class AnimeCloudProxy : ExtractorApi() {
    override var name = "AnimeCloudProxy"
    override var mainUrl = "https://fireani.me"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val id=url.substringAfterLast("/")
        val csrftkn = app.get(url,referer=mainUrl).documentLarge.select("form#wrapper input[name=csrftkn]").attr("value")
        val seassion_ck = app.get("$mainUrl/proxy/player/adehu1awmdxx?csrftkn=$csrftkn",referer=mainUrl).cookies["session"]
        val m3u8="$mainUrl/proxy/nocache/$id/"
        val headers= mapOf("Cookie" to "session=$seassion_ck")
        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = m3u8,
                ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
                this.quality = Qualities.P1080.value
                this.headers = headers
            }
        )
        return
    }
}


open class LuluStream : ExtractorApi() {
    override var name = "LuluStream"
    override var mainUrl = "https://luluvdo.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val filecode = url.substringAfterLast("/")
        val postUrl = "$mainUrl/dl"
        val post = app.post(
            postUrl,
            data = mapOf(
                "op" to "embed",
                "file_code" to filecode,
                "auto" to "1",
                "referer" to (referer ?: "")
            )
        ).documentLarge
        post.selectFirst("script:containsData(vplayer)")?.data()
            ?.let { script ->
                Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
                    callback.invoke(
                        newExtractorLink(
                            name,
                            name,
                            url = link,
                            INFER_TYPE
                        ) {
                            this.referer = mainUrl
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }
    }
}

