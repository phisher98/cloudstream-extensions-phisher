package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink


open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=")
        val res = app.post(
                "$mainUrl/api.php?id=$id",
                data = mapOf(
                        "r" to "https://playeriframe.shop/",
                        "d" to "stream.hownetwork.xyz",
                ),
                referer = url,
                headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                )
        ).parsedSafe<Sources>()

        res?.data?.map {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = it.file,
                    INFER_TYPE
                ) {
                    this.referer = url
                    this.quality = getQualityFromName(it.label)
                }
            )
        }

    }

    data class Sources(
            val data: ArrayList<Data>
    ) {
        data class Data(
                val file: String,
                val label: String?,
        )
    }
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}



