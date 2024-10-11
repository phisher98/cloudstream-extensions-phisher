package com.Phisher98

//import com.Phisher98.StreamPlayExtractor.invokeWatchasian
import android.util.Log
import com.Phisher98.StreamPlayExtractor.invoke2embed
import com.Phisher98.StreamPlayExtractor.invokeAllMovieland
import com.Phisher98.StreamPlayExtractor.invokeAnimes
import com.Phisher98.StreamPlayExtractor.invokeAnitaku
import com.Phisher98.StreamPlayExtractor.invokeMoviesdrive
import com.Phisher98.StreamPlayExtractor.invokeAoneroom
import com.Phisher98.StreamPlayExtractor.invokeAsianHD
import com.Phisher98.StreamPlayExtractor.invokeAsiandrama
//import com.Phisher98.StreamPlayExtractor.invokeCinemaTv
import com.Phisher98.StreamPlayExtractor.invokeMoviesmod
import com.Phisher98.StreamPlayExtractor.invokeDahmerMovies
import com.Phisher98.StreamPlayExtractor.invokeDoomovies
import com.Phisher98.StreamPlayExtractor.invokeDotmovies
import com.Phisher98.StreamPlayExtractor.invokeDramacool
import com.Phisher98.StreamPlayExtractor.invokeDramaday
import com.Phisher98.StreamPlayExtractor.invokeDreamfilm
import com.Phisher98.StreamPlayExtractor.invokeDumpStream
import com.Phisher98.StreamPlayExtractor.invokeEmovies
import com.Phisher98.StreamPlayExtractor.invokeazseries
import com.Phisher98.StreamPlayExtractor.invokeFDMovies
import com.Phisher98.StreamPlayExtractor.invokeFilmxy
import com.Phisher98.StreamPlayExtractor.invokeFlixon
import com.Phisher98.StreamPlayExtractor.invokeGhostx
//import com.Phisher98.StreamPlayExtractor.invokeGoku
//import com.Phisher98.StreamPlayExtractor.invokeHdmovies4u
import com.Phisher98.StreamPlayExtractor.invokecatflix
import com.Phisher98.StreamPlayExtractor.invokeKimcartoon
import com.Phisher98.StreamPlayExtractor.invokeKisskh
import com.Phisher98.StreamPlayExtractor.invokeLing
import com.Phisher98.StreamPlayExtractor.invokeM4uhd
import com.Phisher98.StreamPlayExtractor.invokeMoflix
import com.Phisher98.StreamPlayExtractor.invokeMoviehubAPI
import com.Phisher98.StreamPlayExtractor.invokeMultiEmbed
import com.Phisher98.StreamPlayExtractor.invokeMultimovies
import com.Phisher98.StreamPlayExtractor.invokeNepu
import com.Phisher98.StreamPlayExtractor.invokeNetmovies
import com.Phisher98.StreamPlayExtractor.invokeNinetv
import com.Phisher98.StreamPlayExtractor.invokeNoverse
import com.Phisher98.StreamPlayExtractor.invokeNowTv
import com.Phisher98.StreamPlayExtractor.invokeRidomovies
import com.Phisher98.StreamPlayExtractor.invokeShowflix
import com.Phisher98.StreamPlayExtractor.invokeSmashyStream
import com.Phisher98.StreamPlayExtractor.invokeTopMovies
import com.Phisher98.StreamPlayExtractor.invokeTvMovies
import com.Phisher98.StreamPlayExtractor.invokeUhdmovies
import com.Phisher98.StreamPlayExtractor.invokeVegamovies
import com.Phisher98.StreamPlayExtractor.invokeVidSrc
import com.Phisher98.StreamPlayExtractor.invokeVidsrcto
import com.Phisher98.StreamPlayExtractor.invokeWatchCartoon
import com.Phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.Phisher98.StreamPlayExtractor.invokeZoechip
import com.Phisher98.StreamPlayExtractor.invokeZshow
import com.Phisher98.StreamPlayExtractor.invokekissasian
import com.Phisher98.StreamPlayExtractor.invokePlaydesi
import com.Phisher98.StreamPlayExtractor.invokeBollyflix
import com.Phisher98.StreamPlayExtractor.invokemovies4u
import com.Phisher98.StreamPlayExtractor.invokewhvx
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlin.math.roundToInt
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.newAnimeLoadResponse
open class StreamPlay : TmdbProvider() {
    override var name = "StreamPlay"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    /** AUTHOR : hexated & Code */
    companion object {
        /** TOOLS */
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        const val gdbot = "https://gdtot.pro"
        const val anilistAPI = "https://graphql.anilist.co"
        const val malsyncAPI = "https://api.malsync.moe"
        const val jikanAPI = "https://api.jikan.moe/v4"

        private const val apiKey = BuildConfig.TMDB_API

        /** ALL SOURCES */
        const val twoEmbedAPI = "https://www.2embed.cc"
        const val vidSrcAPI = "https://vidsrc.me"
        const val dreamfilmAPI = "https://dreamfilmsw.info"
        const val noverseAPI = "https://www.thenollyverse.com"
        const val filmxyAPI = "https://www.filmxy.online"
        const val MOVIE_API = BuildConfig.MOVIE_API
        const val kimcartoonAPI = "https://kimcartoon.li"
        const val hianimeAPI = "https://hianime.to"
        const val aniwaveAPI = "https://aniwave.to"
        const val anitaku = "https://anitaku.pe"
        const val MultiEmbedAPI = "https://multiembed.mov"
        const val crunchyrollAPI = "https://beta-api.crunchyroll.com"
        const val kissKhAPI = "https://kisskh.co"
        const val lingAPI = "https://ling-online.net"
        const val AsianhdAPI = "https://asianhdplay.in"
        const val KissasianAPI= "https://ww2.kissasian.vip"
        const val WatchasinAPI = "https://watchasia.to"
        const val m4uhdAPI = "https://ww1.streamm4u.ws"
        const val flixonAPI = "https://myflixer.lol"
        const val azseriesAPI = "https://azseries.org"
        const val PlaydesiAPI = "https://playdesi.net"
        const val smashyStreamAPI = "https://api.smashystream.top"
        const val watchSomuchAPI = "https://watchsomuch.tv" // sub only
        const val cinemaTvAPI = BuildConfig.CINEMATV_API
        const val Whvx_API = BuildConfig.Whvx_API
        const val nineTvAPI = "https://moviesapi.club"
        const val nowTvAPI = "https://myfilestorage.xyz"
        //const val gokuAPI = "https://goku.sx"
        const val zshowAPI = BuildConfig.ZSHOW_API
        const val ridomoviesAPI = "https://ridomovies.tv"
        const val emoviesAPI = "https://emovies.si"
        const val multimoviesAPI = "https://multimovies.sbs"
        const val netmoviesAPI = "https://web.netmovies.to"
        const val allmovielandAPI = "https://allmovieland.fun"
        const val doomoviesAPI = "https://doomovies.net"
        const val vidsrctoAPI = "https://vidsrc.cc"
        const val dramadayAPI = "https://dramaday.me"
        const val animetoshoAPI = "https://animetosho.org"
        const val showflixAPI = "https://showflix.xyz"
        const val aoneroomAPI = "https://api3.aoneroom.com"
        const val watchCartoonAPI = "https://www1.watchcartoononline.bz"
        const val moflixAPI = "https://moflix-stream.xyz"
        const val zoechipAPI = "https://www1.zoechip.to"
        const val nepuAPI = "https://nepu.to"
        const val DramacoolAPI = "https://watchasia.to"
        const val fdMoviesAPI = "https://freedrivemovie.com"
        const val uhdmoviesAPI = "https://uhdmovies.mov"
        const val topmoviesAPI = "https://topmovies.mov"
        const val MoviesmodAPI= "https://moviesmod.day"
        const val hdmovies4uAPI = "https://hdmovies4u.boston"
        const val vegaMoviesAPI = "https://vegamovies.foo"
        const val dotmoviesAPI = "https://luxmovies.shop"
        const val tvMoviesAPI = "https://www.tvseriesnmovies.com"
        const val dahmerMoviesAPI="https://a.datadiff.us.kg"
        const val MovieDrive_API="https://moviesdrive.world"
        const val Asiandrama_API=BuildConfig.AsianDrama_API
        const val bollyflixAPI = "https://bollyflix.beer"
        const val movies4u = "https://movies4u.gb.net"
        const val animepaheAPI = "https://animepahe.ru"
        const val Catflix= "https://catflix.su"
        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

    }

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/all/day?api_key=$apiKey&region=US" to "Trending",
        "$tmdbAPI/movie/popular?api_key=$apiKey&region=US" to "Popular Movies",
        "$tmdbAPI/tv/popular?api_key=$apiKey&region=US&with_original_language=en" to "Popular TV Shows",
        "$tmdbAPI/tv/airing_today?api_key=$apiKey&region=US&with_original_language=en" to "Airing Today TV Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2739" to "Disney+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=453" to "Hulu",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2552" to "Apple TV+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=49" to "HBO",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4330" to "Paramount+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=3353" to "Peacock",
        "$tmdbAPI/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "$tmdbAPI/movie/upcoming?api_key=$apiKey&region=US" to "Upcoming Movies",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().today}&air_date.gte=${getDate().today}" to "Airing Today Anime",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().nextWeek}&air_date.gte=${getDate().today}" to "On The Air Anime",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243" to "Anime",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_keywords=210024|222243" to "Anime Movies",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("${request.data}$adultQuery&page=$page")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse(type)
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        Log.d("Test1","$type")
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=$append"
        }
        Log.d("Test1",resUrl)
        println(resUrl)
        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val rating = res.vote_average.toString().toRatingInt()
        val genres = res.genres?.mapNotNull { it.name }

        val isCartoon = genres?.contains("Animation") ?: false
        val isAnime = isCartoon && (res.original_language == "zh" || res.original_language == "ja")
        val isAsian = !isAnime && (res.original_language == "zh" || res.original_language == "ko")
        val isBollywood = res.production_countries?.any { it.name == "India" } ?: false

        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }

        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: cast.originalName
                    ?: return@mapNotNull null, getImageUrl(cast.profilePath)
                ), roleString = cast.character
            )
        } ?: return null
        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results?.filter { it.type == "Trailer" }?.map { "https://www.youtube.com/watch?v=${it.key}" }?.reversed().orEmpty()
            .ifEmpty { res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" } }

        if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        Episode(
                            LinkData(
                                data.id,
                                res.external_ids?.imdb_id,
                                res.external_ids?.tvdb_id,
                                data.type,
                                eps.seasonNumber,
                                eps.episodeNumber,
                                title = title,
                                year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                                orgTitle = orgTitle,
                                isAnime = isAnime,
                                airedYear = year,
                                lastSeason = lastSeason,
                                epsTitle = eps.name,
                                jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                                date = season.airDate,
                                airedDate = res.releaseDate
                                    ?: res.firstAirDate,
                                isAsian = isAsian,
                                isBollywood = isBollywood,
                                isCartoon = isCartoon
                            ).toJson(),
                            name = eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else "",
                            season = eps.seasonNumber,
                            episode = eps.episodeNumber,
                            posterUrl = getImageUrl(eps.stillPath),
                            rating = eps.voteAverage?.times(10)?.roundToInt(),
                            description = eps.overview
                        ).apply {
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            if (isAnime) {
                return newAnimeLoadResponse(title, url, TvType.Anime) {
                    addEpisodes(DubStatus.Subbed, episodes)
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = keywords
                        ?.map { word -> word.replaceFirstChar { it.titlecase() } }
                        ?.takeIf { it.isNotEmpty() }
                        ?: genres
                    this.rating = rating
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    this.contentRating = fetchContentRating(data.id, "US")
                    addTrailer(trailer)
                    addTMDbId(data.id.toString())
                    addImdbId(res.external_ids?.imdb_id)
                }
            } else {
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = keywords
                        ?.map { word -> word.replaceFirstChar { it.titlecase() } }
                        ?.takeIf { it.isNotEmpty() }
                        ?: genres

                    this.rating = rating
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    this.contentRating = fetchContentRating(data.id, "US")
                    addTrailer(trailer)
                    addTMDbId(data.id.toString())
                    addImdbId(res.external_ids?.imdb_id)
                }
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(
                    data.id,
                    res.external_ids?.imdb_id,
                    res.external_ids?.tvdb_id,
                    data.type,
                    title = title,
                    year = year,
                    orgTitle = orgTitle,
                    isAnime = isAnime,
                    jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                    airedDate = res.releaseDate
                        ?: res.firstAirDate,
                    isAsian = isAsian,
                    isBollywood = isBollywood
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.comingSoon = isUpcoming(releaseDate)
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords
                    ?.map { word -> word.replaceFirstChar { it.titlecase() } }
                    ?.takeIf { it.isNotEmpty() }
                    ?: genres

                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                this.contentRating = fetchContentRating(data.id, "US")
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        Log.d("Test1", "$res")
        println(res)
        argamap({
                    invokeDumpStream(
                        res.title,
                        res.year,
                        res.season,
                        res.episode,
                        subtitleCallback,
                        callback
                    )
                },
            /*
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
             */
                {
                    invokeVidSrc(res.id, res.season, res.episode, subtitleCallback,callback)
                },
                {
                    if (!res.isAnime) invokeAoneroom(
                        res.title, res.airedYear
                            ?: res.year, res.season, res.episode, subtitleCallback, callback
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
                    invokeNoverse(res.title, res.season, res.episode, callback)
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
                    if (!res.isAnime && res.isCartoon) invokeKimcartoon(
                        res.title,
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
                    if (!res.isAnime) invokeVidsrcto(
                        res.imdbId,
                        res.season,
                        res.episode,
                        subtitleCallback,
                        callback
                    )
                },
                {
                   if (res.isAsian && !res.isAnime) invokeKisskh(
                        res.title,
                        res.season,
                        res.episode,
                        res.lastSeason,
                        subtitleCallback,
                        callback
                    )
                },
                {
                    if (!res.isAnime) invokeazseries(
                    res.title,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
                },
                {
                    //         if (res.isAsian) invokeWatchasian(
                    //            res.title,
                    //           res.year,
                    //          res.episode,
                    //         subtitleCallback,
                    //         callback
                    //    )
                },
                {
                    if (res.isAsian) invokeDramacool(
                        res.title,
                        res.season,
                        res.year,
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
                    if (!res.isAnime) invokeLing(
                        res.title, res.airedYear
                            ?: res.year, res.season, res.episode, subtitleCallback, callback
                    )
                },
                {
                    invokeUhdmovies(
                        res.title,
                        res.year,
                        res.season,
                        res.episode,
                        callback,
                        subtitleCallback
                    )
                },
                {
                    if (!res.isAnime) invokeTopMovies(
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
                if (!res.isAnime && !res.isBollywood) invokeMoviesmod(
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
                if (!res.isAnime) invokeBollyflix(
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
                if (!res.isAnime) invokemovies4u(
                    res.title,
                    res.episode,
                    res.season,
                    res.year,
                    subtitleCallback,
                    callback
                )
            },
        {
        if (!res.isAnime) invokeFDMovies(res.title, res.season, res.episode, callback)
    },
    {
        if (!res.isAnime) invokeM4uhd(
            res.title, res.airedYear
                ?: res.year, res.season, res.episode, subtitleCallback, callback
        )
    },
    {
        if (!res.isAnime) invokeTvMovies(res.title, res.season, res.episode, callback)
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
          if (!res.isAnime) invokeSmashyStream(
            res.id,
            res.season,
            res.episode,
            subtitleCallback,
            callback
        )
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
        invokewhvx(
            res.imdbId,
            res.season,
            res.episode,
            subtitleCallback
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
        invokeDahmerMovies(dahmerMoviesAPI,res.title, res.year, res.season, res.episode, callback)
    },
/*    {
        invokeCinemaTv(
            res.imdbId, res.title, res.airedYear
                ?: res.year, res.season, res.episode, subtitleCallback, callback
        )
    },
 */
    {
        if (!res.isAnime) invokeNowTv(
            res.id,
            res.imdbId,
            res.season,
            res.episode,
            callback
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
        if (!res.isAnime) invokeMoviehubAPI(
            res.id,
            res.imdbId,
            res.season,
            res.episode,
            subtitleCallback,
            callback
        )
    },
    {
        if (!res.isAnime) invokeAllMovieland(
            res.imdbId,
            res.season,
            res.episode,
            callback
        )
    },
    {
    if (!res.isAnime) invokeMultiEmbed(
        res.imdbId,
        res.season,
        res.episode,
        callback
        )
    },
    {
       if (!res.isAnime) invokecatflix(
           res.title,
           res.episode,
           res.season,
           res.year,
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
        if (!res.isAnime && !res.isBollywood) invokeVegamovies(
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
          /*
        if (!res.isAnime) invokeHdmovies4u(
            res.title,
            res.imdbId,
            res.season,
            res.episode,
            subtitleCallback,
            callback
        )
           */
    },
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
    {
        if (res.isAsian) invokekissasian(
            res.title,
            res.year,
            res.episode,
            callback
        )
    },
    {
        if (!res.isAsian && !res.isBollywood) invokeZshow(
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
        if (!res.isAnime) invokeMoflix(res.id, res.season, res.episode, callback)
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
        if (!res.isAnime) invokePlaydesi(
            res.title,
            res.season,
            res.episode,
            subtitleCallback,
            callback
        )
    },
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
    if (!res.isAnime) invokeAsiandrama(
        res.title,
        res.season,
        res.episode,
        res.year,
        subtitleCallback,
        callback
    )
}

)
return true
}

data class LinkData(
val id: Int? = null,
val imdbId: String? = null,
val tvdbId: Int? = null,
val type: String? = null,
val season: Int? = null,
val episode: Int? = null,
val aniId: String? = null,
val animeId: String? = null,
val title: String? = null,
val year: Int? = null,
val orgTitle: String? = null,
val isAnime: Boolean = false,
val airedYear: Int? = null,
val lastSeason: Int? = null,
val epsTitle: String? = null,
val jpTitle: String? = null,
val date: String? = null,
val airedDate: String? = null,
val isAsian: Boolean = false,
val isBollywood: Boolean = false,
val isCartoon: Boolean = false,
)

data class Data(
val id: Int? = null,
val type: String? = null,
val aniId: String? = null,
val malId: Int? = null,
)

data class Results(
@JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class Media(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("title") val title: String? = null,
@JsonProperty("original_title") val originalTitle: String? = null,
@JsonProperty("media_type") val mediaType: String? = null,
@JsonProperty("poster_path") val posterPath: String? = null,
)

data class Genres(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
)

data class Keywords(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
)

data class KeywordResults(
@JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
@JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
)

data class Seasons(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("season_number") val seasonNumber: Int? = null,
@JsonProperty("air_date") val airDate: String? = null,
)

data class Cast(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("original_name") val originalName: String? = null,
@JsonProperty("character") val character: String? = null,
@JsonProperty("known_for_department") val knownForDepartment: String? = null,
@JsonProperty("profile_path") val profilePath: String? = null,
)

data class Episodes(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("overview") val overview: String? = null,
@JsonProperty("air_date") val airDate: String? = null,
@JsonProperty("still_path") val stillPath: String? = null,
@JsonProperty("vote_average") val voteAverage: Double? = null,
@JsonProperty("episode_number") val episodeNumber: Int? = null,
@JsonProperty("season_number") val seasonNumber: Int? = null,
)

data class MediaDetailEpisodes(
@JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
)

data class Trailers(
@JsonProperty("key") val key: String? = null,
@JsonProperty("type") val type: String? = null,
)

data class ResultsTrailer(
@JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
)

data class AltTitles(
@JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
@JsonProperty("title") val title: String? = null,
@JsonProperty("type") val type: String? = null,
)

data class ResultsAltTitles(
@JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
)

data class ExternalIds(
@JsonProperty("imdb_id") val imdb_id: String? = null,
@JsonProperty("tvdb_id") val tvdb_id: Int? = null,
)

data class Credits(
@JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
)

data class ResultsRecommendations(
@JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class LastEpisodeToAir(
@JsonProperty("episode_number") val episode_number: Int? = null,
@JsonProperty("season_number") val season_number: Int? = null,
)

data class ProductionCountries(
@JsonProperty("name") val name: String? = null,
)

data class MediaDetail(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("imdb_id") val imdbId: String? = null,
@JsonProperty("title") val title: String? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("original_title") val originalTitle: String? = null,
@JsonProperty("original_name") val originalName: String? = null,
@JsonProperty("poster_path") val posterPath: String? = null,
@JsonProperty("backdrop_path") val backdropPath: String? = null,
@JsonProperty("release_date") val releaseDate: String? = null,
@JsonProperty("first_air_date") val firstAirDate: String? = null,
@JsonProperty("overview") val overview: String? = null,
@JsonProperty("runtime") val runtime: Int? = null,
@JsonProperty("vote_average") val vote_average: Any? = null,
@JsonProperty("original_language") val original_language: String? = null,
@JsonProperty("status") val status: String? = null,
@JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
@JsonProperty("keywords") val keywords: KeywordResults? = null,
@JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
@JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
@JsonProperty("videos") val videos: ResultsTrailer? = null,
@JsonProperty("external_ids") val external_ids: ExternalIds? = null,
@JsonProperty("credits") val credits: Credits? = null,
@JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
@JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
@JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf(),
)

}
