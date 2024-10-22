package com.Phisher98

import android.util.Log
import com.Phisher98.StreamPlayExtractor.invokeAnimes
import com.Phisher98.StreamPlayExtractor.invokeAnitaku
import com.Phisher98.StreamPlayExtractor.invokeAsianHD
import com.Phisher98.StreamPlayExtractor.invokeBollyflix
import com.Phisher98.StreamPlayExtractor.invokeDotmovies
import com.Phisher98.StreamPlayExtractor.invokeDramaCool
import com.Phisher98.StreamPlayExtractor.invokeKisskh
import com.Phisher98.StreamPlayExtractor.invokeMoviesmod
import com.Phisher98.StreamPlayExtractor.invokeVegamovies
import com.Phisher98.StreamPlayExtractor.invokeMoviesdrive
import com.Phisher98.StreamPlayExtractor.invokeMultiEmbed
import com.Phisher98.StreamPlayExtractor.invokeStarkflix
import com.Phisher98.StreamPlayExtractor.invokeTopMovies
import com.Phisher98.StreamPlayExtractor.invokeUhdmovies
import com.Phisher98.StreamPlayExtractor.invokecatflix
import com.Phisher98.StreamPlayExtractor.invokekissasian
import com.Phisher98.StreamPlayExtractor.invokemovies4u
import com.Phisher98.StreamPlayExtractor.invokewhvx
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
                if (res.isAsian) invokeAsianHD(
                    res.title,
                    res.year,
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