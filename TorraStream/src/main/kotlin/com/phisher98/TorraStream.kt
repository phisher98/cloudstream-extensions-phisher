package com.phisher98

import android.content.SharedPreferences
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

class TorraStream(private val sharedPref: SharedPreferences) : TraktProvider() {
    override var name = "TorraStream"
    override var mainUrl = "https://torrentio.strem.fun"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object {
        const val OnethreethreesevenxAPI = "https://proxy.phisher2.workers.dev/?url=https://1337x.to"
        const val MediafusionApi = "https://mediafusion.elfhosted.com/D-_ru4-xVDOkpYNgdQZ-gA6whxWtMNeLLsnAyhb82mkks4eJf4QTlrAksSeBnwFAbIGWQLaokCGFxxsHupxSVxZO8xhhB2UYnyc5nnLeDnIqiLajtkmaGJMB_ZHqMqSYIU2wcGhrw0s4hlXeRAfnnbDywHCW8DLF_ZZfOXYUGPzWS-91cvu7kA2xPs0lJtcqZO"
        const val ThePirateBayApi = "https://thepiratebay-plus.strem.fun"
        const val AIOStreams = "https://aiostreams.elfhosted.com/E2-xLzptGhmwLnA9L%2FOUHyZJg%3D%3D-Io2cJBStOrbqlmGwGz2ZwBbMGBj5enyJFgN5XcslkuiUS5KSjJrv90yd4HHLj1fyq6hJm7QpnCxDiPqbeOwdGA2yySllUQh2T%2B5qPqgtPt2sWBN5zdeetbiFFLHvVqq0PZOhKGM7pv2LzCoMLAk%2BSo86mcrzWIeszmvHuRMoKX3zBO6hUDvH6oqK2hFfbUF7ZONMdm9jE7lHp0LuXKPzHSwKUvDZroJ9iRgBkvHIGjJL65oBv2PxfQK%2Fu4gYEuLVhH3dQ7Xu6i1AshdxycCPRQOO2LcDDZkBC84zLXoy3DDPkvDkWBv2icVZIs2dnQlwvtfu7fFiXaGxWJxtYvbBALIhey8SaaeCKts8xMEyuJvSZiKBbkiTblb0NbqfRyGoJz5rJkiCPzlnX6S%2BpNHKNXVYRj2QZmmvN47fdteAZfhvCuNRW1XBP%2FhTr5ufzCQ9tC8ao%2F4ZhoVXPje45mgPpeJy%2FqYGkX36%2BDgjUMGM1SIvm416pHFL1fVG9MQlIdTn2T4VaUHA0dZHXxznaSQDB%2F1GIkDCHOp2iWUl8zceINOE08AI%2BUwmWCnVXsvsXYaTbFnsE%2F0n1zQwN19ULRCnO4AN2KKLfWKHCz9q5YwQG6y9r%2BXTkjtAXoju764x1f2UlFZT8aavjX1oAcPiTC5vA%3D%3D"
        const val PeerflixApi = "https://peerflix.mov"
        const val CometAPI = "https://comet.elfhosted.com"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val AnimetoshoAPI = "https://feed.animetosho.org"
        const val TorrentioAnimeAPI = "https://torrentio.strem.fun/providers=nyaasi,tokyotosho,anidex%7Csort=seeders"
        const val TorboxAPI= "https://stremio.torbox.app"
        val TRACKER_LIST_URL = listOf(
        "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best.txt",
        "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best_ip.txt",
        )
        private const val simkl = "https://api.simkl.com"
        private const val Uindex = "https://uindex.org"
        private const val Knaben = "https://knaben.org"
    }

    private val traktApiUrl = "https://api.trakt.tv"
    private val traktClientId = "d9f434f48b55683a279ffe88ddc68351cc04c9dc9372bd95af5de780b794e770"
    override val mainPage =
        mainPageOf(
            "$traktApiUrl/movies/trending?extended=full,images&limit=25" to "Trending Movies",
            "$traktApiUrl/movies/popular?extended=full,images&limit=25" to "Popular Movies",
            "$traktApiUrl/shows/trending?extended=full,images&limit=25" to "Trending Shows",
            "$traktApiUrl/shows/popular?extended=full,images&limit=25" to "Popular Shows",
        )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val apiResponse = getApi(request.data)
        val results = parseJson<List<MediaDetails>>(apiResponse).map { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val apiResponse =
            getApi("$traktApiUrl/search/movie,show?extended=full,images&limit=20&page=$page&query=$query")

        return newSearchResponseList(parseJson<List<MediaDetails>>(apiResponse).map { element ->
            element.toSearchResponse()
        })
    }

    private fun MediaDetails.toSearchResponse(): SearchResponse {

        val media = this.media ?: this
        val mediaType = if (media.airedEpisodes !== null) TvType.TvSeries else TvType.Movie
        val poster = media.images?.poster?.firstOrNull()
        return if (mediaType == TvType.Movie) {
            newMovieSearchResponse(
                name = media.title ?: "",
                url = Data(
                    type = TvType.Movie,
                    mediaDetails = media,
                ).toJson(),
                type = TvType.Movie,
            ) {
                score = Score.from10(media.rating)
                posterUrl = fixPath(poster)
            }
        } else {
            newTvSeriesSearchResponse(
                name = media.title ?: "",
                url = Data(
                    type = TvType.TvSeries,
                    mediaDetails = media,
                ).toJson(),
                type = TvType.TvSeries,
            ) {
                score = Score.from10(media.rating)
                this.posterUrl = fixPath(poster)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<Data>(url)
        val mediaDetails = data.mediaDetails

        val moviesOrShows = if (data.type == TvType.Movie) "movies" else "shows"

        val posterUrl = fixPath(mediaDetails?.images?.poster?.firstOrNull())
        val backDropUrl = fixPath(mediaDetails?.images?.fanart?.firstOrNull())

        val resActor =
            getApi("$traktApiUrl/$moviesOrShows/${mediaDetails?.ids?.trakt}/people?extended=full,images")

        val actors = parseJson<People>(resActor).cast?.map {
            ActorData(
                Actor(
                    name = it.person?.name!!,
                    image = fixPath(it.person!!.images?.headshot?.firstOrNull())
                ),
                roleString = it.character
            )
        }

        val simklid = runCatching {
            mediaDetails?.ids?.imdb?.takeIf { it.isNotBlank() }?.let { imdb ->
                val path = if (data.type == TvType.Movie) "movies" else "tv"
                val resJson = JSONObject(app.get("$simkl/$path/$imdb?client_id=${com.lagradost.cloudstream3.BuildConfig.SIMKL_CLIENT_ID}").text)
                resJson.optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
            }
        }.getOrNull()

        val resRelated =
            getApi("$traktApiUrl/$moviesOrShows/${mediaDetails?.ids?.trakt}/related?extended=full,images&limit=20")

        val relatedMedia = parseJson<List<MediaDetails>>(resRelated).map { it.toSearchResponse() }

        val isCartoon =
            mediaDetails?.genres?.contains("animation") == true || mediaDetails?.genres?.contains("anime") == true
        val isAnime =
            isCartoon && (mediaDetails.language == "zh" || mediaDetails.language == "ja")
        val isAsian = !isAnime && (mediaDetails?.language == "zh" || mediaDetails?.language == "ko")
        val isBollywood = mediaDetails?.country == "in"
        val uniqueUrl = data.mediaDetails?.ids?.trakt?.toJson() ?: data.toJson()

        if (data.type == TvType.Movie) {

            val linkData = LinkData(
                id = mediaDetails?.ids?.tmdb,
                traktId = mediaDetails?.ids?.trakt,
                traktSlug = mediaDetails?.ids?.slug,
                tmdbId = mediaDetails?.ids?.tmdb,
                imdbId = mediaDetails?.ids?.imdb.toString(),
                tvdbId = mediaDetails?.ids?.tvdb,
                tvrageId = mediaDetails?.ids?.tvrage,
                type = data.type.toString(),
                title = mediaDetails?.title,
                year = mediaDetails?.year,
                orgTitle = mediaDetails?.title,
                isAnime = isAnime,
                //jpTitle = later if needed as it requires another network request,
                airedDate = mediaDetails?.released
                    ?: mediaDetails?.firstAired,
                isAsian = isAsian,
                isBollywood = isBollywood,
            ).toJson()

            return newMovieLoadResponse(
                name = mediaDetails?.title!!,
                url = data.toJson(),
                dataUrl = linkData.toJson(),
                type = if (isAnime) TvType.AnimeMovie else TvType.Movie,
            ) {
                this.uniqueUrl = uniqueUrl
                this.name = mediaDetails.title.toString()
                this.type = if (isAnime) TvType.AnimeMovie else TvType.Movie
                this.posterUrl = posterUrl
                this.year = mediaDetails.year
                this.plot = mediaDetails.overview
                this.score = Score.from10(mediaDetails.rating)
                this.tags = mediaDetails.genres
                this.duration = mediaDetails.runtime
                this.recommendations = relatedMedia
                this.actors = actors
                this.comingSoon = isUpcoming(mediaDetails.released)
                //posterHeaders
                this.backgroundPosterUrl = backDropUrl
                this.contentRating = mediaDetails.certification
                addTrailer(mediaDetails.trailer)
                addImdbId(mediaDetails.ids?.imdb)
                addTMDbId(mediaDetails.ids?.tmdb.toString())
                addSimklId(simklid)
            }
        } else {

            val resSeasons =
                getApi("$traktApiUrl/shows/${mediaDetails?.ids?.trakt.toString()}/seasons?extended=full,images,episodes")
            val episodes = mutableListOf<Episode>()
            val seasons = parseJson<List<Seasons>>(resSeasons)
            var nextAir: NextAiring? = null

            seasons.forEach { season ->

                season.episodes?.map { episode ->

                    val linkData = LinkData(
                        id = mediaDetails?.ids?.tmdb,
                        traktId = mediaDetails?.ids?.trakt,
                        traktSlug = mediaDetails?.ids?.slug,
                        tmdbId = mediaDetails?.ids?.tmdb,
                        imdbId = mediaDetails?.ids?.imdb.toString(),
                        tvdbId = mediaDetails?.ids?.tvdb,
                        tvrageId = mediaDetails?.ids?.tvrage,
                        type = data.type.toString(),
                        season = episode.season,
                        episode = episode.number,
                        title = mediaDetails?.title,
                        year = mediaDetails?.year,
                        orgTitle = mediaDetails?.title,
                        isAnime = isAnime,
                        airedYear = mediaDetails?.year,
                        lastSeason = seasons.size,
                        epsTitle = episode.title,
                        //jpTitle = later if needed as it requires another network request,
                        date = episode.firstAired,
                        airedDate = episode.firstAired,
                        isAsian = isAsian,
                        isBollywood = isBollywood,
                        isCartoon = isCartoon
                    ).toJson()

                    episodes.add(
                        newEpisode(linkData.toJson()) {
                            this.name = episode.title
                            this.season = episode.season
                            this.episode = episode.number
                            this.description = episode.overview
                            this.runTime = episode.runtime
                            this.posterUrl = fixPath( episode.images?.screenshot?.firstOrNull())
                            //this.rating = episode.rating?.times(10)?.roundToInt()
                            this.score = Score.from10(episode.rating)

                            this.addDate(episode.firstAired, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                            if (nextAir == null && this.date != null && this.date!! > unixTimeMS && this.season != 0) {
                                nextAir = NextAiring(
                                    episode = this.episode!!,
                                    unixTime = this.date!!.div(1000L),
                                    season = if (this.season == 1) null else this.season,
                                )
                            }
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(
                name = mediaDetails?.title!!,
                url = data.toJson(),
                type = if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes = episodes
            ) {
                this.uniqueUrl = uniqueUrl
                this.name = mediaDetails.title.toString()
                this.type = if (isAnime) TvType.Anime else TvType.TvSeries
                this.episodes = episodes
                this.posterUrl = posterUrl
                this.year = mediaDetails.year
                this.plot = mediaDetails.overview
                this.showStatus = getStatus(mediaDetails.status)
                this.score = Score.from10(mediaDetails.rating)
                this.tags = mediaDetails.genres
                this.duration = mediaDetails.runtime
                this.recommendations = relatedMedia
                this.actors = actors
                this.comingSoon = isUpcoming(mediaDetails.released)
                //posterHeaders
                this.nextAiring = nextAir
                this.backgroundPosterUrl = backDropUrl
                this.contentRating = mediaDetails.certification
                addTrailer(mediaDetails.trailer)
                addImdbId(mediaDetails.ids?.imdb)
                addTMDbId(mediaDetails.ids?.tmdb.toString())
                addSimklId(simklid)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val provider = sharedPref.getString("debrid_provider", null)
        val key = sharedPref.getString("debrid_key", null)
        val dataObj = parseJson<LinkData>(data)
        val isAnime = dataObj.isAnime
        val title = dataObj.title
        val season = dataObj.season
        val episode = dataObj.episode
        val id = dataObj.imdbId
        val year = dataObj.year
        val anijson = app.get("https://api.ani.zip/mappings?imdb_id=$id").toString()
        val anidbEid = getAnidbEid(anijson, episode) ?: 0

        val apiUrl = buildApiUrl(sharedPref, mainUrl)
        if (provider == "AIO Streams" && !key.isNullOrEmpty()) {
            runAllAsync(
                suspend { invokeAIOStreamsDebian(key, id, season, episode, callback) }
            )
        }

        if (provider == "TorBox" && !key.isNullOrEmpty()) {
            runAllAsync(
                suspend { invokeDebianTorbox(TorboxAPI, key, id, season, episode, callback) }
            )
        }

        if (!key.isNullOrEmpty()) {
            runAllAsync(
                suspend { invokeTorrentioDebian(apiUrl, id, season, episode, callback) }
            )
        } else {
            runAllAsync(
                suspend { invokeTorrentio(apiUrl, id, season, episode, callback) },
                //suspend { invoke1337x(OnethreethreesevenxAPI, title, year, callback) },
                //suspend { invokeMediaFusion(MediafusionApi, id, season, episode, callback) },
                suspend { invokeThepiratebay(ThePirateBayApi, id, season, episode, callback) },
                //suspend { invokePeerFlix(PeerflixApi, id, season, episode, callback) },
                //suspend { invokeComet(CometAPI, id, season, episode, callback) },
                suspend { if (dataObj.isAnime) invokeAnimetosho(anidbEid, callback) },
                suspend { if (dataObj.isAnime) invokeTorrentioAnime(TorrentioAnimeAPI, id, season, episode, callback) },
                //suspend { invokeAIOStreams(AIOStreams, id, season, episode, callback) },
                suspend { invokeUindex(Uindex,title,year, season, episode,callback) },
                suspend { invokeKnaben(Knaben,isAnime,title,year, season, episode,callback) },
                suspend { invokeSubtitleAPI(id, season, episode, subtitleCallback) }
            )
        }


        // Subtitles
        val subApiUrl = "https://opensubtitles-v3.strem.io"
        val url = if (season == null) "$subApiUrl/subtitles/movie/$id.json"
        else "$subApiUrl/subtitles/series/$id:$season:$episode.json"

        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )

        app.get(url, headers = headers, timeout = 100L)
            .parsedSafe<Subtitles>()?.subtitles?.amap {
                val lan = getLanguage(it.lang) ?: it.lang
                subtitleCallback(
                    newSubtitleFile(
                        lan,
                        it.url
                    )
                )
            }

        return true
    }

    private suspend fun getApi(url: String): String {
        return app.get(
            url = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "trakt-api-version" to "2",
                "trakt-api-key" to traktClientId,
            )
        ).toString()
    }


    private fun getStatus(t: String?): ShowStatus {
        return when (t) {
            "returning series" -> ShowStatus.Ongoing
            "continuing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }


    private fun isUpcoming(dateString: String?): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
            unixTimeMS < dateTime
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    private fun fixPath(url: String?): String? {
        url ?: return null
        return "https://$url"
    }
    private fun buildApiUrl(sharedPref: SharedPreferences, mainUrl: String): String {
        val sort = sharedPref.getString("sort", "qualitysize")
        val languageOption = sharedPref.getString("language", "")
        val qualityFilter = sharedPref.getString("qualityfilter", "")
        val limit = sharedPref.getString("limit", "")
        val sizeFilter = sharedPref.getString("sizefilter", "")
        val debridProvider = sharedPref.getString("debrid_provider", "") // e.g., "easydebrid"
        val debridKey = sharedPref.getString("debrid_key", "") // e.g., "12345abc"

        val params = mutableListOf<String>()
        if (!sort.isNullOrEmpty()) params += "sort=$sort"
        if (!languageOption.isNullOrEmpty()) params += "language=${languageOption.lowercase()}"
        if (!qualityFilter.isNullOrEmpty()) params += "qualityfilter=$qualityFilter"
        if (!limit.isNullOrEmpty()) params += "limit=$limit"
        if (!sizeFilter.isNullOrEmpty()) params += "sizefilter=$sizeFilter"

        if (!debridProvider.isNullOrEmpty() && !debridKey.isNullOrEmpty()) {
            params += "$debridProvider=$debridKey"
        }

        val query = params.joinToString("%7C")
        return "$mainUrl/$query"
    }
}

suspend fun generateMagnetLink(
    trackerUrls: List<String>,
    hash: String?,
): String {
    require(hash?.isNotBlank() == true)

    val trackers = mutableSetOf<String>()

    trackerUrls.forEach { url ->
        try {
            val response = app.get(url)
            response.text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { trackers.add(it) }
        } catch (_: Exception) {
            // ignore bad sources
        }
    }

    return buildString {
        append("magnet:?xt=urn:btih:").append(hash)

        if (hash.isNotBlank()) {
            append("&dn=")
            append(URLEncoder.encode(hash, StandardCharsets.UTF_8.name()))
        }

        trackers
            .take(10) // practical limit
            .forEach { tracker ->
                append("&tr=")
                append(URLEncoder.encode(tracker, StandardCharsets.UTF_8.name()))
            }
    }
}

