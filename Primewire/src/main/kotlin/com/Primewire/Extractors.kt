package com.Primewire

//import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.httpsify

class MixDropag : MixDrop(){
    override var mainUrl = "https://mixdrop.ag"
}

open class MixDrop : ExtractorApi() {
    override val name = "MixDrop"
    override val mainUrl = "https://mixdrop.ps"
    override val requiresReferer = false
    private val srcRegex = Regex("""wurl.*?=.*?"(.*?)";""")
    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/e/$id"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

            val document= app.get(url.replace("/f/","/e/")).document
            val script = document.selectFirst("script:containsData(mxcontent)")?.data().toString()
            val packedregex = """eval\(function\(.*?\}\)\)""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val matchContent = packedregex.find(script)?.value ?:""
            getAndUnpack(matchContent).let { unpackedText ->
                srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { videoUrl ->
                    callback.invoke(
                        ExtractorLink(
                            name,
                            name,
                            httpsify(videoUrl),
                            url,
                            Qualities.Unknown.value,
                            INFER_TYPE
                        )
                    )
                }
            }
    }
}