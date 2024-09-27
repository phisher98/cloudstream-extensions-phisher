package com.kissasian

//import android.util.Log
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

class dwish : StreamWishExtractor() {
    override var mainUrl = "https://dwish.pro"
    override val requiresReferer = false
}

class embedwish : StreamWishExtractor() {
    override var mainUrl = "https://embedwish.com"
    override val requiresReferer = false
}

class MixDropPs : MixDrop(){
    override var mainUrl = "https://mixdrop.ps"
}

open class Plcool1 : ExtractorApi() {
    override var name = "Streamwish"
    override var mainUrl = "https://plcool1.com"
    override val requiresReferer = false

    override suspend fun
            getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val responsecode= app.get(url)
            val serverRes = responsecode.document
            serverRes.select("ul.list-server-items").map {
                Log.d("Phisher p", it.toString())
                val href=it.attr("data-video")
                loadExtractor(href,subtitleCallback,callback)
            }
        }
}