package com.phisher98

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
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
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
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.SubsExtractors.invokeOpenSubs
import com.phisher98.SubsExtractors.invokeWatchsomuch

class StremioX(override var mainUrl: String, override var name: String) : TmdbProvider() {
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Others)

    companion object {
        const val TRACKER_LIST_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val Cinemeta = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb"
        private const val apiKey = BuildConfig.TMDB_API

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
        "$tmdbAPI/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "$tmdbAPI/movie/upcoming?api_key=$apiKey&region=US" to "Upcoming Movies",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Shows",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
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
        val isAnime = genres?.contains("Animation") == true && (res.original_language == "zh" || res.original_language == "ja")
        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }

        val isCartoon = genres?.contains("Animation") ?: false
        val isAsian = !isAnime && (res.original_language == "zh" || res.original_language == "ko")
        val isBollywood = res.production_countries?.any { it.name == "India" } ?: false

        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: cast.originalName ?: return@mapNotNull null,
                    getImageUrl(cast.profilePath)
                ), roleString = cast.character
            )
        } ?: return null
        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer =
            res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }?.randomOrNull()


        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = "https://api.themoviedb.org/3",
            apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
            type = type,
            tmdbId = res.id,
            appLangCode = "en"
        )

        val animeType = if (data.type?.contains("tv", ignoreCase = true) == true) "series" else "movie"
        val imdbId = res.external_ids?.imdb_id.orEmpty()
        val cineRes = app.get("$Cinemeta/meta/$animeType/$imdbId.json").parsedSafe<CinemetaRes>()

        return if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(LoadData(
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
                    }
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
                //this.contentRating = fetchContentRating(data.id, "US")
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(res.external_ids?.imdb_id).toJson()
            ) {
                this.posterUrl = poster
                this.comingSoon = isUpcoming(releaseDate)
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average.toString())
                this.recommendations = recommendations
                this.actors = actors
                //this.contentRating = fetchContentRating(data.id, "US")
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
        val res = parseJson<LoadData>(data)
        runAllAsync(
            {
                Log.d("Phisher",res.imdbId.toString())

                invokeMainSource(res.imdbId, res.season, res.episode, subtitleCallback, callback)
            },
            {
                invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback)
            },
            {
                invokeOpenSubs(res.imdbId, res.season, res.episode, subtitleCallback)
            },
        )

        return true
    }

    private suspend fun invokeMainSource(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixMainUrl = (mainUrl.takeIf { it.isNotBlank() } ?: "https://torrentio.strem.fun/manifest.json").fixSourceUrl()
        val url = if (season == null) {
            "$fixMainUrl/stream/movie/$imdbId.json"
        } else {
            "$fixMainUrl/stream/series/$imdbId:$season:$episode.json"
        }
        val res = app.get(url, timeout = 120L).parsedSafe<StreamsResponse>()
        res?.streams?.forEach { stream ->
            stream.runCallback(subtitleCallback, callback)
        }
    }

    private data class StreamsResponse(val streams: List<Stream>)
    private data class Subtitle(
        val url: String?,
        val lang: String?,
        val id: String?,
    )

    private data class ProxyHeaders(
        val request: Map<String, String>?,
    )

    private data class BehaviorHints(
        val proxyHeaders: ProxyHeaders?,
        val headers: Map<String, String>?,
    )

    private data class Stream(
        val name: String?,
        val title: String?,
        val url: String?,
        val description: String?,
        val ytId: String?,
        val externalUrl: String?,
        val behaviorHints: BehaviorHints?,
        val infoHash: String?,
        val sources: List<String> = emptyList(),
        val subtitles: List<Subtitle> = emptyList()
    ) {
        suspend fun runCallback(
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            if (url != null) {
                callback.invoke(
                    newExtractorLink(
                        name ?: "",
                        fixSourceName(name, title),
                        url,
                        INFER_TYPE,
                    )
                    {
                        this.quality=getQuality(listOf(description,title,name))
                        this.headers=behaviorHints?.proxyHeaders?.request ?: behaviorHints?.headers ?: mapOf()
                    }
                )
                subtitles.map { sub ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            SubtitleHelper.fromTagToEnglishLanguageName(sub.lang ?: "") ?: sub.lang
                            ?: "",
                            sub.url ?: return@map
                        )
                    )
                }
            }
            if (ytId != null) {
                loadExtractor("https://www.youtube.com/watch?v=$ytId", subtitleCallback, callback)
            }
            if (externalUrl != null) {
                loadExtractor(externalUrl, subtitleCallback, callback)
            }
            if (infoHash != null) {
                val resp = app.get(TRACKER_LIST_URL).text
                val otherTrackers = resp
                    .split("\n")
                    .filterIndexed { i, _ -> i % 2 == 0 }
                    .filter { s -> s.isNotEmpty() }.joinToString("") { "&tr=$it" }

                val sourceTrackers = sources
                    .filter { it.startsWith("tracker:") }
                    .map { it.removePrefix("tracker:") }
                    .filter { s -> s.isNotEmpty() }.joinToString("") { "&tr=$it" }

                val magnet = "magnet:?xt=urn:btih:${infoHash}${sourceTrackers}${otherTrackers}"
                callback.invoke(
                    newExtractorLink(
                        name ?: "",
                        title ?: name ?: "",
                        magnet,
                    )
                    {
                        this.quality=Qualities.Unknown.value
                    }
                )
            }
        }
    }

    data class LoadData(
        val imdbId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
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
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class ExternalIds(
        @get:JsonProperty("imdb_id") val imdb_id: String? = null,
        @get:JsonProperty("tvdb_id") val tvdb_id: Int? = null,
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

    data class ResultsAltTitles(
        @get:JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
    )

    data class AltTitles(
        @get:JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
        @get:JsonProperty("title") val title: String? = null,
        @get:JsonProperty("type") val type: String? = null,
    )

    data class ProductionCountries(
        @get:JsonProperty("name") val name: String? = null,
    )

    data class CinemetaRes(
        val meta: Meta? = null
    ) {

        data class Meta(
            val id: String? = null,
            val type: String? = null,
            val name: String? = null,

            @JsonProperty("imdb_id")
            val imdbId: String? = null,

            val slug: String? = null,

            val director: String? = null,
            val writer: String? = null,

            val description: String? = null,
            val year: String? = null,
            val releaseInfo: String? = null,
            val released: String? = null,
            val runtime: String? = null,
            val status: String? = null,
            val country: String? = null,
            val imdbRating: String? = null,
            val genres: List<String>? = null,
            val poster: String? = null,
            @JsonProperty("_rawPosterUrl")
            val rawPosterUrl: String? = null,

            val background: String? = null,
            val logo: String? = null,

            val videos: List<Video>? = null,
            val trailers: List<Trailer>? = null,
            val trailerStreams: List<TrailerStream>? = null,
            val links: List<Link>? = null,

            val behaviorHints: BehaviorHints? = null,

            @JsonProperty("app_extras")
            val appExtras: AppExtras? = null,
        ) {

            data class BehaviorHints(
                val defaultVideoId: Any? = null,
                val hasScheduledVideos: Boolean? = null
            )

            data class Link(
                val name: String? = null,
                val category: String? = null,
                val url: String? = null
            )

            data class Trailer(
                val source: String? = null,
                val type: String? = null,
                val name: String? = null
            )

            data class TrailerStream(
                val ytId: String? = null,
                val title: String? = null
            )

            data class Video(
                val id: String? = null,
                val title: String? = null,
                val season: Int? = null,
                val episode: Int? = null,
                val thumbnail: String? = null,
                val overview: String? = null,
                val released: String? = null,
                val available: Boolean? = null,
                val runtime: String? = null
            )

            data class AppExtras(
                val cast: List<Cast>? = null,
                val directors: List<Any?>? = null,
                val writers: List<Any?>? = null,
                val seasonPosters: List<String?>? = null,
                val certification: String? = null
            )

            data class Cast(
                val name: String? = null,
                val character: String? = null,
                val photo: String? = null
            )
        }
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

}
