package com.Phisher98

import com.Phisher98.StreamPlayExtractor.invokeAnimes
import com.Phisher98.StreamPlayExtractor.invokeKisskh
import com.Phisher98.StreamPlayExtractor.invokeMoviehubAPI
import com.Phisher98.StreamPlayExtractor.invokeMoviesmod
import com.Phisher98.StreamPlayExtractor.invokeSuperstream
import com.Phisher98.StreamPlayExtractor.invokeTopMovies
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
            {
                invokeSuperstream(
                    res.imdbId,
                    res.season,
                    res.episode,
                    callback
                )
            }
        )
        return true
    }

}