package com.phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.StreamPlayExtractor.invoke2embed
import com.phisher98.StreamPlayExtractor.invoke4khdhub
import com.phisher98.StreamPlayExtractor.invokeAllMovieland
import com.phisher98.StreamPlayExtractor.invokeAnimes
import com.phisher98.StreamPlayExtractor.invokeBollyflix
import com.phisher98.StreamPlayExtractor.invokeCinemaOS
import com.phisher98.StreamPlayExtractor.invokeDahmerMovies
import com.phisher98.StreamPlayExtractor.invokeDotmovies
import com.phisher98.StreamPlayExtractor.invokeDramadrip
import com.phisher98.StreamPlayExtractor.invokeElevenmovies
import com.phisher98.StreamPlayExtractor.invokeEmbedlc
import com.phisher98.StreamPlayExtractor.invokeEmovies
import com.phisher98.StreamPlayExtractor.invokeExtramovies
import com.phisher98.StreamPlayExtractor.invokeFilm1k
import com.phisher98.StreamPlayExtractor.invokeHdmovie2
import com.phisher98.StreamPlayExtractor.invokeKisskh
import com.phisher98.StreamPlayExtractor.invokeKisskhAsia
import com.phisher98.StreamPlayExtractor.invokeMappleTv
import com.phisher98.StreamPlayExtractor.invokeMoflix
import com.phisher98.StreamPlayExtractor.invokeMovieBox
import com.phisher98.StreamPlayExtractor.invokeMoviehubAPI
import com.phisher98.StreamPlayExtractor.invokeMoviesdrive
import com.phisher98.StreamPlayExtractor.invokeMoviesmod
import com.phisher98.StreamPlayExtractor.invokeMultiEmbed
import com.phisher98.StreamPlayExtractor.invokeMultimovies
import com.phisher98.StreamPlayExtractor.invokeNepu
import com.phisher98.StreamPlayExtractor.invokeNinetv
import com.phisher98.StreamPlayExtractor.invokePlaydesi
import com.phisher98.StreamPlayExtractor.invokePlayer4U
import com.phisher98.StreamPlayExtractor.invokePrimeSrc
import com.phisher98.StreamPlayExtractor.invokeRidomovies
import com.phisher98.StreamPlayExtractor.invokeRiveStream
import com.phisher98.StreamPlayExtractor.invokeRogmovies
import com.phisher98.StreamPlayExtractor.invokeShowflix
import com.phisher98.StreamPlayExtractor.invokeSoapy
import com.phisher98.StreamPlayExtractor.invokeSuperstream
import com.phisher98.StreamPlayExtractor.invokeToonstream
import com.phisher98.StreamPlayExtractor.invokeTopMovies
import com.phisher98.StreamPlayExtractor.invokeUhdmovies
import com.phisher98.StreamPlayExtractor.invokeVegamovies
import com.phisher98.StreamPlayExtractor.invokeVidFast
import com.phisher98.StreamPlayExtractor.invokeVidPlus
import com.phisher98.StreamPlayExtractor.invokeVidSrcXyz
import com.phisher98.StreamPlayExtractor.invokeVidlink
import com.phisher98.StreamPlayExtractor.invokeVidnest
import com.phisher98.StreamPlayExtractor.invokeVidsrccc
import com.phisher98.StreamPlayExtractor.invokeVidzee
import com.phisher98.StreamPlayExtractor.invokeWatch32APIHQ
import com.phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.phisher98.StreamPlayExtractor.invokeXPrimeAPI
import com.phisher98.StreamPlayExtractor.invokeZoechip
import com.phisher98.StreamPlayExtractor.invokeZshow
import com.phisher98.StreamPlayExtractor.invokehdhub4u
import com.phisher98.StreamPlayExtractor.invokemorph
import com.phisher98.StreamPlayExtractor.invokemp4hydra
import com.phisher98.StreamPlayExtractor.invokevidrock

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


@RequiresApi(Build.VERSION_CODES.O)
fun buildProviders(): List<Provider> {
    return listOf(
        Provider("uhdmovies", "UHD Movies (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeUhdmovies(res.title, res.year, res.season, res.episode, callback, subtitleCallback)
        },
        Provider("anime", "All Anime Sources") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (res.isAnime) invokeAnimes(res.title, res.jpTitle, res.date, res.airedDate, res.season, res.episode, subtitleCallback, callback, res.isDub)
        },
        Provider("player4u", "Player4U") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokePlayer4U(res.title, res.season, res.episode, res.year, callback)
        },
        Provider("vidsrccc", "Vidsrccc") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidsrccc(res.id, res.season, res.episode, callback)
        },
        Provider("topmovies", "Top Movies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeTopMovies(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviesmod", "MoviesMod") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMoviesmod(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("bollyflix", "Bollyflix") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeBollyflix(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("watchsomuch", "WatchSoMuch") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("ninetv", "NineTV") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeNinetv(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("ridomovies", "RidoMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeRidomovies(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviehubapi", "MovieHub API") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMoviehubAPI(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("allmovieland", "AllMovieland") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeAllMovieland(res.imdbId, res.season, res.episode, callback)
        },
        Provider("multiembed", "MultiEmbed") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMultiEmbed(res.imdbId, res.season, res.episode, callback)
        },
        Provider("emovies", "EMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeEmovies(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vegamovies", "VegaMovies (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVegamovies(res.title, res.year, res.season, res.episode, res.imdbId, subtitleCallback, callback)
        },
        Provider("extramovies", "ExtraMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeExtramovies(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("multimovies", "MultiMovies (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("2embed", "2Embed") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invoke2embed(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("zshow", "ZShow") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeZshow(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("showflix", "ShowFlix (South Indian)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeShowflix(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moflix", "Moflix (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMoflix(res.id, res.season, res.episode, callback)
        },
        Provider("zoechip", "ZoeChip") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeZoechip(res.title, res.year, res.season, res.episode, callback)
        },
        Provider("nepu", "Nepu") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeNepu(res.title, res.airedYear ?: res.year, res.season, res.episode, callback)
        },
        Provider("playdesi", "PlayDesi") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokePlaydesi(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviesdrive", "MoviesDrive (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            invokeMoviesdrive(res.title, res.season, res.episode, res.year, res.imdbId, subtitleCallback, callback)
        },
        Provider("watch32APIHQ", "Watch32 API HQ (English)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeWatch32APIHQ(res.title, res.season, res.episode, res.year, subtitleCallback, callback)
        },
        Provider("primesrc", "PrimeSrc") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokePrimeSrc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("film1k", "Film1k") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeFilm1k(res.title, res.season, res.year, subtitleCallback, callback)
        },
        Provider("superstream", "SuperStream") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime && res.imdbId != null) invokeSuperstream(token, res.imdbId, res.season, res.episode, callback)
        },
        Provider("vidsrcxyz", "VidSrcXyz (English)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback)
        },
        Provider("xprimeapi", "XPrime API") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeXPrimeAPI(res.title, res.year, res.imdbId, res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidzeeapi", "Vidzee API") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidzee(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("4khdhub", "4kHdhub (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            invoke4khdhub(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("elevenmovies", "ElevenMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeElevenmovies(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdhub4u", "Hdhub4u (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokehdhub4u(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdmovie2", "Hdmovie2") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeHdmovie2(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("dramadrip", "Dramadrip (Asian Drama)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime)invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("embedlc", "EmbedLC") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeEmbedlc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("rivestream", "RiveStream") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeRiveStream(res.id, res.season, res.episode, callback)
        },
        Provider("moviebox", "MovieBox (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMovieBox(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("morph", "Morph") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokemorph(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidrock", "Vidrock") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokevidrock(res.id, res.season, res.episode, callback)
        },
        Provider("soapy", "Soapy") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeSoapy(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidlink", "Vidlink") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidlink(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("mappletv", "MappleTV") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMappleTv(res.id, res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidnest", "Vidnest") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidnest(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("dotmovies", "DotMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeDotmovies(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("rogmovies", "RogMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeRogmovies(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("kisskh", "KissKH (Asian Drama)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeKisskh(res.title, res.season, res.episode, res.lastSeason, subtitleCallback, callback)
        },
        Provider("cinemaos", "CinemaOS") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            invokeCinemaOS(res.imdbId, res.id, res.title, res.season, res.episode, res.year, callback, subtitleCallback)
        },
        Provider("dahmermovies", "DahmerMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeDahmerMovies(dahmerMoviesAPI, res.title, res.year, res.season, res.episode, callback)
        },
        Provider("KisskhAsia", "KissKhAsia (Asian Drama)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeKisskhAsia(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("mp4hydra", "MP4Hydra") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokemp4hydra(res.title, res.year,res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidfast", "VidFast") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidFast(res.id, res.season,res.episode, callback, subtitleCallback)
        },
        Provider("vidplus", "VidPlus") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidPlus(res.id, res.season,res.episode,  callback,subtitleCallback)
        },
        Provider("toonstream", "Toonstream (Hindi Anime)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (res.isAnime || res.isCartoon) invokeToonstream(res.title, res.season, res.episode, subtitleCallback, callback)
        }
    )
}
