package com.phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.StreamPlayExtractor.invoke2embed
import com.phisher98.StreamPlayExtractor.invoke4khdhub
import com.phisher98.StreamPlayExtractor.invokeAllMovieland
import com.phisher98.StreamPlayExtractor.invokeAnichi
import com.phisher98.StreamPlayExtractor.invokeAnimeKai
import com.phisher98.StreamPlayExtractor.invokeAnimepahe
import com.phisher98.StreamPlayExtractor.invokeAnimetosho
import com.phisher98.StreamPlayExtractor.invokeAnimex
import com.phisher98.StreamPlayExtractor.invokeAnizone
import com.phisher98.StreamPlayExtractor.invokeBollyflix
import com.phisher98.StreamPlayExtractor.invokeCineVood
import com.phisher98.StreamPlayExtractor.invokeDahmerMovies
import com.phisher98.StreamPlayExtractor.invokeDooflix
import com.phisher98.StreamPlayExtractor.invokeDudefilms
import com.phisher98.StreamPlayExtractor.invokeFilmyfiy
import com.phisher98.StreamPlayExtractor.invokeHdmovie2
import com.phisher98.StreamPlayExtractor.invokeHexa
import com.phisher98.StreamPlayExtractor.invokeHianime
import com.phisher98.StreamPlayExtractor.invokeHindmoviez
import com.phisher98.StreamPlayExtractor.invokeKickAssAnime
import com.phisher98.StreamPlayExtractor.invokeKisskh
import com.phisher98.StreamPlayExtractor.invokeM4uhd
import com.phisher98.StreamPlayExtractor.invokeMapple
import com.phisher98.StreamPlayExtractor.invokeMovieBox
import com.phisher98.StreamPlayExtractor.invokeMovies4u
import com.phisher98.StreamPlayExtractor.invokeMoviesApi
import com.phisher98.StreamPlayExtractor.invokeMoviesdrive
import com.phisher98.StreamPlayExtractor.invokeMoviesmod
import com.phisher98.StreamPlayExtractor.invokeMultimovies
import com.phisher98.StreamPlayExtractor.invokeNepu
import com.phisher98.StreamPlayExtractor.invokeNinetv
import com.phisher98.StreamPlayExtractor.invokeReAnime
import com.phisher98.StreamPlayExtractor.invokeRiveStream
import com.phisher98.StreamPlayExtractor.invokeRogmovies
import com.phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.phisher98.StreamPlayExtractor.invokeSuperstream
import com.phisher98.StreamPlayExtractor.invokeTokyoInsider
import com.phisher98.StreamPlayExtractor.invokeTopMovies
import com.phisher98.StreamPlayExtractor.invokeUhdmovies
import com.phisher98.StreamPlayExtractor.invokeVegamovies
import com.phisher98.StreamPlayExtractor.invokeVidFast
import com.phisher98.StreamPlayExtractor.invokeVidSrcXyz
import com.phisher98.StreamPlayExtractor.invokeVideasy
import com.phisher98.StreamPlayExtractor.invokeVidlink
import com.phisher98.StreamPlayExtractor.invokeVidzee
import com.phisher98.StreamPlayExtractor.invokeWatch32APIHQ
import com.phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import com.phisher98.StreamPlayExtractor.invokeXpass
import com.phisher98.StreamPlayExtractor.invokeYflix
import com.phisher98.StreamPlayExtractor.invokeZshow
import com.phisher98.StreamPlayExtractor.invokecinemacity
import com.phisher98.StreamPlayExtractor.invokehdhub4u
import com.phisher98.StreamPlayExtractor.invokevaplayer
import com.phisher98.StreamPlayExtractor.invokevidrock
import com.phisher98.StreamPlayExtractor.resolveAnimeIds

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: StreamPlay.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        token: String,
        dahmerMoviesAPI: String
    ) -> Unit
)

private fun getDubStatus(res: StreamPlay.LinkData): String {
    return when {
        res.isMovie == true -> "Movie"
        res.isDub -> "DUB"
        else -> "SUB"
    }
}

private suspend fun getAnimeIds(res: StreamPlay.LinkData): StreamPlayExtractor.AnimeResolvedIds {
    val cacheKey = "${res.title}_${res.date ?: res.airedDate}_${res.season ?: 0}"

    val cached = StreamPlayCache.getCachedAnimeIds(cacheKey)
    if (cached != null) {
        return StreamPlayExtractor.AnimeResolvedIds(
            malId = cached.malId?.toIntOrNull(),
            anilistId = cached.anilistId?.toIntOrNull(),
            anidbEid = 0,
            zoroIds = cached.zoroId?.split(",")?.filter { it.isNotBlank() },
            zoroTitle = null,
            aniXL = null,
            kaasSlug = null,
            animepaheUrl = null,
            animekaiId = cached.animekaiId,
            tmdbYear = null
        )
    }

    val ids = resolveAnimeIds(res.title, res.date, res.airedDate, res.season, res.episode)

    StreamPlayCache.cacheAnimeIds(
        cacheKey,
        StreamPlayCache.AnimeIdMapping(
            anilistId = null,
            malId = ids.malId?.toString(),
            kitsuId = null,
            zoroId = ids.zoroIds?.joinToString(",")
        )
    )

    return ids
}

private val providers by lazy {
    listOf(
        Provider("uhdmovies", "UHD Movies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeUhdmovies(res.title, res.year, res.season, res.episode, callback, subtitleCallback)
        },
        Provider("hianime", "HiAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.malId?.let { invokeHianime(it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("animetosho", "AnimeTosho") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.malId?.let { invokeAnimetosho(it, res.episode, subtitleCallback, callback, getDubStatus(res), ids.anidbEid) }
            }
        },
        Provider("ReAnime", "ReAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.anilistId?.let { invokeReAnime(it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("animekai", "AnimeKai") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.animekaiId?.let { invokeAnimeKai(it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("Animex", "Animex") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                invokeAnimex(ids.malId, ids.anilistId, res.title, res.episode, subtitleCallback, callback, getDubStatus(res))
            }
        },
        Provider("kickass", "KickAssAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.kaasSlug?.let { invokeKickAssAnime(res.title, it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("animepahe", "AnimePahe") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                ids.animepaheUrl?.let { invokeAnimepahe(it, res.episode, subtitleCallback, callback, getDubStatus(res)) }
            }
        },
        Provider("anichi", "Anichi / AllAnime") { res, subtitleCallback, callback, _, _ ->
            if (res.isAnime) {
                val ids = getAnimeIds(res)
                invokeAnichi(ids.zoroTitle, res.title, ids.tmdbYear, res.episode, subtitleCallback, callback, getDubStatus(res))
            }
        },
        Provider("tokyoinsider", "Tokyo Insider") { res, _, callback, _, _ ->
            if (res.isAnime) {
                invokeTokyoInsider(res.jpTitle, res.title, res.episode, callback, getDubStatus(res))
            }
        },
        Provider("anizone", "AniZone") { res, _, callback, _, _ ->
            if (res.isAnime) {
                invokeAnizone(res.jpTitle, res.title, res.episode, callback, getDubStatus(res))
            }
        },
        Provider("topmovies", "Top Movies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeTopMovies(res.imdbId,
                res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviesmod", "MoviesMod") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeMoviesmod(res.title,res.imdbId,
                res.season, res.episode, subtitleCallback, callback)
        },
        Provider("bollyflix", "Bollyflix") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeBollyflix(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("watchsomuch", "WatchSoMuch") { res, subtitleCallback, _, _, _ ->
            if (!res.isAnime) invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("ninetv", "NineTV") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeNinetv(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("allmovieland", "AllMovieland") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeAllMovieland(res.imdbId, res.season, res.episode, callback)
        },
        Provider("vegamovies", "VegaMovies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isBollywood) invokeVegamovies(res.title, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("Rogmovies", "RogMovies") { res, subtitleCallback, callback, _, _ ->
            if (res.isBollywood) invokeRogmovies(res.title, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("multimovies", "MultiMovies") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("zshow", "ZShow") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeZshow(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("nepu", "Nepu") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeNepu(res.title, res.airedYear ?: res.year, res.season, res.episode, callback)
        },
        Provider("moviesdrive", "MoviesDrive") { res, subtitleCallback, callback, _, _ ->
            invokeMoviesdrive(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("watch32APIHQ", "Watch32 API HQ") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeWatch32APIHQ(res.title, res.season, res.episode,
                subtitleCallback, callback)
        },
        Provider("superstream", "SuperStream") { res, _, callback, token, _ ->
            val status = getDubStatus(res)
            val isAnime = res.isAnime
            if (isAnime && status != "SUB") return@Provider

            if (res.imdbId != null && token.isNotEmpty()) {
                invokeSuperstream(
                    token,
                    res.imdbId,
                    res.id,
                    res.season,
                    res.episode,
                    callback
                )
            }
        },
        Provider("vidsrcxyz", "VidSrcXyz") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback)
        },
        Provider("vidzeeapi", "Vidzee API") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeVidzee(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("4khdhub", "4kHdhub (Multi)") { res, subtitleCallback, callback, _, _ ->
            invoke4khdhub(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdhub4u", "Hdhub4u (Multi)") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokehdhub4u(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdmovie2", "Hdmovie2") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeHdmovie2(res.title, res.year,
                res.episode, subtitleCallback, callback)
        },
        Provider("rivestream", "RiveStream") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeRiveStream(res.id, res.season, res.episode, callback)
        },
        Provider("moviebox", "MovieBox (Multi)") { res, subtitleCallback, callback, _, _ ->
            invokeMovieBox(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidrock", "Vidrock") { res, _, callback, _, _ ->
            if (!res.isAnime) invokevidrock(res.id, res.season, res.episode, callback)
        },
        Provider("vidlink", "Vidlink") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeVidlink(res.id, res.season, res.episode, callback)
        },
        Provider("kisskh", "KissKH (Asian Drama)") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeKisskh(res.title, res.season, res.episode, res.lastSeason, subtitleCallback, callback)
        },
        Provider("dahmermovies", "DahmerMovies") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeDahmerMovies(res.title, res.year, res.season, res.episode, callback)
        },
        Provider("vidfast", "VidFast") { res, _, callback, _, _ ->
            invokeVidFast(res.id, res.season,res.episode, callback)
        },
        Provider("VidEasy", "VidEasy") { res, subtitleCallback, callback, _, _ ->
            invokeVideasy(res.title,res.id, res.imdbId, res.year, res.season,res.episode, subtitleCallback, callback )
        },
        Provider("YFlix", "YFlix") { res, subtitleCallback, callback, _, _ ->
            val status = getDubStatus(res)
            val isAnime = res.isAnime
            if (isAnime && status != "SUB") return@Provider
            invokeYflix(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviesapi", "MoviesApi Club") { res, _, callback, _, _ ->
            invokeMoviesApi(res.id, res.season, res.episode, callback)
        },
        Provider("CinemaCity", "CinemaCity") { res, _, callback, _, _ ->
            invokecinemacity(res.imdbId, res.season,res.episode,  callback)
        },
        Provider("HexaSU", "HexaSU") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeHexa(res.id, res.season, res.episode, callback)
        },
        Provider("Hindmoviez", "HindMoviez") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeHindmoviez(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("Movies4u", "Movies4u") { res, subtitleCallback, callback, _, _ ->
            invokeMovies4u(res.imdbId, res.title,res.year, res.season, res.episode, subtitleCallback ,callback)
        },
        Provider("M4uhd", "M4uhd") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeM4uhd(res.title,
                res.season, res.episode, subtitleCallback ,callback)
        },
        Provider("MappleTV", "MappleTV") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeMapple(res.id, res.season, res.episode ,callback)
        },
        Provider("WyZIESUB", "WyZIESUB (Subtitles)") { res, subtitleCallback, _, _, _ ->
            invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("SubtitleAPI", "SubtitleAPI (Subtitles)") { res, subtitleCallback, _, _, _ ->
            invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("CineVood", "CineVood (Movies Only)") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeCineVood(res.imdbId, subtitleCallback, callback)
        },
        Provider("Filmyfiy", "Filmyfiy (Movies Only)") { res, sub, cb, _, _ ->
            if (!res.isAnime && res.season == null) invokeFilmyfiy(res.title, sub, cb)
        },
        Provider("2Embed", "2Embed") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invoke2embed(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("DooFlix", "DooFlix") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeDooflix(res.id, res.season, res.episode, callback)
        },
        Provider("Xpass", "Xpass") { res, _, callback, _, _ ->
            if (!res.isAnime) invokeXpass(res.id, res.season, res.episode, callback, )
        },
        Provider("vaplayer", "Vaplayer") { res, _, callback, _, _ ->
            if (!res.isAnime) invokevaplayer(res.id, res.season, res.episode, callback)
        },
        Provider("Dudefilms", "Dudefilms") { res, subtitleCallback, callback, _, _ ->
            if (!res.isAnime) invokeDudefilms(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        }
    )
}

fun buildProviders(): List<Provider> = providers
