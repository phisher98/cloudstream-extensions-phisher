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
import com.HindiProviders.StreamPlayExtractor.invokeTopMovies
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
                { if (!res.isAnime)
                invokeM4uhd(res.title, res.airedYear?: res.year, res.season, res.episode,subtitleCallback,callback)
                invokeBollyflix(res.title,res.year,res.season,res.lastSeason,res.episode,subtitleCallback,callback)
                invokeMoflix(res.id, res.season, res.episode, callback)
                invokeWatchsomuch(res.imdbId,res.season,res.episode,subtitleCallback)
                invokeMoviesdrive(res.title,res.season,res.episode,res.year,subtitleCallback,callback)
                invokeTopMovies(res.title,res.year,res.season,res.lastSeason,res.episode,subtitleCallback,callback)
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
                invokeVidSrc(res.id, res.season, res.episode, subtitleCallback,callback)
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
                if (!res.isAnime && res.isCartoon) invokeKimcartoon(
                    res.title,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
        /*    {
                if (!res.isAnime) invokeSmashyStream(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
         */
            {
                if (!res.isAnime) invokeVidsrcto(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (res.isAsian || res.isAnime) invokeKisskh(
                    res.title,
                    res.season,
                    res.episode,
                    res.isAnime,
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
            /*{
                if (!res.isAnime) invokeM4uhd(
                    res.title, res.airedYear
                        ?: res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
             */
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
                invokeCinemaTv(
                    res.imdbId, res.title, res.airedYear
                        ?: res.year, res.season, res.episode, subtitleCallback, callback
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
                if (!res.isAnime && res.season == null) invokeDoomovies(
                    res.title,
                    subtitleCallback,
                    callback
                )
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
                invokeZshow(
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
            /*{
                if (!res.isAnime) invokeNepu(
                    res.title,
                    res.airedYear ?: res.year,
                    res.season,
                    res.episode,
                    callback
                )
            }

             */
            {
                if (!res.isAnime) invokeVegamovies(
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
                if (!res.isAnime) invokeDotmovies(
                    res.title,
                    res.year,
                    res.season,
                    res.lastSeason,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
        )
        return true
    }

}