package com.Tennistream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities


open class Quest4play : ExtractorApi() {
    override var name = "Quest4play"
    override var mainUrl = "https://quest4play.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res=app.get(url).document.toString()
        val href=Regex("""source:.'(.*?)'""").find(res)?.groupValues?.get(1) ?:""
        val reallink=app.get(href,referer=mainUrl, allowRedirects = false).headers["location"] ?:""
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                reallink,
                mainUrl,
                Qualities.P1080.value,
                isM3u8 = true,
            )
        )
    }
}

class Choosingnothing :Vaguedinosaurs()
{
    override var name = "Choosing Nothing"
    override var mainUrl = "https://choosingnothing.com"

}

open class Vaguedinosaurs : ExtractorApi() {
    override var name = "Vaguedinosaurs"
    override var mainUrl = "https://vaguedinosaurs.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc= app.get(url, referer = "https://wikisport.best/").text
        val hash=Regex("""player","(.*?)"""").find(doc)?.groupValues?.get(1)
        Log.d("Phisher doc",doc)
        Log.d("Phisher doc", hash.toString())
        val domain=Regex("""\{"([^"]+)""").find(doc)?.groupValues?.get(1)
        val link ="https://$domain/hls/$hash/live.m3u8"
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                link,
                mainUrl,
                Qualities.P1080.value,
                isM3u8 = true,
            )
        )
    }
}