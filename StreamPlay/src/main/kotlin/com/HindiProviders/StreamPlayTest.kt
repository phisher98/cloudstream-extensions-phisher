package com.Phisher98

import android.util.Log
import com.Phisher98.StreamPlayExtractor.invokeAnimes
import com.Phisher98.StreamPlayExtractor.invokeShowflix
import com.Phisher98.StreamPlayExtractor.invokeVidsrcsu
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class StreamPlayTest : StreamPlay() {
    override var name = "StreamPlay-Test"
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = AppUtils.parseJson<LinkData>(data)
        Log.d("Test1", "$res")
        argamap(
            {
                if (!res.isAnime) invokeVidsrcsu(
                    res.id,
                    res.season,
                    res.episode,
                    callback
                )
            }

        )
        return true
    }

}