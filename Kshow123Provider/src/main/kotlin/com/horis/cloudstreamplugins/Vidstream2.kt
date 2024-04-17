package com.horis.cloudstreamplugins

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink

class Vidstream2(override val mainUrl: String) : ExtractorApi() {

    override val name = "Vidstream"
    override val requiresReferer = false

    private val vidStream by lazy { Vidstream(mainUrl) }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Vidstream2", "getUrl")
        if (!url.contains("streaming.php")) {
            return
        }
        vidStream.getUrl(
            url.substringAfter("id=").substringBefore("&"),
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }

}