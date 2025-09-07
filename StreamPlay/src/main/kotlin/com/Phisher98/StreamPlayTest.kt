package com.phisher98


import android.content.SharedPreferences
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.StreamPlayExtractor.invokeVidzeeApi
import com.phisher98.StreamPlayExtractor.invokevidzeeUltra

class StreamPlayTest(sharedPreferences:SharedPreferences?=null) : StreamPlay(sharedPreferences) {
    override var name = "StreamPlay-Test"
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = AppUtils.parseJson<LinkData>(data)
        runAllAsync(
            {
                if (!res.isAnime) invokevidzeeUltra(
                    res.id,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeVidzeeApi(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
        )
        return true
    }

}