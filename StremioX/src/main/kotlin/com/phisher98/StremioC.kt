package com.phisher98

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.imdbUrlToIdNullable
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.StremioC.Companion.TRACKER_LIST_URLS
import com.phisher98.SubsExtractors.invokeOpenSubs
import com.phisher98.SubsExtractors.invokeWatchsomuch
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class StremioC(override var mainUrl: String, override var name: String) : MainAPI() {
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true
     
    private var cachedManifest: Manifest? = null
    private var lastManifestUrl: String = ""
    private var lastCacheTime: Long = 0
    private val catalogSentIds = mutableMapOf<String, MutableSet<String>>()
    private val pageContentCache = mutableMapOf<String, List<SearchResponse>>()
    
    companion object {
        private const val cinemeta = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb"
        private const val cinemetav3 = "https://v3-cinemeta.strem.io"

        val TRACKER_LIST_URLS = listOf(
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best.txt",
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best_ip.txt",
        )
        private const val TRACKER_LIST_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = BuildConfig.TMDB_API
    }

    private fun baseUrl(): String {
        return mainUrl.substringBefore("?").trimEnd('/')
    }

    private fun querySuffix(): String {
        return mainUrl.substringAfter("?", "")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" }
            ?: ""
    }

    private fun buildUrl(path: String): String {
        return "${baseUrl()}$path${querySuffix()}"
    }

    private suspend fun getManifest(): Manifest? {
        val currentUrl = buildUrl("/manifest.json")
        val now = System.currentTimeMillis()
        val cacheAge = now - lastCacheTime
        val isExpired = cacheAge > 24 * 60 * 60 * 1000 // 24Hour

        if (cachedManifest != null && 
            lastManifestUrl == currentUrl && 
            !isExpired && 
            !cachedManifest?.catalogs.isNullOrEmpty()) {
            return cachedManifest
        }

        val res = app.get(currentUrl, timeout = 120L).parsedSafe<Manifest>()

        if (res != null && res.catalogs.isNotEmpty()) {
            cachedManifest = res
            lastManifestUrl = currentUrl
            lastCacheTime = now
            pageContentCache.clear()
            catalogSentIds.clear()
        } else {
            Log.d("Error:","Null")
        }        
        return res ?: cachedManifest
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        if (mainUrl.isEmpty()) throw IllegalArgumentException("Configure in Extension Settings\n")
        mainUrl = mainUrl.fixSourceUrl()

        if (page <= 1) {
            catalogSentIds.clear()
        }

        val skip = (page - 1) * 100
        val manifest = getManifest()
        val targetCatalogs = manifest?.catalogs?.filter { !it.isSearchRequired() } ?: emptyList()

        val lists = targetCatalogs.amap { catalog ->
            val catalogKey = catalog.id
            val cacheKey = "${catalogKey}_$skip"

            val cachedItems = pageContentCache[cacheKey]
            
            val row = if (cachedItems != null) {
                HomePageList(catalog.name ?: catalog.id, cachedItems)
            } else {
                val freshRow = catalog.toHomePageList(provider = this, skip = skip)
                if (freshRow.list.isNotEmpty()) {
                    pageContentCache[cacheKey] = freshRow.list
                }
                freshRow
            }
            
            val seenForThisCatalog = catalogSentIds.getOrPut(catalogKey) { mutableSetOf() }
            val filteredItems = row.list.filter { item ->
                seenForThisCatalog.add(item.url)
            }
            
            row.copy(list = filteredItems)
        }.filter { it.list.isNotEmpty() }

        return newHomePageResponse(
            lists,
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        mainUrl = mainUrl.fixSourceUrl()
        val manifest = getManifest()
        val supportedCatalogs = manifest?.catalogs?.filter { it.supportsSearch() } ?: emptyList()
        val addonResults = supportedCatalogs.amap { catalog -> catalog.search(query, this) }.flatten().distinctBy { it.url }
        if (addonResults.isNotEmpty()) {
            return addonResults
        }
        return searchTMDb(query)
    }

    private suspend fun searchTMDb(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$encoded&page=1&include_adult=false"
        val results = app.get(url, timeout = 120L).parsedSafe<Results>()?.results ?: emptyList()
        return results.filter { it.mediaType == "movie" || it.mediaType == "tv" }.distinctBy { "${it.mediaType}:${it.id}" }.mapNotNull { media ->
                val stremioType =
                    if (media.mediaType == "tv") "series"
                    else "movie"

                val title = media.title ?: media.name ?: media.originalTitle ?: return@mapNotNull null
                val poster = media.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

                val entry = CatalogEntry(
                    name = title,
                    id = "tmdb:${media.id}",
                    type = stremioType,
                    poster = poster,
                    background = poster,
                    description = null,
                    imdbRating = null,
                    videos = null,
                    genre = null
                )

                newMovieSearchResponse(
                    title,
                    entry.toJson(),
                    if (stremioType == "series") TvType.TvSeries else TvType.Movie
                ) {
                    posterUrl = poster
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val res: CatalogEntry = if (url.startsWith("{")) {
            parseJson(url)
        } else {
            val json = app.get(url).text
            val metaJson = JSONObject(json).getJSONObject("meta").toString()
            parseJson(metaJson)
        }
        val normalizedId = normalizeId(res.id)
        val encodedId = URLEncoder.encode(normalizedId, "UTF-8")
        val response = app.get(buildUrl("/meta/${res.type}/$encodedId.json"))
            .parsedSafe<CatalogResponse>()
            ?: throw RuntimeException("Failed to load meta")

        val entry = response.meta
            ?: response.metas?.firstOrNull { it.id == res.id }
            ?: response.metas?.firstOrNull()
            ?: run {
                val fallback = app.get(
                    "$cinemeta/meta/${res.type}/$encodedId.json",
                    timeout = 120L
                ).parsedSafe<CatalogResponse>()

                fallback?.meta
                    ?: fallback?.metas?.firstOrNull()
                    ?: throw RuntimeException("Meta not found (primary + fallback)")
            }

        return entry.toLoadResponse(this, res.id)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val normalizedId = normalizeId(loadData.id)
        val encodedId = URLEncoder.encode(normalizedId, "UTF-8")
        val request = app.get(buildUrl("/stream/${loadData.type}/$encodedId.json"), timeout = 120L)

        val res = if (request.isSuccessful)
            request.parsedSafe<StreamsResponse>()
        else
            null

        if (!res?.streams.isNullOrEmpty()) {
            res.streams.forEach { stream ->
                stream.runCallback(subtitleCallback, callback)
            }
        } else {
            runAllAsync(
                    {
                        invokeStremioX(loadData.type, loadData.id, subtitleCallback, callback)
                    },{
                        invokeTorrentio(loadData.imdbId, loadData.season, loadData.episode, callback)
                    },
                    {
                        invokeKnaben(loadData.imdbId, loadData.year,loadData.season, loadData.episode, callback)
                    },
                    {
                        invokeUindex(loadData.imdbId, loadData.year,loadData.season, loadData.episode, callback)
                    },
                    {
                        invokeWatchsomuch(
                            loadData.imdbId,
                            loadData.season,
                            loadData.episode,
                            subtitleCallback
                        )
                    },
                    {
                        invokeOpenSubs(
                            loadData.imdbId,
                            loadData.season,
                            loadData.episode,
                            subtitleCallback
                        )
                    }
            )
        }

        return true
    }

    private suspend fun invokeStremioX(
        type: String?,
        id: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sites = AcraApplication.getKey<Array<CustomSite>>(USER_PROVIDER_API)?.toMutableList()
            ?: mutableListOf()
        sites.filter { it.parentJavaClass == "StremioX" }.amap { site ->
            val res = app.get(
                "${site.url.fixSourceUrl()}/stream/${type}/${id}.json",
                timeout = 120L
            ).parsedSafe<StreamsResponse>()
            res?.streams?.forEach { stream ->
                stream.runCallback(subtitleCallback, callback)
            }
        }
    }

    data class LoadData(
        val type: String? = null,
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val imdbId: String? = null,
        val year: Int? = null
    )

    data class CustomSite(
        @JsonProperty("parentJavaClass") val parentJavaClass: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("lang") val lang: String,
    )

    private fun isImdborTmdb(url: String?): Boolean {
        return imdbUrlToIdNullable(url) != null || url?.startsWith("tmdb:") == true
    }

    private fun isImdb(url: String?): Boolean {
        return imdbUrlToIdNullable(url) != null
    }

   private data class Manifest(val catalogs: List<Catalog>)
    
    private data class Extra(
        @JsonProperty("name") val name: String?,
        @JsonProperty("isRequired") val isRequired: Boolean? = false
    )

    private data class Catalog(
        var name: String?,
        val id: String,
        val type: String?,
        val types: MutableList<String> = mutableListOf(),
        @JsonProperty("extra") val extra: List<Extra>? = null,
        @JsonProperty("extraSupported") val extraSupported: List<String>? = null
    ) {
        init {
            if (type != null) types.add(type)
        }

        fun isSearchRequired(): Boolean {
            return extra?.any { it.name == "search" && it.isRequired == true } == true
        }

        fun supportsSearch(): Boolean {
            val hasSearchInExtra = extra?.any { it.name == "search" } == true
            val hasSearchInExtraSupported = extraSupported?.contains("search") == true
            return hasSearchInExtra || hasSearchInExtraSupported
        }

        suspend fun search(query: String, provider: StremioC): List<SearchResponse> {
            val allMetas = types.amap { type ->
                val searchUrl = provider.buildUrl("/catalog/${type}/${id}/search=${URLEncoder.encode(query, "UTF-8")}.json")
                val res = app.get(searchUrl, timeout = 120L).parsedSafe<CatalogResponse>()
                res?.metas ?: emptyList()
            }.flatten()

            return allMetas.distinctBy { it.id }.map { it.toSearchResponse(provider) }
        }

        suspend fun toHomePageList(
            provider: StremioC,
            skip: Int
        ): HomePageList {
            val allMetas = types.amap { type ->
                val path = if (skip > 0) {
                    "/catalog/$type/$id/skip=$skip.json"
                } else {
                    "/catalog/$type/$id.json"
                }
                val url = provider.buildUrl(path)

                val res = app.get(url, timeout = 120L).parsedSafe<CatalogResponse>()
                res?.metas ?: emptyList()
            }.flatten()

            val distinctEntries = allMetas.distinctBy { it.id }.map { it.toSearchResponse(provider) }

            return HomePageList(
                name ?: id,
                distinctEntries
            )
        }
    }

    private data class CatalogResponse(val metas: List<CatalogEntry>?, val meta: CatalogEntry?)

    private data class Trailer(
        val source: String?,
        val type: String?
    )

    private data class TrailerStream(
        @JsonProperty("ytId") val ytId: String?,
        @JsonProperty("title") val title: String? = null        
    )

    private data class Link(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("url") val url: String? = null
    )

    private data class CatalogEntry(
        @JsonProperty("name") val name: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("background") val background: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("imdbRating") val imdbRating: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("videos") val videos: List<Video>?,
        @JsonProperty("genre") val genre: List<String>?,
        @JsonProperty("genres") val genres: List<String> = emptyList(),
        @JsonProperty("cast") val cast: List<String> = emptyList(),
        @JsonProperty("trailers") val trailersSources: List<Trailer> = emptyList(),
        @JsonProperty("trailerStreams") val trailerStreams: List<TrailerStream> = emptyList(),
        @JsonProperty("year") val yearNum: String? = null,
        @JsonProperty("links") val links: List<Link> = emptyList()
    ) {
        fun toSearchResponse(provider: StremioC): SearchResponse {
            return provider.newMovieSearchResponse(
                name,
                this.toJson(),
                TvType.Others
            ) {
                posterUrl = poster
            }
        }

        suspend fun toLoadResponse(provider: StremioC, imdbId: String?): LoadResponse {
            val allTrailers = (trailersSources.mapNotNull { it.source } + trailerStreams.mapNotNull { it.ytId })
                .distinct()
                .map { "https://www.youtube.com/watch?v=$it" }
            
            var fetchedRecommendations: List<SearchResponse>? = null

            val extractedImdbId = links.firstOrNull { it.category == "imdb" }?.url?.substringAfterLast("/")?.takeIf { it.startsWith("tt") }
            val extractedTmdbId = if (this.id.startsWith("tmdb:")) this.id.removePrefix("tmdb:") else null
            val finalImdbId = extractedImdbId ?: (if (this.id.startsWith("tt")) this.id else imdbId)
            var tmdbIdStr: String? = extractedTmdbId

            try {
                val isMovie = type == "movie" || videos.isNullOrEmpty()
                val tmdbMediaType = if (isMovie) "movie" else "tv"

                if (tmdbIdStr == null && finalImdbId?.startsWith("tt") == true) {
                    val findUrl = "$tmdbAPI/find/$finalImdbId?api_key=$apiKey&external_source=imdb_id"
                    val findRes = app.get(findUrl).parsedSafe<TmdbFindResponse>()
                    
                    val tmdbId = if (isMovie) findRes?.movie_results?.firstOrNull()?.id else findRes?.tv_results?.firstOrNull()?.id
                    if (tmdbId != null) {
                        tmdbIdStr = tmdbId.toString()
                    }
                }

                if (tmdbIdStr != null) {
                    val detailUrl = "$tmdbAPI/$tmdbMediaType/$tmdbIdStr?api_key=$apiKey&append_to_response=recommendations"
                    val detailRes = app.get(detailUrl).parsedSafe<TmdbDetailResponse>()
                    
                    fetchedRecommendations = detailRes?.recommendations?.results?.mapNotNull { media ->
                        val recTitle = media.title ?: media.name ?: media.originalTitle ?: return@mapNotNull null
                        val posterUrl = if (media.posterPath?.startsWith("/") == true) "https://image.tmdb.org/t/p/original${media.posterPath}" else media.posterPath
                        
                        val rawMediaType = media.mediaType ?: tmdbMediaType
                        val stremioType = if (rawMediaType == "tv") "series" else "movie"
                        
                        val recommendationEntry = CatalogEntry(
                            name = recTitle,
                            id = "tmdb:${media.id}",
                            type = stremioType, 
                            poster = posterUrl,
                            background = null,
                            description = media.overview,
                            imdbRating = null,
                            videos = null,
                            genre = null
                        )
                        
                        provider.newMovieSearchResponse(
                            recTitle,
                            recommendationEntry.toJson(),
                            if (stremioType == "movie") TvType.Movie else TvType.TvSeries
                        ) {
                            this.posterUrl = posterUrl
                        }
                    }
                }
            } catch (_: Exception) {
            }

            if (videos.isNullOrEmpty()) {
                return provider.newMovieLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type}/${id}.json",
                    TvType.Movie,
                    LoadData(type, id, imdbId = finalImdbId, year = yearNum?.toIntOrNull())
                ) {
                    posterUrl = poster
                    backgroundPosterUrl = background
                    score = Score.from10(imdbRating)
                    plot = description
                    year = yearNum?.toIntOrNull()
                    tags = genre ?: genres
                    addActors(cast)
                    addTrailer(allTrailers)                    
                    
                    this.recommendations = fetchedRecommendations
                    
                    tmdbIdStr?.let { 
                        addTMDbId(it)
                    }
                    finalImdbId?.let { 
                        if (it.startsWith("tt")) {
                            addImdbId(it)
                        } else {
                            println("Kitsu or TMDB ID: $it")
                        }
                    }
                }
            } else {
                return provider.newTvSeriesLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type}/${id}.json",
                    TvType.TvSeries,
                    videos.map {
                        it.toEpisode(provider, type, finalImdbId)
                    }
                ) {
                    posterUrl = poster
                    backgroundPosterUrl = background
                    score = Score.from10(imdbRating)
                    plot = description
                    year = yearNum?.toIntOrNull()
                    tags = genre ?: genres
                    addActors(cast)
                    addTrailer(allTrailers.randomOrNull())
                    
                    this.recommendations = fetchedRecommendations
                    
                    tmdbIdStr?.let { 
                        addTMDbId(it)
                    }
                    finalImdbId?.let { 
                        if (it.startsWith("tt")) {
                            addImdbId(it)
                        } else {
                            println("Kitsu or TMDB ID: $it")
                        }
                    }
                }
            }

        }
    }

    private data class Video(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season") val seasonNumber: Int? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("description") val description: String? = null,
    ) {
        fun toEpisode(provider: StremioC, type: String?, imdbId: String?): Episode {
            return provider.newEpisode(
                LoadData(type, id, seasonNumber, episode ?: number, imdbId)
            ) {
                this.name = this@Video.name ?: title
                this.posterUrl = thumbnail
                this.description = overview ?:  this@Video.description
                this.season = seasonNumber
                this.episode =  this@Video.episode ?: number
            }
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
}

private data class TmdbFindResponse(
    @JsonProperty("movie_results") val movie_results: List<TmdbFindResult>? = null,
    @JsonProperty("tv_results") val tv_results: List<TmdbFindResult>? = null
)

private data class TmdbFindResult(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("media_type") val media_type: String? = null
)

private data class TmdbDetailResponse(
    @JsonProperty("recommendations") val recommendations: TmdbRecommendations? = null
)

private data class TmdbRecommendations(
    @JsonProperty("results") val results: List<TmdbMedia>? = null
)

private data class TmdbMedia(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("overview") val overview: String? = null
)

//TMDB Search


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
    @JsonProperty("imdb_id") val imdb_id: String? = null,
    @JsonProperty("tvdb_id") val tvdb_id: String? = null,
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
)

suspend fun invokeUindex(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val uindex = "https://uindex.org"
    val isTv = season != null

    val searchQuery = buildString {
        if (!title.isNullOrBlank()) append(title)
        if (year != null) {
            if (isNotEmpty()) append(' ')
            append(year)
        }
    }.replace(' ', '+')

    val url = "$uindex/search.php?search=$searchQuery&c=${if (isTv) 2 else 1}"

    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )

    val rows = app.get(url, headers = headers).documentLarge.select("tr")

    val episodePatterns: List<Regex> = if (isTv && episode != null) {
        val rawPatterns = listOf(
            String.format(Locale.US, "S%02dE%02d", season, episode),
            "S${season}E$episode",
            String.format(Locale.US, "S%02dE%d", season, episode),
            String.format(Locale.US, "S%dE%02d", season, episode),
        )

        rawPatterns.distinct().map {
            Regex("\\b$it\\b", RegexOption.IGNORE_CASE)
        }
    } else {
        emptyList()
    }

    rows.amap { row ->
        val rowTitle = row.select("td:nth-child(2) > a:nth-child(2)").text()
        val magnet = row.select("td:nth-child(2) > a:nth-child(1)").attr("href")

        if (rowTitle.isBlank() || magnet.isBlank()) return@amap

        if (isTv && episodePatterns.isNotEmpty()) {
            if (episodePatterns.none { it.containsMatchIn(rowTitle) }) return@amap
        }

        val qualityMatch = "(2160p|1080p|720p)"
            .toRegex(RegexOption.IGNORE_CASE)
            .find(rowTitle)
            ?.value

        val seeder = row
            .select("td:nth-child(4) > span")
            .text()
            .replace(",", "")
            .ifBlank { "0" }

        val fileSize = row.select("td:nth-child(3)").text()

        val formattedTitleName = run {
            val qualityTermsRegex =
                "(WEBRip|WEB-DL|x265|x264|10bit|HEVC|H264)"
                    .toRegex(RegexOption.IGNORE_CASE)

            val tags = qualityTermsRegex.findAll(rowTitle)
                .map { it.value.uppercase() }
                .distinct()
                .joinToString(" | ")

            "UIndex | $tags | Seeder: $seeder | FileSize: $fileSize".trim()
        }

        callback.invoke(
            newExtractorLink(
                "UIndex",
                formattedTitleName.ifBlank { rowTitle },
                url = magnet,
                type = INFER_TYPE
            ) {
                this.quality = getQualityFromName(qualityMatch)
            }
        )
    }
}

suspend fun invokeKnaben(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val knaben = "https://knaben.org"
    val isTv = season != null
    val host = knaben.trimEnd('/')

    val baseQuery = buildString {
        val queryText = title?.takeIf { it.isNotBlank() } ?: return@buildString

        append(
            queryText
                .trim()
                .replace("\\s+".toRegex(), "+")
        )

        if (isTv && episode != null) {
            append("+S${season.toString().padStart(2, '0')}")
            append("E${episode.toString().padStart(2, '0')}")
        } else if (!isTv && year != null) {
            append("+$year")
        }
    }

    if (baseQuery.isBlank()) return

    val category = when {
        isTv -> "2000000"
        else -> "3000000"
    }

    for (page in 1..2) {
        val url = "$host/search/$baseQuery/$category/$page/seeders"

        val doc = app.get(url).document

        doc.select("tr.text-nowrap.border-start").forEach { row ->
            val infoTd = row.selectFirst("td:nth-child(2)") ?: return@forEach

            val titleElement = infoTd.selectFirst("a[title]") ?: return@forEach
            val rawTitle = titleElement.attr("title").ifBlank { titleElement.text() }

            val magnet = infoTd.selectFirst("a[href^=magnet:?]")?.attr("href") ?: return@forEach

            val source = row
                .selectFirst("td.d-sm-none.d-xl-table-cell a")
                ?.text()
                ?.trim()
                .orEmpty()

            val tds = row.select("td")
            val sizeText = tds.getOrNull(2)?.text().orEmpty()
            val seedsText = tds.getOrNull(4)?.text().orEmpty()
            val seeds = seedsText.toIntOrNull() ?: 0
            val qualityMatch = "(2160p|1080p|720p)"
                .toRegex(RegexOption.IGNORE_CASE)
                .find(rawTitle)
                ?.value
            val formattedTitleName = buildString {
                append("Knaben | ")
                append(rawTitle)

                if (seeds > 0) {
                    append(" | Seeds: ")
                    append(seeds)
                }

                if (sizeText.isNotBlank()) {
                    append(" | ")
                    append(sizeText)
                }

                if (source.isNotBlank()) {
                    append(" | ")
                    append(source)
                }
            }

            callback(
                newExtractorLink(
                    "Knaben",
                    formattedTitleName.ifBlank { rawTitle },
                    url = magnet,
                    type = INFER_TYPE
                ) {
                    this.quality = getQualityFromName(qualityMatch)
                }
            )
        }
    }
}

suspend fun invokeTorrentio(
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if (id?.startsWith("kitsu:") == true) {
        "https://torrentio.strem.fun/stream/series/$id:${episode ?: 1}.json"
    } else if (season == null) {
        "https://torrentio.strem.fun/stream/movie/$id.json"
    } else {
        "https://torrentio.strem.fun/stream/series/$id:$season:$episode.json"
    }
    
    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )
    val res = app.get(url, headers = headers, timeout = 100L).parsedSafe<TorrentioResponse>()
    res?.streams?.forEach { stream ->
        val formattedTitleName = stream.title
            ?.let { title ->
                val qualityTermsRegex = "(2160p|1080p|720p|WEBRip|WEB-DL|x265|x264|10bit|HEVC|H264)".toRegex(RegexOption.IGNORE_CASE)
                val tagsList = qualityTermsRegex.findAll(title).map { it.value.uppercase() }.toList()
                val tags = tagsList.distinct().joinToString(" | ")

                val seeder = "👤\\s*(\\d+)".toRegex().find(title)?.groupValues?.get(1) ?: "0"
                val provider = "⚙️\\s*([^\\n]+)".toRegex().find(title)?.groupValues?.get(1)?.trim() ?: "Unknown"

                "Torrentio | $tags | Seeder: $seeder | Provider: $provider".trim()
            }

        val qualityMatch = "(2160p|1080p|720p)".toRegex(RegexOption.IGNORE_CASE)
            .find(stream.title ?: "")
            ?.value
            ?.lowercase()

        val magnet = generateMagnetLink(TRACKER_LIST_URLS, stream.infoHash)

        callback.invoke(
            newExtractorLink(
                "Torrentio",
                formattedTitleName ?: stream.name ?: "",
                url = magnet,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getQualityFromName(qualityMatch)
            }
        )
    }
}
