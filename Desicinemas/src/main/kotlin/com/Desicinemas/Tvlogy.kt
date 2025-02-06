package com.Desicinemas

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities

class Tvlogyflow(val source:String) : ExtractorApi() {
    override val mainUrl = "https://flow.tvlogy.to"
    override val name = "Tvlogy"
    private val privatereferer = "https://skillsmagnet.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc=app.get(url, referer = privatereferer).text
        if (doc.contains(".m3u8"))
        {
            Regex("\"src\":\"(.*?)\",\"").find(doc)?.groupValues?.get(1)?.let {
                callback(
                    ExtractorLink(
                        source,
                        name,
                        it,
                        url,
                        Qualities.Unknown.value,
                        INFER_TYPE
                    )
                )
            }
        }
        else {
            val script =
                base64Decode(doc.substringAfter("JuicyCodes.Run(\"").substringBefore("\");").replace("\"+\"", ""))
                val unpacked= JsUnpacker(script).unpack().toString()
            Log.d("Phisher scrit",script)
            Log.d("Phisher scrit unpacked",unpacked)
            Regex("file\":.*?\"(.*.m3u8)\"").find(unpacked)?.groupValues?.get(1)?.let {
                callback(
                    ExtractorLink(
                        source,
                        name,
                        it,
                        url,
                        Qualities.Unknown.value,
                        INFER_TYPE
                    )
                )
            }
        }
    }
}



class Tvlogy(private val source:String) : ExtractorApi() {
    override val mainUrl = "https://tvlogy.to"
    override val name = "Tvlogy"
    override val requiresReferer = true


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val id = url.substringAfter("data=")
        val data = mapOf(
            "hash" to id,
            "r" to "http%3A%2F%2Ftellygossips.net%2F"
        )
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val meta = app.post("$url&do=getVideo", headers = headers, referer = referer, data = data)
            .parsedSafe<MetaData>() ?: return

        callback(
            ExtractorLink(
                source,
                name,
                meta.videoSource,
                url,
                Qualities.Unknown.value,
                meta.hls
            )
        )
    }

    data class MetaData(
        val hls: Boolean,
        val videoSource: String
    )

}