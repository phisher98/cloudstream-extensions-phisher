package com.phisher98

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.imdbUrlToIdNullable
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.StreamPlay.Companion.dahmerMoviesAPI
import com.phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import com.phisher98.StreamPlayExtractor.token
import org.json.JSONObject
import java.net.URLEncoder


class StreamPlayStremioCatelog(
    override var mainUrl: String,
    override var name: String,
    val sharedPref: SharedPreferences? = null
) : MainAPI() {
    override val supportedTypes = setOf(TvType.Others,TvType.Movie,
        TvType.TvSeries)
    override val hasMainPage = true

    companion object {
        private const val cinemataUrl = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb"
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        if (mainUrl.isEmpty()) {
            throw IllegalArgumentException("Configure in StreamPlay Catalogs Addon in Extension Settings\n")
        }
        mainUrl = mainUrl.fixSourceUrl()

        val pageSize = 100
        val skip = (page - 1) * pageSize

        val manifest = app
            .get("$mainUrl/manifest.json")
            .parsedSafe<Manifest>()

        val lists = mutableListOf<HomePageList>()

        manifest?.catalogs?.amap { catalog ->
            catalog.toHomePageList(
                provider = this,
                skip = skip
            ).let {
                if (it.list.isNotEmpty()) {
                    lists.add(it)
                }
            }
        }

        return newHomePageResponse(
            lists,
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        mainUrl = mainUrl.fixSourceUrl()
        val res = app.get("${mainUrl}/manifest.json").parsedSafe<Manifest>()
        val list = mutableListOf<SearchResponse>()
        res?.catalogs?.amap { catalog ->
            list.addAll(catalog.search(query, this))
        }
        return list.distinct()
    }

    override suspend fun load(url: String): LoadResponse {
        val res: CatalogEntry = if (url.startsWith("{")) {
            parseJson(url)
        } else {
            val json = app.get(url).text
            val metaJson = JSONObject(json).getJSONObject("meta").toString()
            parseJson(metaJson)
        }

        val encodedId = URLEncoder.encode(res.id, "UTF-8")

        val response = app.get("${mainUrl}/meta/${res.type}/$encodedId.json")
            .parsedSafe<CatalogResponse>()
            ?: throw RuntimeException("Failed to load meta")

        val entry = response.meta
            ?: response.metas?.firstOrNull { it.id == res.id }
            ?: response.metas?.firstOrNull()
            ?: throw RuntimeException("Meta not found")

        return entry.toLoadResponse(this, res.id)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LoadData>(data)
        val imdb = res.resolveImdbId()
        val cinemeta = imdb?.let {
            fetchCinemetaMeta(it, res.type)
        }

        val resolved = res.copy(
            imdbId = imdb,
            title = cinemeta?.title
        )

        val disabledProviderIds = sharedPref
            ?.getStringSet("disabled_providers", emptySet())
            ?.toSet() ?: emptySet()
        val providersList = buildProviders().filter { it.id !in disabledProviderIds }
        val authToken = token
        runLimitedAsync( concurrency = 10,
            {
                try {
                    invokeSubtitleAPI(imdb, resolved.season, resolved.episode, subtitleCallback)
                } catch (_: Throwable) {
                    // ignore failure but do not cancel the rest
                }
            },
            {
                try {
                    invokeWyZIESUBAPI(imdb, res.season, res.episode, subtitleCallback)
                } catch (_: Throwable) {
                    // ignore failure
                }
            },
            *providersList.map { provider ->
                suspend {
                    try {
                        provider.invoke(
                            resolved.toLinkData(),
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

    data class LoadData(
        val type: String? = null,
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val imdbId: String? = null,
        val year: Int? = null,
        val title: String? = null
    )


    private fun LoadData.toLinkData(): StreamPlay.LinkData {
        return StreamPlay.LinkData(
            imdbId = imdbId,
            type = type,
            season = season,
            episode = episode,
            title = title,
            year = year,
        )
    }






    // check if id is imdb/tmdb cause stremio addons like torrentio works base on imdbId
    private fun isImdborTmdb(url: String?): Boolean {
        return imdbUrlToIdNullable(url) != null || url?.startsWith("tmdb:") == true
    }

    private fun isImdb(url: String?): Boolean {
        return imdbUrlToIdNullable(url) != null
    }


    private data class Manifest(val catalogs: List<Catalog>)
    private data class Catalog(
        var name: String?,
        val id: String,
        val type: String?,
        val types: MutableList<String> = mutableListOf()
    ) {
        init {
            if (type != null) types.add(type)
        }

        suspend fun search(query: String, provider: StreamPlayStremioCatelog): List<SearchResponse> {
            val entries = mutableListOf<SearchResponse>()
            types.forEach { type ->
                val res = app.get(
                    "${provider.mainUrl}/catalog/${type}/${id}/search=${query}.json",
                    timeout = 120L
                ).parsedSafe<CatalogResponse>()
                res?.metas?.forEach { entry ->
                    entries.add(entry.toSearchResponse(provider))
                }
            }
            return entries
        }

        suspend fun toHomePageList(provider: StreamPlayStremioCatelog, skip: Int): HomePageList {
            val entries = mutableMapOf<String, SearchResponse>()

            types.forEach { type ->
                val url = if (skip > 0) {
                    "${provider.mainUrl}/catalog/$type/$id/skip=$skip.json"
                } else {
                    "${provider.mainUrl}/catalog/$type/$id.json"
                }

                val res = app.get(
                    url,
                    timeout = 120L
                ).parsedSafe<CatalogResponse>()

                res?.metas?.forEach { entry ->
                    if (!entries.containsKey(entry.id)) {
                        entries[entry.id] = entry.toSearchResponse(provider)
                    }
                }
            }

            return HomePageList(
                name ?: id,
                entries.values.toList()
            )
        }
    }

    private data class CatalogResponse(val metas: List<CatalogEntry>?, val meta: CatalogEntry?)

    private data class Trailer(
        val source: String?,
        val type: String?
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
        @JsonProperty("year") val yearNum: String? = null
    ) {
        fun toSearchResponse(provider: StreamPlayStremioCatelog): SearchResponse {
            return provider.newMovieSearchResponse(
                name,
                this.toJson(),
                TvType.Others
            ) {
                posterUrl = poster
            }
        }

        suspend fun toLoadResponse(provider: StreamPlayStremioCatelog, imdbId: String?): LoadResponse {
            if (videos.isNullOrEmpty()) {
                return provider.newMovieLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type}/${id}.json",
                    TvType.Movie,
                    LoadData(type, id, imdbId = imdbId, year = yearNum?.toIntOrNull())
                ) {
                    posterUrl = poster
                    backgroundPosterUrl = background
                    score = Score.from10(imdbRating)
                    plot = description
                    year = yearNum?.toIntOrNull()
                    tags = genre ?: genres
                    addActors(cast)
                    addTrailer(trailersSources.map { "https://www.youtube.com/watch?v=${it.source}" })
                    addImdbId(imdbId)
                }
            } else {
                return provider.newTvSeriesLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type}/${id}.json",
                    TvType.TvSeries,
                    videos.map {
                        it.toEpisode(provider, type, imdbId)
                    }
                ) {
                    posterUrl = poster
                    backgroundPosterUrl = background
                    score = Score.from10(imdbRating)
                    plot = description
                    year = yearNum?.toIntOrNull()
                    tags = genre ?: genres
                    addActors(cast)
                    addTrailer(trailersSources.map { "https://www.youtube.com/watch?v=${it.source}" }
                        ?.randomOrNull())
                    addImdbId(imdbId)
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
        fun toEpisode(provider: StreamPlayStremioCatelog, type: String?, imdbId: String?): Episode {
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

    suspend fun LoadData.resolveImdbId(): String? {
        val source = imdbId ?: id ?: return null
        val imdb: String? = imdbUrlToIdNullable(source)
        if (imdb != null) return imdb
        return when {
            source.startsWith("tt") -> source
            source.startsWith("tmdb:") -> tmdbToImdb(source.removePrefix("tmdb:"), type)
            source.startsWith("kitsu:") -> kitsuToImdb(source.removePrefix("kitsu:"))
            else -> null
        }
    }




    suspend fun tmdbToImdb(tmdbId: String, type: String?): String? {
        val mediaType = if (type == "series") "tv" else "movie"

        val res = app.get(
            "https://api.themoviedb.org/3/$mediaType/$tmdbId/external_ids",
            params = mapOf("api_key" to "98ae14df2b8d8f8f8136499daf79f0e0")
        ).parsedSafe<TmdbExternalIds>()

        return res?.imdb_id
    }

    data class TmdbExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String?
    )

    suspend fun kitsuToImdb(kitsuId: String): String? {
        val id = kitsuId.removePrefix("kitsu:")

        val res = app.get(
            "https://api.ani.zip/mappings",
            params = mapOf("kitsu_id" to id)
        ).parsedSafe<AniZipResponse>()

        return res?.mappings?.imdb_id
    }

    data class AniZipResponse(
        val mappings: AniZipMappings?
    )

    data class AniZipMappings(
        val imdb_id: String?
    )

    suspend fun fetchCinemetaMeta(
        imdbId: String,
        type: String?
    ): CinemetaMetaData? {
        val mediaType = if (type == "series") "series" else "movie"

        val res = app.get(
            "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta/$mediaType/$imdbId.json"
        ).parsedSafe<CinemetaResponse>()

        return res?.meta?.let {
            CinemetaMetaData(
                title = it.name,
                tmdbId = it.links
                    ?.firstOrNull { link -> link.category == "tmdb" }
                    ?.id
                    ?.toIntOrNull()
            )
        }
    }

    data class CinemetaResponse(
        val meta: CinemetaMeta?
    )

    data class CinemetaMeta(
        val name: String?,
        val links: List<CinemetaLink>?
    )

    data class CinemetaLink(
        val category: String?,
        val id: String?
    )

    data class CinemetaMetaData(
        val title: String?,
        val tmdbId: Int?
    )



}