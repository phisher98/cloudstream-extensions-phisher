package com.HindiProviders

import com.HindiProviders.CodeExtractor.invoke2embed
import com.HindiProviders.CodeExtractor.invokeAllMovieland
import com.HindiProviders.CodeExtractor.invokeAnimes
import com.HindiProviders.CodeExtractor.invokeAoneroom
import com.HindiProviders.CodeExtractor.invokeDoomovies
import com.HindiProviders.CodeExtractor.invokeDramaday
import com.HindiProviders.CodeExtractor.invokeDreamfilm
import com.HindiProviders.CodeExtractor.invokeFilmxy
import com.HindiProviders.CodeExtractor.invokeFlixon
import com.HindiProviders.CodeExtractor.invokeGoku
import com.HindiProviders.CodeExtractor.invokeKimcartoon
import com.HindiProviders.CodeExtractor.invokeKisskh
import com.HindiProviders.CodeExtractor.invokeLing
import com.HindiProviders.CodeExtractor.invokeM4uhd
import com.HindiProviders.CodeExtractor.invokeNinetv
import com.HindiProviders.CodeExtractor.invokeNowTv
import com.HindiProviders.CodeExtractor.invokeRStream
import com.HindiProviders.CodeExtractor.invokeRidomovies
//import com.HindiProviders.CodeExtractor.invokeSmashyStream
import com.HindiProviders.CodeExtractor.invokeDumpStream
import com.HindiProviders.CodeExtractor.invokeEmovies
import com.HindiProviders.CodeExtractor.invokeMultimovies
import com.HindiProviders.CodeExtractor.invokeNetmovies
import com.HindiProviders.CodeExtractor.invokeShowflix
import com.HindiProviders.CodeExtractor.invokeVidSrc
import com.HindiProviders.CodeExtractor.invokeVidsrcto
import com.HindiProviders.CodeExtractor.invokeCinemaTv
import com.HindiProviders.CodeExtractor.invokeMoflix
import com.HindiProviders.CodeExtractor.invokeGhostx
//import com.HindiProviders.CodeExtractor.invokeNepu
import com.HindiProviders.CodeExtractor.invokeWatchCartoon
import com.HindiProviders.CodeExtractor.invokeWatchsomuch
import com.HindiProviders.CodeExtractor.invokeZoechip
import com.HindiProviders.CodeExtractor.invokeZshow
import com.HindiProviders.CodeExtractor.invokeMoviesdrive
import com.HindiProviders.CodeExtractor.invokeVegamovies
import com.HindiProviders.CodeExtractor.invokeDotmovies
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class CodeStreamLite : CodeStream() {
    override var name = "CodeStream-Lite"

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = AppUtils.parseJson<LinkData>(data)

        argamap(
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
                invokeGoku(
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
            {
                if (!res.isAnime) invokeM4uhd(
                    res.title, res.airedYear
                        ?: res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeRStream(res.id, res.season, res.episode, callback)
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