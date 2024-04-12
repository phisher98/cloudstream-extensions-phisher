package com.KillerDogeEmpire

import com.KillerDogeEmpire.CodeExtractor.invoke2embed
import com.KillerDogeEmpire.CodeExtractor.invokeAllMovieland
import com.KillerDogeEmpire.CodeExtractor.invokeAnimes
import com.KillerDogeEmpire.CodeExtractor.invokeAoneroom
import com.KillerDogeEmpire.CodeExtractor.invokeDoomovies
import com.KillerDogeEmpire.CodeExtractor.invokeDramaday
import com.KillerDogeEmpire.CodeExtractor.invokeDreamfilm
import com.KillerDogeEmpire.CodeExtractor.invokeFilmxy
import com.KillerDogeEmpire.CodeExtractor.invokeFlixon
import com.KillerDogeEmpire.CodeExtractor.invokeGoku
import com.KillerDogeEmpire.CodeExtractor.invokeKimcartoon
import com.KillerDogeEmpire.CodeExtractor.invokeKisskh
import com.KillerDogeEmpire.CodeExtractor.invokeLing
import com.KillerDogeEmpire.CodeExtractor.invokeM4uhd
import com.KillerDogeEmpire.CodeExtractor.invokeNinetv
import com.KillerDogeEmpire.CodeExtractor.invokeNowTv
import com.KillerDogeEmpire.CodeExtractor.invokeRStream
import com.KillerDogeEmpire.CodeExtractor.invokeRidomovies
import com.KillerDogeEmpire.CodeExtractor.invokeSmashyStream
import com.KillerDogeEmpire.CodeExtractor.invokeDumpStream
import com.KillerDogeEmpire.CodeExtractor.invokeEmovies
import com.KillerDogeEmpire.CodeExtractor.invokeMultimovies
import com.KillerDogeEmpire.CodeExtractor.invokeNetmovies
import com.KillerDogeEmpire.CodeExtractor.invokeShowflix
import com.KillerDogeEmpire.CodeExtractor.invokeVidSrc
import com.KillerDogeEmpire.CodeExtractor.invokeVidsrcto
import com.KillerDogeEmpire.CodeExtractor.invokeCinemaTv
import com.KillerDogeEmpire.CodeExtractor.invokeMoflix
import com.KillerDogeEmpire.CodeExtractor.invokeGhostx
import com.KillerDogeEmpire.CodeExtractor.invokeNepu
import com.KillerDogeEmpire.CodeExtractor.invokeWatchCartoon
import com.KillerDogeEmpire.CodeExtractor.invokeWatchsomuch
import com.KillerDogeEmpire.CodeExtractor.invokeZoechip
import com.KillerDogeEmpire.CodeExtractor.invokeZshow
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
                invokeVidSrc(res.id, res.season, res.episode, callback)
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
            {
                if (!res.isAnime) invokeSmashyStream(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
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
                if (res.isBollywood) invokeMultimovies(
                    multimoviesAPI,
                    res.title,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (res.isBollywood) invokeMultimovies(
                    multimovies2API,
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
            {
                if (!res.isAnime) invokeNepu(
                    res.title,
                    res.airedYear ?: res.year,
                    res.season,
                    res.episode,
                    callback
                )
            }
        )

        return true
    }

}