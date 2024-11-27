package com.Phisher98

import android.util.Log
import com.Phisher98.StreamPlayExtractor.invokeEmbedsu
import com.Phisher98.StreamPlayExtractor.invokeFlicky
import com.Phisher98.StreamPlayExtractor.invokeFlixAPI
import com.Phisher98.StreamPlayExtractor.invokeShowflix
import com.Phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.Phisher98.StreamPlayExtractor.invokeVidbinge
import com.Phisher98.StreamPlayExtractor.invokeVidsrccc
import com.Phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
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
                if (!res.isAnime) invokeShowflix(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeSubtitleAPI(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            }

        )
        return true
    }

}