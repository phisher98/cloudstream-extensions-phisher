package com.phisher98


import android.content.SharedPreferences
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

import com.phisher98.StreamPlayExtractor.invokeVegamovies

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
                invokeVegamovies(res.title, res.year, res.season, res.episode, res.imdbId, subtitleCallback, callback) }

        )
        return true
    }

}