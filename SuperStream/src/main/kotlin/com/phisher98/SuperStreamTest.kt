package com.Phisher98


import android.content.SharedPreferences
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class SuperStreamTest(sharedPreferences:SharedPreferences?=null) : SuperStream(sharedPreferences) {
    override var name = "SuperStream-Test"
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = AppUtils.parseJson<LinkData>(data)
        invokeSuperstream(
            token,
            res.imdbId,
            res.season,
            res.episode,
            callback
        )
        return true
    }

}