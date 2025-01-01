package com.Phisher98

import android.util.Log
import com.Phisher98.StreamPlayExtractor.invokeTom

import com.Phisher98.StreamPlayExtractor.invokeVidsrccc
import com.Phisher98.StreamPlayExtractor.invokecatflix
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
                if (!res.isAnime) invokecatflix(
                    res.id,
                    res.epid,
                    res.title,
                    res.episode,
                    res.season,
                    res.year,
                    subtitleCallback,
                    callback
                )
            }

        )
        return true
    }

}