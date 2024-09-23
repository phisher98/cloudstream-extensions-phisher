package com.HindiProviders

import android.util.Log
import com.HindiProviders.StreamPlayExtractor.invokeAnitaku
import com.HindiProviders.StreamPlayExtractor.invokeBollyflix
import com.HindiProviders.StreamPlayExtractor.invokeMoviesmod
import com.HindiProviders.StreamPlayExtractor.invokeVegamovies
import com.HindiProviders.StreamPlayExtractor.invokeMoviesdrive
import com.HindiProviders.StreamPlayExtractor.invokeTopMovies
import com.HindiProviders.StreamPlayExtractor.invokeUhdmovies
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
            {   if (res.isAnime) invokeAnitaku(
                res.title,
                res.epsTitle,
                res.date,
                res.year,
                res.season,
                res.episode,
                subtitleCallback,
                callback
            )
            },
            {
                if (!res.isAnime && !res.isBollywood) invokeMoviesmod(
                    res.title,
                    res.year,
                    res.season,
                    res.lastSeason,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            }
        )
        return true
    }

}