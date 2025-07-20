package com.KdramaHoodProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor

open class Embasic : ExtractorApi() {
    override var mainUrl = "https://embasic.pro"
    override val name = "Embasic"
    override val requiresReferer = false

    override suspend fun
            getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val iframe = app.get(httpsify(url))
        val iframeDoc = iframe.document
        runAllAsync({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    //Log.d("Phisher",element.toString())
                    val status = element.attr("data-status") ?: return@forEach
                    if (status != "1") return@forEach
                    val extractorData = element.attr("data-video") ?: return@forEach
                    if(extractorData.contains(mainUrl))
                    {
                        Log.d("Error","Not Found")
                    }
                    else
                        loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            val iv = "9262859232435825"
            val secretKey = "93422192433952489752342908585752"
            val secretDecryptKey = secretKey
            GogoHelper.extractVidstream(
                iframe.url,
                this.name,
                callback,
                iv,
                secretKey,
                secretDecryptKey,
                isUsingAdaptiveKeys = false,
                isUsingAdaptiveData = true,
                iframeDocument = iframeDoc
            )
        })
    }
}