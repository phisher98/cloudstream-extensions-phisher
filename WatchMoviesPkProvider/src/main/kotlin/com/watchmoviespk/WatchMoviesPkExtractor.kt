package com.watchmoviespk

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.*

open class EmbedPk : ExtractorApi() {
    override var name = "EmbedPk"
    override var mainUrl = "https://embedpk.net/"
    override val requiresReferer = false


    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            getAndUnpack(this.text).let { unpackedText ->
                    val finalLink = unpackedText.substringAfter("sources:[{src:\"").substringBefore("\",")
                    return listOf(
                        ExtractorLink(
                            name,
                            name,
                            httpsify(finalLink),
                            url,
                            Qualities.Unknown.value,
                        )
                    )
            }
        }
        return null
    }


}

class TapeAdvertisement : StreamTape() {
    override var mainUrl = "https://tapeadvertisement.com/"
}