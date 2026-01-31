package com.phisher98

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addKitsuId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
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
import kotlinx.coroutines.sync.withLock
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlinx.coroutines.*
open class StreamPlay(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "StreamPlay"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    val token: String? = sharedPref?.getString("token", null)
    val langCode = sharedPref?.getString("tmdb_language_code", "en-US")

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    /** AUTHOR : hexated & Phisher & Code */
    companion object {
        /** TOOLS */
        private const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
        private const val Cinemeta = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb"
        private const val REMOTE_PROXY_LIST = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Proxylist.txt"
        private const val apiKey = BuildConfig.TMDB_API
        private var currentBaseUrl: String? = null


        private val apiMutex = Mutex() // Prevents race conditions
        private const val TAG = "StreamPlay"

        suspend fun getApiBase(): String {
            currentBaseUrl?.let { return it }
            return apiMutex.withLock {
                currentBaseUrl?.let { return it }

                // 1. Try Official Fast
                if (checkConnectivity(OFFICIAL_TMDB_URL)) {
                    Log.d(TAG, "✅ Using official TMDB API")
                    currentBaseUrl = OFFICIAL_TMDB_URL
                    return OFFICIAL_TMDB_URL
                }

                // 2. Fetch Proxies
                val proxies = fetchProxyList()
                if (proxies.isEmpty()) {
                    Log.e(TAG, "❌ No proxies found, falling back to official")
                    return OFFICIAL_TMDB_URL
                }

                // 3. Parallel Race: Check all proxies at the same time
                val workingProxy = coroutineScope {
                    val deferredChecks = proxies.map { proxy ->
                        async {
                            if (checkConnectivity(proxy)) proxy else null
                        }
                    }
                    deferredChecks.awaitAll().firstOrNull { it != null }
                }

                if (workingProxy != null) {
                    Log.d(TAG, "✅ Switched to proxy: $workingProxy")
                    currentBaseUrl = workingProxy
                    return workingProxy
                }

                // 4. Ultimate Fallback
                Log.e(TAG, "❌ All proxies failed, fallback to official")
                currentBaseUrl = OFFICIAL_TMDB_URL
                OFFICIAL_TMDB_URL
            }
        }

        private suspend fun checkConnectivity(url: String): Boolean {
            val testUrl = "$url/configuration?api_key=$apiKey"

            return withTimeoutOrNull(2000) { // 2s timeout max
                try {
                    val response = app.get(
                        testUrl,
                        timeout = 1500, // Fast socket timeout
                        headers = mapOf("Cache-Control" to "no-cache")
                    )
                    response.code == 200 || response.code == 304
                } catch (_: Exception) {
                    false
                }
            } ?: false
        }

        private suspend fun fetchProxyList(): List<String> = try {
            val response = app.get(REMOTE_PROXY_LIST, timeout = 5000).text
            val json = JSONObject(response)
            val arr = json.getJSONArray("proxies")

            // Convert to list and clean strings
            (0 until arr.length()).map { arr.getString(it).trim().removeSuffix("/") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching proxy list: ${e.message}")
            emptyList()
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
                }
            }
            return cachedDomains
        }

        const val anilistAPI = "https://graphql.anilist.co"
        const val malsyncAPI = "https://api.malsync.moe"
        const val jikanAPI = "https://api.jikan.moe/v4"

        /** ALL SOURCES */
        const val twoEmbedAPI = "https://www.2embed.cc"
        const val MOVIE_API = BuildConfig.MOVIE_API
        val hianimeAPIs = listOf(
            "https://hianimez.is",
            "https://hianime.to",
            "https://hianime.nz",
            "https://hianime.bz",
            "https://hianime.pe"
        )
        val animekaiAPIs = listOf(
            "https://animekai.im",
            "https://animekai.in",
            "https://animekai.la",
            "https://animekai.nl",
            "https://animekai.vc",
            "https://anikai.to"
        )
        const val MultiEmbedAPI = "https://multiembed.mov"
        const val kissKhAPI = "https://kisskh.ovh"
        const val PlaydesiAPI = "https://playdesi.info"
        const val watchSomuchAPI = "https://watchsomuch.tv" // sub only
        const val nineTvAPI = "https://moviesapi.club"
        const val zshowAPI = BuildConfig.ZSHOW_API
        const val ridomoviesAPI = "https://ridomovies.tv"
        const val allmovielandAPI = "https://allmovieland.io"
        const val vidsrctoAPI = "https://vidsrc.cc"
        const val animetoshoAPI = "https://animetosho.org"
        const val showflixAPI = "https://showflix.store"
        const val moflixAPI = "https://moflix-stream.xyz"
        const val zoechipAPI = "https://www1.zoechip.to"
        const val nepuAPI = "https://nepu.to"
        const val dahmerMoviesAPI = "https://a.111477.xyz"
        const val animepaheAPI = "https://animepahe.si"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val WyZIESUBAPI = "https://sub.wyzie.ru"
        const val RiveStreamAPI = "https://rivestream.org"
        const val PrimeSrcApi = "https://primesrc.me"
        const val Film1kApi = "https://www.film1k.com"
        const val thrirdAPI = BuildConfig.SUPERSTREAM_THIRD_API
        const val fourthAPI = BuildConfig.SUPERSTREAM_FOURTH_API
        const val KickassAPI = "https://kaa.to"
        const val Vidsrcxyz = "https://vidsrc-embed.su"
        const val Elevenmovies = "https://111movies.com"
        const val Watch32 = "https://watch32.sx"
        const val movieBox= "https://api.inmoviebox.com"
        const val vidrock = "https://vidrock.net"
        const val soapy = "https://soapy.to"
        const val vidlink = "https://vidlink.pro"
        const val cinemaOSApi = "https://cinemaos.tech"
        const val mappleTvApi = "https://mapple.uk"
        const val mp4hydra = "https://mp4hydra.org"
        const val vidfastProApi = "https://vidfast.pro"
        const val vidPlusApi = "https://player.vidplus.to"
        const val Videasy = "https://api.videasy.net"
        const val XDmoviesAPI = "https://new.xdmovies.wtf"
        const val kimcartoonAPI = "https://kimcartoon.si"
        const val yFlix = "https://yflix.to"
        const val moviesClubApi = "https://moviesapi.club"
        const val cinemacity = "https://cinemacity.cc"
        const val embedmaster = "https://embedmaster.link"
        const val hexaSU = "https://themoviedb.hexa.su"
        const val bidSrc = "https://bidsrc.pro"
        const val flixindia = "https://m.flixindia.xyz"
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
        "/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().today}&air_date.gte=${getDate().today}" to "Airing Today Anime",
        "/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().nextWeek}&air_date.gte=${getDate().today}" to "On The Air Anime",
        "/discover/movie?api_key=$apiKey&with_keywords=210024|222243" to "Anime Movies",
        "/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Shows",
        "/discover/tv?api_key=$apiKey&with_genres=99" to "Documentary",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tmdbAPI =getApiBase()
        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("$tmdbAPI${request.data}$adultQuery&language=$langCode&page=$page", timeout = 10000)
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
            this.score= Score.from10(voteAverage)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query,1)?.items

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val tmdbAPI = getApiBase()
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=$langCode&query=$query&page=$page&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }?.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val tmdbAPI = getApiBase()
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,videos,recommendations"

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&language=$langCode&append_to_response=$append"
        }

        val enResUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&language=en-US"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&language=en-US"
        }

        val enRes = app.get(enResUrl).parsedSafe<MediaDetail>()
        val enTitle = enRes?.title ?: enRes?.name

        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")
        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
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
        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = "https://api.themoviedb.org/3",
            apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
            type = type,
            tmdbId = res.id,
            appLangCode = langCode ?: "en"
        )
        val cinetype = if (type == TvType.TvSeries) "series" else "movie"
        val cineRes = app.get("$Cinemeta/meta/$cinetype/${res.external_ids?.imdb_id}.json").parsedSafe<CinemetaRes>()

        if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = coroutineScope {
                res.seasons?.map { season ->
                    async {
                        app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey&language=$langCode")
                            .parsedSafe<MediaDetailEpisodes>()
                            ?.episodes
                            ?.map { eps ->
                                newEpisode(
                                    LinkData(
                                        data.id,
                                        res.external_ids?.imdb_id,
                                        res.external_ids?.tvdb_id,
                                        data.type,
                                        eps.seasonNumber,
                                        eps.episodeNumber,
                                        eps.id,
                                        title = enTitle,
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
                                    this.name = eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                                    this.season = eps.seasonNumber
                                    this.episode = eps.episodeNumber
                                    this.posterUrl = getImageUrl(eps.stillPath)
                                    this.score = Score.from10(eps.voteAverage)
                                    this.description = eps.overview
                                    this.runTime = eps.runTime
                                }.apply {
                                    this.addDate(eps.airDate)
                                }
                            }
                    }
                }?.awaitAll()?.filterNotNull()?.flatten() ?: listOf()
            }
            if (isAnime) {
                val animeType = if (data.type?.contains("tv", ignoreCase = true) == true) "series" else "movie"
                val imdbId = res.external_ids?.imdb_id.orEmpty()
                val cineRes = app.get("$Cinemeta/meta/$animeType/$imdbId.json").parsedSafe<CinemetaRes>()
                val animeVideos = cineRes?.meta?.videos?.filter { it.season != 0 } ?: emptyList()
                val jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title
                    ?: cineRes?.meta?.name
                val syncMetaData = app.get("https://api.ani.zip/mappings?imdb_id=$imdbId").toString()
                val animeMetaData = parseAnimeData(syncMetaData)
                val kitsuid = animeMetaData?.mappings?.kitsuid
                fun buildEpisodeList(isDub: Boolean) = animeVideos.map { video ->
                    val videoYear = video.released?.split("-")?.firstOrNull()?.toIntOrNull()
                        ?: cineRes?.meta?.year?.toIntOrNull() ?: 0

                    newEpisode(
                        LinkData(
                            id = data.id,
                            imdbId = imdbId,
                            tvdbId = res.external_ids?.tvdb_id,
                            type = data.type,
                            season = video.season,
                            episode = video.episode,
                            title = title,
                            year = videoYear,
                            orgTitle = orgTitle,
                            isAnime = true,
                            airedYear = year,
                            epsTitle = video.title,
                            jpTitle = jpTitle,
                            date = video.released,
                            airedDate = res.releaseDate ?: res.firstAirDate,
                            isAsian = isAsian,
                            isBollywood = isBollywood,
                            isCartoon = isCartoon,
                            alttitle = res.title,
                            nametitle = res.name,
                            isDub = isDub
                        ).toJson()
                    ) {
                        this.name = video.title + if (isUpcoming(video.released)) " • [UPCOMING]" else ""
                        this.season = video.season
                        this.episode = video.episode
                        this.posterUrl = video.thumbnail
                        this.description = video.overview
                        addDate(video.released)
                    }
                }

                return newAnimeLoadResponse(title, url, TvType.Anime) {
                    addEpisodes(DubStatus.Subbed, buildEpisodeList(isDub = false))
                    addEpisodes(DubStatus.Dubbed, buildEpisodeList(isDub = true))
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    try { this.logoUrl = logoUrl } catch(_:Throwable){}
                    this.year = year
                    this.plot = res.overview
                    this.tags = keywords?.map { it.replaceFirstChar { c -> c.titlecase() } }
                        ?.takeIf { it.isNotEmpty() } ?: genres
                    this.score = Score.from10(res.vote_average.toString())
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    addTrailer(trailer)
                    try { addKitsuId(kitsuid) } catch(_:Throwable){}
                    this.contentRating = cineRes?.meta?.appExtras?.certification
                    addImdbId(imdbId)
                }
            } else {
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = keywords?.map { word -> word.replaceFirstChar { it.titlecase() } }
                        ?.takeIf { it.isNotEmpty() } ?: genres
                    this.score = Score.from10(res.vote_average.toString())
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    try { this.logoUrl = logoUrl } catch(_:Throwable){}
                    this.contentRating = cineRes?.meta?.appExtras?.certification
                    addTrailer(trailer)
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
                    title = enTitle,
                    year = year,
                    orgTitle = orgTitle,
                    isAnime = isAnime,
                    jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                    airedDate = res.releaseDate ?: res.firstAirDate,
                    isAsian = isAsian,
                    isBollywood = isBollywood,
                    alttitle = res.title,
                    nametitle = res.name,
                    isMovie = true
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
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.score = Score.from10(res.vote_average.toString())
                this.recommendations = recommendations
                this.actors = actors
                this.contentRating = cineRes?.meta?.appExtras?.certification
                addTrailer(trailer)
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        val disabledProviderIds = sharedPref
            ?.getStringSet("disabled_providers", emptySet())
            ?.toSet() ?: emptySet()
        val providersList = buildProviders().filter { it.id !in disabledProviderIds }
        val authToken = token
        runLimitedAsync( concurrency = 10,
            {
                try {
                    if (!res.isAnime) {
                        invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback)
                    }
                } catch (_: Throwable) {
                    // ignore failure but do not cancel the rest
                }
            },
            {
                try {
                    if (!res.isAnime) {
                        invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback)
                    }
                } catch (_: Throwable) {
                    // ignore failure
                }
            },
            *providersList.map { provider ->
                suspend {
                    try {
                        provider.invoke(
                            res,
                            subtitleCallback,
                            callback,
                            authToken ?: "",
                            dahmerMoviesAPI
                        )
                    } catch (_: Throwable) {
                        // provider failure shouldn't kill others
                    }
                }
            }.toTypedArray()
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
        val nametitle: String? = null,
        val isDub: Boolean = false,
        val isMovie: Boolean? = false,
    )

    data class Data(
        val id: Int? = null,
        val type: String? = null,
        val aniId: String? = null,
        val malId: Int? = null,
    )

    data class Results(
        @param:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @param:JsonProperty("id") val id: Int? = null,
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("title") val title: String? = null,
        @param:JsonProperty("original_title") val originalTitle: String? = null,
        @param:JsonProperty("media_type") val mediaType: String? = null,
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("vote_average") val voteAverage: Double? = null,
    )

    data class Genres(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
    )

    data class Keywords(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
    )

    data class KeywordResults(
        @get:JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
        @get:JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
    )

    data class Seasons(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("season_number") val seasonNumber: Int? = null,
        @get:JsonProperty("air_date") val airDate: String? = null,
    )

    data class Cast(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("original_name") val originalName: String? = null,
        @get:JsonProperty("character") val character: String? = null,
        @get:JsonProperty("known_for_department") val knownForDepartment: String? = null,
        @get:JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Episodes(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("overview") val overview: String? = null,
        @get:JsonProperty("air_date") val airDate: String? = null,
        @get:JsonProperty("still_path") val stillPath: String? = null,
        @get:JsonProperty("vote_average") val voteAverage: Double? = null,
        @get:JsonProperty("episode_number") val episodeNumber: Int? = null,
        @get:JsonProperty("season_number") val seasonNumber: Int? = null,
        @get:JsonProperty("runtime") val runTime: Int? = null
    )

    data class MediaDetailEpisodes(
        @get:JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Trailers(
        @get:JsonProperty("key") val key: String? = null,
        @get:JsonProperty("type") val type: String? = null,
    )

    data class ResultsTrailer(
        @get:JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class AltTitles(
        @get:JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
        @get:JsonProperty("title") val title: String? = null,
        @get:JsonProperty("type") val type: String? = null,
    )

    data class ResultsAltTitles(
        @get:JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
    )

    data class ExternalIds(
        @get:JsonProperty("imdb_id") val imdb_id: String? = null,
        @get:JsonProperty("tvdb_id") val tvdb_id: Int? = null,
    )

    data class Credits(
        @get:JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class ResultsRecommendations(
        @get:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class LastEpisodeToAir(
        @get:JsonProperty("episode_number") val episode_number: Int? = null,
        @get:JsonProperty("season_number") val season_number: Int? = null,
    )

    data class ProductionCountries(
        @get:JsonProperty("name") val name: String? = null,
    )

    data class MediaDetail(
        @get:JsonProperty("id") val id: Int? = null,
        @get:JsonProperty("imdb_id") val imdbId: String? = null,
        @get:JsonProperty("title") val title: String? = null,
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("original_title") val originalTitle: String? = null,
        @get:JsonProperty("original_name") val originalName: String? = null,
        @get:JsonProperty("poster_path") val posterPath: String? = null,
        @get:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @get:JsonProperty("release_date") val releaseDate: String? = null,
        @get:JsonProperty("first_air_date") val firstAirDate: String? = null,
        @get:JsonProperty("overview") val overview: String? = null,
        @get:JsonProperty("runtime") val runtime: Int? = null,
        @get:JsonProperty("vote_average") val vote_average: Any? = null,
        @get:JsonProperty("original_language") val original_language: String? = null,
        @get:JsonProperty("status") val status: String? = null,
        @get:JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @get:JsonProperty("keywords") val keywords: KeywordResults? = null,
        @get:JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
        @get:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @get:JsonProperty("videos") val videos: ResultsTrailer? = null,
        @get:JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @get:JsonProperty("credits") val credits: Credits? = null,
        @get:JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
        @get:JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
        @get:JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf(),
    )
}
