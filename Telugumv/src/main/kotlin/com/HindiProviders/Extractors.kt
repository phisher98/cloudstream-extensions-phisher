package com.HindiProviders

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import okio.ByteString.Companion.decodeBase64
import java.net.URI

open class Autoembed : ExtractorApi() {
    override var name = "Autoembed"
    override var mainUrl = "https://autoembed.cc"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        response.select("dropdown-menu > button").map {
            val encoded=it.attr("data-server")
            val link=encoded.decodeBase64().toString()
            Log.d("Phisher",link)
        }
        return null
    }
}