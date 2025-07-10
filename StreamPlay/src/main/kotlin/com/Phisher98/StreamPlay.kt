package com.phisher98

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.phisher98.StreamPlayExtractor.invoke2embed
import com.phisher98.StreamPlayExtractor.invokeAllMovieland
import com.phisher98.StreamPlayExtractor.invokeAnimes
import com.phisher98.StreamPlayExtractor.invokeBollyflix
import com.phisher98.StreamPlayExtractor.invokeDahmerMovies
import com.phisher98.StreamPlayExtractor.invokeDotmovies
import com.phisher98.StreamPlayExtractor.invokeEmbedsu
import com.phisher98.StreamPlayExtractor.invokeEmovies
import com.phisher98.StreamPlayExtractor.invokeExtramovies
import com.phisher98.StreamPlayExtractor.invokeFilm1k
import com.phisher98.StreamPlayExtractor.invokeFlixAPIHQ
import com.phisher98.StreamPlayExtractor.invokeFlixon
import com.phisher98.StreamPlayExtractor.invokeKisskh
import com.phisher98.StreamPlayExtractor.invokeLing
import com.phisher98.StreamPlayExtractor.invokeMoflix
import com.phisher98.StreamPlayExtractor.invokeMoviehubAPI
import com.phisher98.StreamPlayExtractor.invokeMoviesdrive
import com.phisher98.StreamPlayExtractor.invokeMoviesmod
import com.phisher98.StreamPlayExtractor.invokeMultiEmbed
import com.phisher98.StreamPlayExtractor.invokeMultimovies
import com.phisher98.StreamPlayExtractor.invokeNepu
import com.phisher98.StreamPlayExtractor.invokeNinetv
import com.phisher98.StreamPlayExtractor.invokePlaydesi
import com.phisher98.StreamPlayExtractor.invokePlayer4U
import com.phisher98.StreamPlayExtractor.invokePrimeWire
import com.phisher98.StreamPlayExtractor.invokeRidomovies
import com.phisher98.StreamPlayExtractor.invokeRiveStream
import com.phisher98.StreamPlayExtractor.invokeRogmovies
import com.phisher98.StreamPlayExtractor.invokeShowflix
import com.phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.phisher98.StreamPlayExtractor.invokeSuperstream
import com.phisher98.StreamPlayExtractor.invokeTom
import com.phisher98.StreamPlayExtractor.invokeTopMovies
import com.phisher98.StreamPlayExtractor.invokeUhdmovies
import com.phisher98.StreamPlayExtractor.invokeVegamovies
import com.phisher98.StreamPlayExtractor.invokeVidSrcViP
import com.phisher98.StreamPlayExtractor.invokeVidSrcXyz
import com.phisher98.StreamPlayExtractor.invokeVidsrccc
import com.phisher98.StreamPlayExtractor.invokeVidsrcsu
import com.phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import com.phisher98.StreamPlayExtractor.invokeZoechip
import com.phisher98.StreamPlayExtractor.invokeZshow
import com.phisher98.StreamPlayExtractor.invokeazseries
import com.phisher98.StreamPlayExtractor.invokecatflix
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.DubStatus
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
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.StreamPlayExtractor.invoke4khdhub
import com.phisher98.StreamPlayExtractor.invokeDramacool
import com.phisher98.StreamPlayExtractor.invokeElevenmovies
import com.phisher98.StreamPlayExtractor.invokeHdmovie2
import com.phisher98.StreamPlayExtractor.invokeDramadrip
import com.phisher98.StreamPlayExtractor.invokeVidfast
import com.phisher98.StreamPlayExtractor.invokeXPrimeAPI
import com.phisher98.StreamPlayExtractor.invokehdhub4u
import com.phisher98.StreamPlayExtractor.invokevidzeeMulti
import com.phisher98.StreamPlayExtractor.invokevidzeeUltra
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

open class StreamPlay(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "StreamPlay"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    private val token = sharedPref?.getString("token", null)
    val wpRedisInterceptor by lazy { CloudflareKiller() }

    /** AUTHOR : hexated & Phisher & Code */
    companion object {
        /** TOOLS */
        private const val remoteURLList =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Proxylist.txt"

        private suspend fun fetchTmdbApiList(): List<String> {
            val officialTmdbUrl = "https://api.themoviedb.org/3"
            val sampleMovieTestUrl = "$officialTmdbUrl/movie/550?api_key=$apiKey"

            return try {
                val response = app.get(sampleMovieTestUrl, timeout = 5000)
                if (response.code == 200) {
                    Log.d("TMDB Check", "✅ Official TMDB API is working.")
                    listOf(officialTmdbUrl)
                } else {
                    Log.d("TMDB Check", "⚠️ Official TMDB API returned ${response.code}, falling back.")
                    fetchFromRemoteList()
                }
            } catch (e: Exception) {
                Log.e("TMDB Check", "❌ Official TMDB API check failed: ${e.message}")
                fetchFromRemoteList()
            }
        }

        private suspend fun fetchFromRemoteList(): List<String> {
            repeat(3) { attempt ->
                try {
                    val response = app.get(remoteURLList, timeout = 5000)
                    val text = response.text
                    val proxyList = text.split("\n")
                        .mapNotNull { it.trim().takeIf { url -> url.isNotEmpty() } }

                    if (proxyList.isNotEmpty()) {
                        Log.d("Proxy Fetch", "✅ Fetched ${proxyList.size} proxy URLs.")
                        return proxyList
                    } else {
                        Log.w("Proxy Fetch", "⚠️ Remote proxy list is empty.")
                    }
                } catch (e: CancellationException) {
                    Log.e("Proxy Fetch", "❌ Coroutine cancelled.")
                    throw e // rethrow so coroutine cancellation is respected
                } catch (e: Exception) {
                    Log.e("Proxy Fetch", "Attempt ${attempt + 1}: Failed to fetch: ${e.message}")
                    delay(1000L * (attempt + 1))
                }
            }

            Log.w("Proxy Fetch", "❌ All attempts failed. Using default TMDB URL.")
            return listOf("https://api.themoviedb.org/3")
        }


        const val gdbot = "https://gdtot.pro"
        const val anilistAPI = "https://graphql.anilist.co"
        const val malsyncAPI = "https://api.malsync.moe"
        const val jikanAPI = "https://api.jikan.moe/v4"
        private const val apiKey = BuildConfig.TMDB_API
        private const val Cinemeta = "https://v3-cinemeta.strem.io"

        /** ALL SOURCES */
        const val twoEmbedAPI = "https://www.2embed.cc"
        const val filmxyAPI = "https://www.filmxy.online"
        const val MOVIE_API = BuildConfig.MOVIE_API
        val hianimeAPIs = listOf(
            "https://hianimez.is",
            "https://hianimez.to",
            "https://hianime.nz",
            "https://hianime.bz",
            "https://hianime.pe"
        )
        const val AnimeKai = "https://animekai.to"
        const val MultiEmbedAPI = "https://multiembed.mov"
        const val kissKhAPI = "https://kisskh.ovh"
        const val lingAPI = "https://ling-online.net"
        const val flixonAPI = "https://flixon.ovh"
        const val azseriesAPI = "https://azseries.org"
        const val PlaydesiAPI = "https://playdesi.net"
        const val watchSomuchAPI = "https://watchsomuch.tv" // sub only
        const val cinemaTvAPI = BuildConfig.CINEMATV_API
        const val Whvx_API = BuildConfig.Whvx_API
        const val nineTvAPI = "https://moviesapi.club"
        const val nowTvAPI = "https://myfilestorage.xyz"
        const val zshowAPI = BuildConfig.ZSHOW_API
        const val ridomoviesAPI = "https://ridomovies.tv"
        const val emoviesAPI = "https://emovies.si"
        const val allmovielandAPI = "https://allmovieland.fun"
        const val vidsrctoAPI = "https://vidsrc.cc"
        const val vidsrcsu = "https://vidsrc.su"
        const val animetoshoAPI = "https://animetosho.org"
        const val showflixAPI = "https://showflix.store"
        const val moflixAPI = "https://moflix-stream.xyz"
        const val zoechipAPI = "https://www1.zoechip.to"
        const val nepuAPI = "https://nepu.to"
        const val dahmerMoviesAPI = "https://a.111477.xyz"
        const val animepaheAPI = "https://animepahe.ru"
        const val Catflix = "https://catflix.su"
        const val NyaaAPI = "https://nyaa.land"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val EmbedSu = "https://embed.su"
        const val WyZIESUBAPI = "https://sub.wyzie.ru"
        const val TomAPI = "https://tom.autoembed.cc"
        const val RiveStreamAPI = "https://rivestream.org"
        const val VidSrcVip = "https://vidsrc.vip"
        const val Primewire = "https://www.primewire.tf"
        const val consumetFlixhqAPI = "https://consumet.8man.me/movies/flixhq"
        const val AnimeOwlAPI = "https://animeowl.me"
        const val Film1kApi = "https://www.film1k.com"
        const val thrirdAPI = BuildConfig.SUPERSTREAM_THIRD_API
        const val fourthAPI = BuildConfig.SUPERSTREAM_FOURTH_API
        const val KickassAPI = "https://kaa.mx"
        const val Player4uApi = "https://player4u.xyz"
        const val Vidsrcxyz = "https://vidsrc.xyz"
        const val Dramacool = "https://stremio-dramacool-addon.xyz"
        const val Xprime = "https://xprime.tv"
        const val Vidzee = "https://vidzee.wtf"
        const val Elevenmovies = "https://111movies.com"
        const val FlixHQ = "https://myflixerz.to"
        const val Vidfast = "https://vidfast.pro"
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

        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        private var cachedDomains: DomainsParser? = null

        suspend fun getDomains(forceRefresh: Boolean = false): DomainsParser? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<DomainsParser>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }
    }

    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$apiKey&region=US" to "Trending",
        "/trending/movie/week?api_key=$apiKey&region=US&with_original_language=en" to "Popular Movies",
        "/trending/tv/week?api_key=$apiKey&region=US&with_original_language=en" to "Popular TV Shows",
        "/tv/airing_today?api_key=$apiKey&region=US&with_original_language=en" to "Airing Today TV Shows",
        "/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix",
        "/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon",
        "/discover/tv?api_key=$apiKey&with_networks=2739" to "Disney+",
        "/discover/tv?api_key=$apiKey&with_networks=453" to "Hulu",
        "/discover/tv?api_key=$apiKey&with_networks=2552" to "Apple TV+",
        "/discover/tv?api_key=$apiKey&with_networks=49" to "HBO",
        "/discover/tv?api_key=$apiKey&with_networks=4330" to "Paramount+",
        "/discover/tv?api_key=$apiKey&with_networks=3353" to "Peacock",
        "/discover/movie?api_key=$apiKey&language=en-US&page=1&sort_by=popularity.desc&with_origin_country=IN&release_date.gte=${getDate().lastWeekStart}&release_date.lte=${getDate().today}" to "Trending Indian Movies",
        "/discover/tv?api_key=$apiKey&with_networks=5920" to "Amazon MiniTV",
        "/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().today}&air_date.gte=${getDate().today}" to "Airing Today Anime",
        "/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().nextWeek}&air_date.gte=${getDate().today}" to "On The Air Anime",
        "/discover/movie?api_key=$apiKey&with_keywords=210024|222243" to "Anime Movies",
        "/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Shows",
        "/discover/tv?api_key=$apiKey&with_genres=99" to "Documentary",
        "/movie/upcoming?api_key=$apiKey&region=US" to "Upcoming Movies",
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
        val tmdbAPI = fetchTmdbApiList().random()

        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("$tmdbAPI${request.data}$adultQuery&page=$page", timeout = 10000)
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
        val tmdbAPI = fetchTmdbApiList().random()
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        val tmdbAPI = fetchTmdbApiList().random()
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,videos,recommendations"

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=$append"
        }

        val res = app.get(resUrl, timeout = 10000).parsedSafe<MediaDetail>()
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
            val name = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(
                Actor(name, getImageUrl(cast.profilePath)), roleString = cast.character
            )
        } ?: emptyList()

        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results.orEmpty()
            .filter { it.type == "Trailer" }
            .map { "https://www.youtube.com/watch?v=${it.key}" }
            .reversed()
            .ifEmpty {
                res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" } ?: emptyList()
            }

        if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            LinkData(
                                data.id,
                                res.external_ids?.imdb_id,
                                res.external_ids?.tvdb_id,
                                data.type,
                                eps.seasonNumber,
                                eps.episodeNumber,
                                eps.id,
                                title = title,
                                year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                                orgTitle = orgTitle,
                                isAnime = isAnime,
                                airedYear = year,
                                lastSeason = lastSeason,
                                epsTitle = eps.name,
                                jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                                date = season.airDate,
                                airedDate = res.releaseDate ?: res.firstAirDate,
                                isAsian = isAsian,
                                isBollywood = isBollywood,
                                isCartoon = isCartoon,
                                alttitle = res.title,
                                nametitle = res.name
                            ).toJson()
                        ) {
                            this.name =
                                eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath)
                            this.rating = eps.voteAverage?.times(10)?.roundToInt()
                            this.description = eps.overview
                        }.apply {
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            if (isAnime) {
                val gson = Gson()
                val animeType = if (data.type?.contains("tv", ignoreCase = true) == true) "series" else "movie"
                val imdbId = res.external_ids?.imdb_id.orEmpty()
                val cineJsonText = app.get("$Cinemeta/meta/$animeType/$imdbId.json").text
                val cinejson = runCatching {
                    gson.fromJson(cineJsonText, CinemetaRes::class.java)
                }.getOrNull()
                val animeepisodes = cinejson?.meta?.videos?.filter { it.season != 0 } ?.map { video ->
                    newEpisode(
                        LinkData(
                            id = data.id,
                            imdbId = res.external_ids?.imdb_id,
                            tvdbId = res.external_ids?.tvdb_id,
                            type = data.type,
                            season = video.season,
                            episode = video.number,
                            epid = null,
                            aniId = null,
                            animeId = null,
                            title = title,
                            year = video.released?.split("-")?.firstOrNull()?.toIntOrNull(),
                            orgTitle = orgTitle,
                            isAnime = true,
                            airedYear = year,
                            lastSeason = null,
                            epsTitle = video.name,
                            jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                            date = video.released,
                            airedDate = res.releaseDate ?: res.firstAirDate,
                            isAsian = isAsian,
                            isBollywood = isBollywood,
                            isCartoon = isCartoon,
                            alttitle = res.title,
                            nametitle = res.name
                        ).toJson()
                    ) {
                        this.name = video.name + if (isUpcoming(video.released)) " • [UPCOMING]" else ""
                        this.season = video.season
                        this.episode = video.number
                        this.posterUrl = video.thumbnail
                        this.rating = video.rating.times(10).roundToInt()
                        this.description = video.description
                    }.apply {
                        this.addDate(video.released)
                    }
                } ?: emptyList()

                return newAnimeLoadResponse(title, url, TvType.Anime) {
                    addEpisodes(DubStatus.Subbed, animeepisodes)
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = keywords?.map { it.replaceFirstChar { it.titlecase() } }
                        ?.takeIf { it.isNotEmpty() } ?: genres
                    this.rating = rating
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
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
                    this.tags = keywords?.map { word -> word.replaceFirstChar { it.titlecase() } }
                        ?.takeIf { it.isNotEmpty() } ?: genres

                    this.rating = rating
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
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
                    airedDate = res.releaseDate ?: res.firstAirDate,
                    isAsian = isAsian,
                    isBollywood = isBollywood,
                    alttitle = res.title,
                    nametitle = res.name
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.comingSoon = isUpcoming(releaseDate)
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords?.map { word -> word.replaceFirstChar { it.titlecase() } }
                    ?.takeIf { it.isNotEmpty() } ?: genres

                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                //this.contentRating = fetchContentRating(data.id, "US") ?: "Not Rated"
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    @SuppressLint("NewApi")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        runAllAsync(
            {
                if (res.isAnime) invokeEmbedsu(res.imdbId, res.season, res.episode, callback)
            },
            {
                if (res.isAnime) invokeAnimes(
                    res.title,
                    res.jpTitle,
                    res.date,
                    res.airedDate,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeVidsrccc(
                    res.id, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeVidsrcsu(
                    res.id, res.season, res.episode, callback
                )
            },
            {
                if (res.isAsian && !res.isAnime) invokeKisskh(
                    res.title, res.season, res.episode, res.lastSeason, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeazseries(
                    res.title, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeLing(
                    res.title,
                    res.airedYear ?: res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isBollywood && !res.isAnime) invokeUhdmovies(
                    res.title, res.year, res.season, res.episode, callback, subtitleCallback
                )
            },
            {
                if (!res.isAnime) invokeTopMovies(
                    res.imdbId,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime && !res.isBollywood) invokeMoviesmod(
                    res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeBollyflix(
                    res.imdbId, res.season, res.episode,subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeFlixon(
                    res.id, res.imdbId, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokeWatchsomuch(
                    res.imdbId, res.season, res.episode, subtitleCallback
                )
            },
            {
                if (!res.isAnime) invokeNinetv(
                    res.id, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                invokeDahmerMovies(
                    dahmerMoviesAPI, res.title, res.year, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokeRidomovies(
                    res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeMoviehubAPI(
                    res.id, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeAllMovieland(
                    res.imdbId, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokeMultiEmbed(
                    res.imdbId, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokecatflix(
                    res.id, res.epid, res.title, res.episode, res.season, callback
                )
            },
            {
                if (!res.isAnime) invokeEmovies(
                    res.title, res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime && !res.isBollywood) invokeVegamovies(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    res.imdbId,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeExtramovies(
                    res.imdbId, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime && res.isBollywood) invokeDotmovies(
                    res.imdbId,
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime && res.isBollywood) invokeRogmovies(
                    res.imdbId,
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeMultimovies(
                    res.title, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invoke2embed(
                    res.imdbId, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAsian && !res.isBollywood && !res.isAnime) invokeZshow(
                    res.title, res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeShowflix(
                    res.title, res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeMoflix(res.id, res.season, res.episode, callback)
            },
            {
                if (!res.isAnime) invokeTom(
                    res.id, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeZoechip(
                    res.title, res.year, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokeNepu(
                    res.title, res.airedYear ?: res.year, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokePlaydesi(
                    res.title, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeMoviesdrive(
                    res.title,
                    res.season,
                    res.episode,
                    res.year,
                    res.imdbId,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeFlixAPIHQ(
                    res.title, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (res.isAsian && res.season != null) invokeDramacool(
                    res.title, "kdhd", res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                invokeRiveStream(
                    res.id, res.season, res.episode, callback
                )

            },

            {
                if (!res.isAnime) invokeVidSrcViP(
                    res.id, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokePrimeWire(
                    res.id,
                    res.imdbId,
                    res.title,
                    res.season,
                    res.episode,
                    res.year,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime && !res.isBollywood && !res.isCartoon) invokeFilm1k(
                    res.id,
                    res.imdbId,
                    res.title,
                    res.season,
                    res.episode,
                    res.year,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeSuperstream(
                    token, res.imdbId, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokePlayer4U(
                    res.title, res.season, res.episode, res.year, callback
                )
            },
            {
                if (!res.isAnime) invokeVidSrcXyz(
                    res.imdbId, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokeXPrimeAPI(
                    res.title,
                    res.year,
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokevidzeeUltra(
                    res.id, res.season, res.episode, callback
                )
            },
            {
                if (!res.isAnime) invokevidzeeMulti(
                    res.id, res.season, res.episode, callback
                )
            },
            {
                if (!res.isBollywood || !res.isAnime) invoke4khdhub(
                    res.title, res.year, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokeElevenmovies(
                    res.id, res.season, res.episode, subtitleCallback, callback
                )
            },
            {
                if (!res.isAnime) invokehdhub4u(
                    res.imdbId,
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeHdmovie2(res.title,res.year,res.season, res.episode, subtitleCallback, callback)
            },
            {
                if (!res.isAnime) invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback)
            },
            {
                if (!res.isAnime) invokeVidfast(res.imdbId, res.season, res.episode, callback)
            },

            //Subtitles Invokes
            {
                invokeSubtitleAPI(
                    res.imdbId, res.season, res.episode, subtitleCallback
                )
            },
            {
                invokeWyZIESUBAPI(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                )
            },
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
        val epid: Int? = null,
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
        val alttitle: String? = null,
        val nametitle: String? = null
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
