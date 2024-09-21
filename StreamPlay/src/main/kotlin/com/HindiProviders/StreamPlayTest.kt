package com.HindiProviders

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

        argamap(
            {    invokeUhdmovies(
                res.title,
                res.year,
                res.season,
                res.episode,
                callback,
                subtitleCallback
            )
            }
        )
        return true
    }

}