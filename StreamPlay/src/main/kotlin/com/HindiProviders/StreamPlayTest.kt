package com.HindiProviders

import com.HindiProviders.StreamPlayExtractor.invoke2embed
import com.HindiProviders.StreamPlayExtractor.invokeAllMovieland
import com.HindiProviders.StreamPlayExtractor.invokeAnimes
import com.HindiProviders.StreamPlayExtractor.invokeAoneroom
import com.HindiProviders.StreamPlayExtractor.invokeBollyflix
import com.HindiProviders.StreamPlayExtractor.invokeDoomovies
import com.HindiProviders.StreamPlayExtractor.invokeDramaday
import com.HindiProviders.StreamPlayExtractor.invokeDreamfilm
import com.HindiProviders.StreamPlayExtractor.invokeFilmxy
import com.HindiProviders.StreamPlayExtractor.invokeFlixon
import com.HindiProviders.StreamPlayExtractor.invokeGoku
import com.HindiProviders.StreamPlayExtractor.invokeKimcartoon
import com.HindiProviders.StreamPlayExtractor.invokeKisskh
import com.HindiProviders.StreamPlayExtractor.invokeLing
import com.HindiProviders.StreamPlayExtractor.invokeM4uhd
import com.HindiProviders.StreamPlayExtractor.invokeNinetv
import com.HindiProviders.StreamPlayExtractor.invokeNowTv
import com.HindiProviders.StreamPlayExtractor.invokeRidomovies
//import com.HindiProviders.StreamPlayExtractor.invokeSmashyStream
import com.HindiProviders.StreamPlayExtractor.invokeDumpStream
import com.HindiProviders.StreamPlayExtractor.invokeEmovies
import com.HindiProviders.StreamPlayExtractor.invokeMultimovies
import com.HindiProviders.StreamPlayExtractor.invokeNetmovies
import com.HindiProviders.StreamPlayExtractor.invokeShowflix
import com.HindiProviders.StreamPlayExtractor.invokeVidSrc
import com.HindiProviders.StreamPlayExtractor.invokeVidsrcto
import com.HindiProviders.StreamPlayExtractor.invokeCinemaTv
import com.HindiProviders.StreamPlayExtractor.invokeMoflix
import com.HindiProviders.StreamPlayExtractor.invokeGhostx
//import com.HindiProviders.StreamPlayExtractor.invokeNepu
import com.HindiProviders.StreamPlayExtractor.invokeWatchCartoon
import com.HindiProviders.StreamPlayExtractor.invokeWatchsomuch
import com.HindiProviders.StreamPlayExtractor.invokeZoechip
import com.HindiProviders.StreamPlayExtractor.invokeZshow
import com.HindiProviders.StreamPlayExtractor.invokeMoviesdrive
import com.HindiProviders.StreamPlayExtractor.invokeVegamovies
import com.HindiProviders.StreamPlayExtractor.invokeDotmovies
import com.HindiProviders.StreamPlayExtractor.invokeMoviesmod
import com.HindiProviders.StreamPlayExtractor.invokeTopMovies
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
            { if (!res.isAnime)
                invokeTopMovies(
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