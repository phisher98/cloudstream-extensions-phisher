package com.Phisher98

import com.Phisher98.StreamPlayExtractor.invoke2embed
import com.Phisher98.StreamPlayExtractor.invokeAllMovieland
import com.Phisher98.StreamPlayExtractor.invokeAnimes
import com.Phisher98.StreamPlayExtractor.invokeAoneroom
import com.Phisher98.StreamPlayExtractor.invokeBollyflix
//import com.Phisher98.StreamPlayExtractor.invokeDoomovies
import com.Phisher98.StreamPlayExtractor.invokeDramaday
import com.Phisher98.StreamPlayExtractor.invokeDreamfilm
import com.Phisher98.StreamPlayExtractor.invokeFilmxy
import com.Phisher98.StreamPlayExtractor.invokeFlixon
import com.Phisher98.StreamPlayExtractor.invokeKisskh
import com.Phisher98.StreamPlayExtractor.invokeLing
import com.Phisher98.StreamPlayExtractor.invokeM4uhd
import com.Phisher98.StreamPlayExtractor.invokeNinetv
import com.Phisher98.StreamPlayExtractor.invokeNowTv
import com.Phisher98.StreamPlayExtractor.invokeRidomovies
//import com.Phisher98.StreamPlayExtractor.invokeSmashyStream
import com.Phisher98.StreamPlayExtractor.invokeDumpStream
import com.Phisher98.StreamPlayExtractor.invokeEmovies
import com.Phisher98.StreamPlayExtractor.invokeMultimovies
import com.Phisher98.StreamPlayExtractor.invokeNetmovies
import com.Phisher98.StreamPlayExtractor.invokeShowflix
import com.Phisher98.StreamPlayExtractor.invokeMoflix
import com.Phisher98.StreamPlayExtractor.invokeGhostx
import com.Phisher98.StreamPlayExtractor.invokeWatchCartoon
import com.Phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.Phisher98.StreamPlayExtractor.invokeZoechip
import com.Phisher98.StreamPlayExtractor.invokeZshow
import com.Phisher98.StreamPlayExtractor.invokeFlicky
import com.Phisher98.StreamPlayExtractor.invokeFlixAPIHQ
import com.Phisher98.StreamPlayExtractor.invokeNepu
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class StreamPlayLite : StreamPlay() {
    override var name = "StreamPlay-Lite"

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = AppUtils.parseJson<LinkData>(data)
        argamap(
            {
                if (!res.isAnime) invokeM4uhd(
                    res.title, res.airedYear
                        ?: res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeBollyflix(
                    res.imdbId,
                    res.title,
                    res.year,
                    res.season,
                    res.lastSeason,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeFlicky(res.id, res.season, res.episode, callback)
            },
            {
                if (!res.isAnime) invokeMoflix(res.id, res.season, res.episode, callback)
            },
            {
                if (!res.isAnime) invokeWatchsomuch(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback
                )
            },
            {
                /*
                invokewhvx(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback
                )
                 */
            },
            {
                invokeDumpStream(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeNinetv(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime && res.isCartoon) invokeWatchCartoon(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (res.isAnime) invokeAnimes(
                    res.title,
                    res.jpTitle,
                    res.epsTitle,
                    res.date,
                    res.airedDate,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeDreamfilm(
                    res.title,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeFilmxy(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeGhostx(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (res.isAsian) invokeKisskh(
                    res.title,
                    res.season,
                    res.episode,
                    res.lastSeason,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeLing(
                    res.title, res.airedYear
                        ?: res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeFlixon(
                    res.id,
                    res.imdbId,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeNowTv(res.id, res.imdbId, res.season, res.episode, callback)
            },
            {
                if (!res.isAnime) invokeAoneroom(
                    res.title, res.airedYear
                        ?: res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeRidomovies(
                    res.id,
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeEmovies(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeMultimovies(
                    multimoviesAPI,
                    res.title,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeNetmovies(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeAllMovieland(res.imdbId, res.season, res.episode, callback)
            },
            {
                if (res.isAsian) invokeDramaday(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invoke2embed(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAsian && !res.isBollywood &&!res.isAnime) invokeZshow(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
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
                if (!res.isAnime) invokeZoechip(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeNepu(
                    res.title,
                    res.airedYear ?: res.year,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeFlicky(res.id, res.season, res.episode, callback)
            },
            {
                if (!res.isAnime) invokeFlixAPIHQ(
                    res.title,
                    res.year,
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