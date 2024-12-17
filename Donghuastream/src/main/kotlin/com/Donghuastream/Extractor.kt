package com.Donghuastream

//import android.util.Log
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import java.net.URI

open class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        referer ?: "",
                        Qualities.Unknown.value,
                        URI(link).path.endsWith(".m3u8")
                    )
                )
            }
        }
        return null
    }
}

class wishfast : StreamWishExtractor() {
    override var mainUrl = "https://wishfast.top"
    override var name = "StreamWish"
}

class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}


open class Ultrahd : ExtractorApi() {
    override var name = "Ultrahd Streamplay"
    override var mainUrl = "https://ultrahd.streamplay.co.in"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
            val response = app.get(url,referer=mainUrl).document
            val extractedpack =response.toString()
            Regex("\\\$\\.\\s*ajax\\(\\s*\\{\\s*url:\\s*\"(.*?)\"").find(extractedpack)?.groupValues?.get(1)?.let { link ->
                app.get(link).parsedSafe<Root>()?.sources?.map {
                    val m3u8= httpsify( it.file)
                    Log.d("Phisher Ultrahd",m3u8)
                    if (m3u8.contains(".mp4"))
                    {
                        callback.invoke(
                            ExtractorLink(
                                "Ultrahd Streamplay",
                                "Ultrahd Streamplay",
                                m3u8,
                                "",
                                getQualityFromName(""),
                                type = INFER_TYPE,
                            )
                        )
                    }
                    else
                    {
                        M3u8Helper.generateM3u8(
                            this.name,
                            m3u8,
                            "$referer",
                        ).forEach(callback)
                    }
                }
                app.get(link).parsedSafe<Root>()?.tracks?.map {
                    val langurl=it.file
                    val lang=it.label
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang,  // Use label for the name
                            langurl     // Use extracted URL
                        )
                    )
                }
            }
    }
}

class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Phisher Rumbel","i Here")

        val response = app.get(
            url, referer = referer ?: "$mainUrl/"
        )
        val playerScript =
            response.document.selectFirst("script:containsData(mp4)")?.data()
                ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{") ?:""
        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        Log.d("Phisher Rumbel",playerScript)
        val matches = regex.findAll(playerScript)
        for (match in matches) {
            val href = match.groupValues[1].replace("\\/", "/")
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    href,
                    "",
                    getQualityFromName(""),
                    type = INFER_TYPE,
                )
            )

        }
    }
}
