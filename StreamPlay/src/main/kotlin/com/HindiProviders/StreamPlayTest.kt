package com.Phisher98

import android.util.Log
import com.Phisher98.StreamPlayExtractor.invokeAnimes
import com.Phisher98.StreamPlayExtractor.invokeAnitaku
import com.Phisher98.StreamPlayExtractor.invokeBollyflix
import com.Phisher98.StreamPlayExtractor.invokeDotmovies
import com.Phisher98.StreamPlayExtractor.invokeMoviesmod
import com.Phisher98.StreamPlayExtractor.invokeVegamovies
import com.Phisher98.StreamPlayExtractor.invokeMoviesdrive
import com.Phisher98.StreamPlayExtractor.invokeMultiEmbed
import com.Phisher98.StreamPlayExtractor.invokeTopMovies
import com.Phisher98.StreamPlayExtractor.invokeUhdmovies
import com.Phisher98.StreamPlayExtractor.invokemovies4u
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
                if (!res.isAnime) invokeMoviesdrive(
                    res.title,
                    res.season,
                    res.episode,
                    res.year,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeDotmovies(
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