package com.IStreamFlare

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink


class Istreamjam : Istreamcdn() {
    override val mainUrl = base64Decode("aHR0cHM6Ly9zdHJlYW0uaXN0cmVhbWphbS5jb20=")
    override val requiresReferer = false
}

class Iasbase : Istreamcdn() {
    override val mainUrl = "https://iasbase.net"
    override val requiresReferer = false
}
open class Istreamcdn : ExtractorApi() {

    override val name = "IStreamCDN"
    override val mainUrl = "https://istreamcdn.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = base64Decode("aHR0cHM6Ly9zdHJlYW0uaXN0cmVhbWphbS5jb20=")
        try {
            val qualityName = referer?.substringBefore("+")
            val response = app.get("$host/mcdn/mDL.php?id=${
                url.substringAfterLast(
                    "id="
                )
            }", allowRedirects = false, headers = mapOf("Referer" to host))

            val location = response.headers["location"] ?: return
            if (location.contains("sub_expire", true)) return

            callback.invoke(
                newExtractorLink(
                    name = name,
                    source = name,
                    url = location,
                    type = INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getQualityFromName(qualityName)
                    this.headers = mapOf("Referer" to host)

                }
            )

        } catch (e: Throwable) {
            Log.e("IStreamCDN", "Resolver failed", e)
        }
    }
}



