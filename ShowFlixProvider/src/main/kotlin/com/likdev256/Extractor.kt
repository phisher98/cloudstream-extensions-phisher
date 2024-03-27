package com.likdev256

//import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI


open class Streamwish : ExtractorApi() {
    override var name = "Streamwish"
    override var mainUrl = "https://streamwish.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        //val response = app.get("$url",referer=url)
        val serverRes = app.get(url,referer=url).document
        //Log.d("Test12","$serverRes")
            val script = serverRes.selectFirst("script:containsData(sources)")?.data().toString()
            //Log.d("Test12","$script")
            Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        referer ?: "",
                        getQualityFromName(""),
                        URI(link).path.endsWith(".m3u8")
                    )
                )
            }
        return null
    }
}


open class Filelion : ExtractorApi() {
    override val name = "Filelion"
    override val mainUrl = "https://filelions.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val response =app.get(url).document
        //Log.d("Test12","$response")
        //val response = app.get(url, referer = referer)
        val script = response.selectFirst("script:containsData(sources)")?.data().toString()
        //Log.d("Test9871",script)
        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            //Log.d("Test9876",link)
                return listOf(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        referer ?: "",
                        getQualityFromName(""),
                        URI(link).path.endsWith(".m3u8")
                    )
                )
        }
        return null
    }
}


open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val response =app.get(url).document
        //Log.d("Test12","$response")
        //val response = app.get(url, referer = referer)
        val script = response.selectFirst("script:containsData(sources)")?.data().toString()
        //Log.d("Test9871",script)
        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            //Log.d("Test9876",link)
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    link,
                    referer ?: "",
                    getQualityFromName(""),
                    URI(link).path.endsWith(".m3u8")
                )
            )
        }
        return null
    }
}