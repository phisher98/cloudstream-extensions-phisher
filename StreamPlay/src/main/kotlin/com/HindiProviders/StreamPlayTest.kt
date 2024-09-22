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
            {    if (!res.isAnime) invokeMoviesdrive(
                res.title,
                res.season,
                res.episode,
                res.year,
                subtitleCallback,
                callback
            )
            }
        )
        return true
    }

}