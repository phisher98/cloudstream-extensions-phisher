package com.phisher98

import android.content.SharedPreferences
import android.util.Base64
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
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
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

class TorraStream(private val sharedPref: SharedPreferences) : TmdbProvider() {
    override var name = "TorraStream"
    override var mainUrl = "https://torrentio.strem.fun"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = true

    companion object {
        //const val MediafusionApi = "https://mediafusion.elfhosted.com/D-_ru4-xVDOkpYNgdQZ-gA6whxWtMNeLLsnAyhb82mkks4eJf4QTlrAksSeBnwFAbIGWQLaokCGFxxsHupxSVxZO8xhhB2UYnyc5nnLeDnIqiLajtkmaGJMB_ZHqMqSYIU2wcGhrw0s4hlXeRAfnnbDywHCW8DLF_ZZfOXYUGPzWS-91cvu7kA2xPs0lJtcqZO"
        private const val Cinemeta = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb"
        const val ThePirateBayApi = "https://thepiratebay-plus.strem.fun"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val AnimetoshoAPI = "https://feed.animetosho.org"
        const val TorrentioAnimeAPI = "https://torrentio.strem.fun/providers=nyaasi,tokyotosho,anidex%7Csort=seeders"
        const val TorboxAPI= "https://stremio.torbox.app"
        val TRACKER_LIST_URL = listOf(
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best.txt",
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best_ip.txt",
        )
        private const val Uindex = "https://uindex.org"
        private const val Knaben = "https://knaben.org"
        private const val TorrentsDB = "https://torrentsdb.com"
        const val Meteorfortheweebs ="https://meteorfortheweebs.midnightignite.me"
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = "1865f43a0549ca50d341dd9ab8b29f49"

        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }
    }

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/all/day?api_key=$apiKey&region=US" to "Trending",
        "$tmdbAPI/trending/movie/week?api_key=$apiKey&region=US&with_original_language=en" to "Popular Movies",
        "$tmdbAPI/trending/tv/week?api_key=$apiKey&region=US&with_original_language=en" to "Popular TV Shows",
        "$tmdbAPI/tv/airing_today?api_key=$apiKey&region=US&with_original_language=en" to "Airing Today TV Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2739" to "Disney+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=453" to "Hulu",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2552" to "Apple TV+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=49" to "HBO",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4330" to "Paramount+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=3353" to "Peacock",
        "$tmdbAPI/discover/movie?api_key=$apiKey&language=en-US&page=1&sort_by=popularity.desc&with_origin_country=IN&release_date.gte=${getDate().lastWeekStart}&release_date.lte=${getDate().today}" to "Trending Indian Movies",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().today}&air_date.gte=${getDate().today}" to "Airing Today Anime",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().nextWeek}&air_date.gte=${getDate().today}" to "On The Air Anime",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_keywords=210024|222243" to "Anime Movies",
        "$tmdbAPI/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_genres=99" to "Documentary",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669|190370"
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
            this.score= Score.from10(voteAverage)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query,1)?.items

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return app.get(
            "$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=$page&include_adult=${settingsForProvider.enableAdult}"
        ).parsedSafe<Results>()?.results?.mapNotNull { media ->
            media.toSearchResponse()
        }?.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=keywords,credits,external_ids,videos,recommendations"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=keywords,credits,external_ids,videos,recommendations"
        }
        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
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
            ActorData(
                Actor(
                    cast.name ?: cast.originalName ?: return@mapNotNull null,
                    getImageUrl(cast.profilePath)
                ), roleString = cast.character
            )
        } ?: return null

        val recommendations = res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }?.randomOrNull()

        val comingSoonFlag = when (res.status?.lowercase()) {
            "released" -> false
            "post production", "in production", "planned" -> true
            else -> isUpcoming(releaseDate) // fallback
        }

        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = tmdbAPI,
            apiKey = apiKey,
            type = type,
            tmdbId = res.id,
            appLangCode = "en"
        )
        val animeType = if (data.type?.contains("tv", ignoreCase = true) == true) "series" else "movie"
        val imdbId = res.external_ids?.imdb_id.orEmpty()
        val cineRes = app.get("$Cinemeta/meta/$animeType/$imdbId.json").parsedSafe<CinemetaRes>()

        return if (type == TvType.TvSeries) {
            val episodes = res.seasons?.amap { season ->
                val mediaType = data.type ?: "tv"
                app.get("$tmdbAPI/$mediaType/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(LoadData(
                            res.title,
                            year,
                            isAnime,
                            res.external_ids?.imdb_id,
                            eps.seasonNumber,
                            eps.episodeNumber
                        ).toJson())
                        {
                            this.name = eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath)
                            this.score = Score.from10(eps.voteAverage)
                            this.description = eps.overview
                            this.addDate(eps.airDate)
                        }
                    }.orEmpty()
            }?.flatten() ?: listOf()

            if (isAnime) {
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
                            orgTitle = "",
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
            }

            newTvSeriesLoadResponse(
                title, url, TvType.TvSeries, episodes
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.year = year
                this.plot = res.overview
                this.tags =  keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average.toString())
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                this.episodes = episodes
                this.contentRating = cineRes?.meta?.appExtras?.certification
                addTrailer(trailer)
                addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(res.title,year,isAnime,res.external_ids?.imdb_id).toJson()
            ) {
                this.posterUrl = poster
                this.comingSoon = comingSoonFlag
                this.backgroundPosterUrl = bgPoster
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average.toString())
                this.recommendations = recommendations
                this.actors = actors
                this.contentRating = cineRes?.meta?.appExtras?.certification
                addTrailer(trailer)
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
        val provider = sharedPref.getString("debrid_provider", null)
        val key = sharedPref.getString("debrid_key", null)
        val dataObj = parseJson<LoadData>(data)
        val isAnime = dataObj.isAnime
        val title = dataObj.title
        val season = dataObj.season
        var episode = dataObj.episode
        val id = dataObj.imdbId
        val year = dataObj.year
        val aniResponse = runCatching { app.get("https://api.ani.zip/mappings?imdb_id=$id") }.getOrNull()
        val anijson = aniResponse?.text.orEmpty()
        val aniJson = runCatching { JSONObject(anijson) }.getOrNull()
        val mappings = aniJson?.optJSONObject("mappings")
        val kitsuId = mappings?.optInt("kitsu_id")

        val isMovie = mappings
            ?.optString("type", "")
            ?.contains("MOVIE", ignoreCase = true) == true

        episode = if (isMovie) 1 else episode
        val anidbEid = getAnidbEid(anijson, episode) ?: 0

        val torrentioapiUrl = buildTorrentioApiUrl(sharedPref, mainUrl)
        val meteorUrl = buildMeteorUrl(sharedPref, Meteorfortheweebs)
        val filtered = filteredCallback(sharedPref, callback)

        if (!key.isNullOrEmpty() && provider!="AIO Streams") {
            runAllAsync(
                { invokeTorrentioDebian(torrentioapiUrl, id, season, episode, callback, filtered) },
                { invokeMeteorDebian(meteorUrl, id, season, episode, callback, filtered) }
            )
        }

        when (provider) {
            "AIO Streams" if !key.isNullOrEmpty() -> {
                runAllAsync(
                    { invokeAIOStreamsDebian(key, id, season, episode, callback, filtered) }
                )
            }
            "TorBox" if !key.isNullOrEmpty() -> {
                runAllAsync(
                    { invokeDebianTorbox(TorboxAPI, key, id, season, episode, callback, filtered) }
                )
            }
            else -> {
                runAllAsync(
                    { invokeTorrentio(torrentioapiUrl, id, season, episode, callback, filtered) },
                    {
                        if (!dataObj.isAnime) invokeThepiratebay(
                            ThePirateBayApi,
                            id,
                            season,
                            episode,
                            callback
                        )
                    },
                    { if (dataObj.isAnime) invokeAnimetosho(anidbEid, callback) },
                    { invokeTorrentioAnime(TorrentioAnimeAPI, kitsuId, season, episode, filtered) },
                    {
                        if (!dataObj.isAnime) invokeUindex(
                            Uindex,
                            title,
                            year,
                            season,
                            episode,
                            callback,
                            filtered
                        )
                    },
                    { invokeTorrentsDB(TorrentsDB, id, season, episode, callback) },
                    {
                        if (dataObj.isAnime) invokeTorrentsDBAnime(
                            TorrentsDB,
                            kitsuId,
                            season,
                            episode,
                            callback,
                            filtered
                        )
                    },
                    { invokeKnaben(Knaben, isAnime, title, year, season, episode, callback, filtered) },
                    { invokeSubtitleAPI(id, season, episode, subtitleCallback) }
                )
            }
        }
        return true
    }


    private fun getStatus(t: String?): ShowStatus {
        return when (t?.lowercase()) {
            "returning series", "continuing" -> ShowStatus.Ongoing
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

    private fun buildTorrentioApiUrl(sharedPref: SharedPreferences, mainUrl: String): String {
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

    fun buildMeteorUrl(sharedPref: SharedPreferences, baseUrl: String): String {

        val debridProvider = sharedPref.getString("debrid_provider", "") ?: ""
        val debridKey = sharedPref.getString("debrid_key", "") ?: ""
        val languagesPref = sharedPref.getString("language", "") ?: ""
        val limit = sharedPref.getString("limit", "0") ?: "0"
        val sizeFilter = sharedPref.getString("sizefilter", "0") ?: "0"

        // preferred languages
        val preferredLanguages = JSONArray().apply {
            if (languagesPref.isNotEmpty()) {
                languagesPref.split(",").forEach { put(it.lowercase()) }
            } else {
                put("en")
                put("multi")
            }
        }

        val languages = JSONObject().apply {
            put("preferred", preferredLanguages)
            put("required", JSONArray())
            put("exclude", JSONArray())
        }

        val json = JSONObject().apply {
            put("debridService", debridProvider.lowercase())
            put("debridApiKey", debridKey)
            put("cachedOnly", false)
            put("removeTrash", true)
            put("removeSamples", true)
            put("removeAdult", false)
            put("exclude3D", false)
            put("enableSeaDex", false)

            put("minSeeders", 0)
            put("maxResults", limit.toIntOrNull() ?: 0)
            put("maxResultsPerRes", 0)
            put("maxSize", sizeFilter.toIntOrNull() ?: 0)

            put("resolutions", JSONArray())
            put("languages", languages)

            put(
                "resultFormat",
                JSONArray().apply {
                    put("title")
                    put("quality")
                    put("size")
                    put("audio")
                }
            )

            put(
                "sortOrder",
                JSONArray().apply {
                    put("cached")
                    put("resolution")
                    put("quality")
                    put("seeders")
                    put("size")
                    put("pack")
                    put("language")
                    put("seadex")
                }
            )
        }

        val encoded = Base64.encodeToString(
            json.toString().toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP
        )

        return "$baseUrl/$encoded"
    }
}

suspend fun generateMagnetLink(
    trackerUrls: List<String>,
    hash: String?,
): String {
    require(hash?.isNotBlank() == true)

    val trackers = mutableSetOf<String>()

    trackerUrls.amap { url ->
        runCatching {
            app.get(url).text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }.getOrElse { emptyList() }
    }.flatten().toMutableSet()

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

