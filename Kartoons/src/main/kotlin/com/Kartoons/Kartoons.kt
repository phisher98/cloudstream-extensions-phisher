package com.Kartoons


import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.toResponseBody

class Kartoons() : MainAPI() {
    override var mainUrl              = base64Decode("aHR0cHM6Ly9hcGkua2FydG9vbnMuZnVu")
    override var name                 = "Kartoons"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = false
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.Cartoon)

    override val mainPage = mainPageOf(
        "api/shows" to "Shows",
        "api/movies" to "Movies",
        "api/popularity/shows?limit=15&period=day" to "Popular Shows",
        "api/popularity/movies?limit=15&period=day" to "Popular Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("$mainUrl/${request.data}/?page=$page&limit=20").parsed<Home>()
        val home = res.data?.map { it.toSearchResult() }
        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home!!,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Data.toSearchResult(): SearchResponse {
        val title = this.title!!
        val href = fixUrl("/api/${this.type}s/${this.slug}")
        val poster = this.image
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.score = Score.from10(rating)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)
    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("${mainUrl}/api/search/suggestions?q=$query&limit=5").parsedSafeLarge<Search>()?.data?.map {
            newMovieSearchResponse(it.title,fixUrl("/api/${it.type}s/${it.id}"), TvType.Movie)
            {
                this.posterUrl = it.image
            }
        } ?: emptyList()
        return res
    }

    override suspend fun load(url: String): LoadResponse {
        val json = app.get(url).parsedSafeLarge<Load>() ?: throw ErrorLoadingException("Failed to parse JSON")
        val res = json.data ?: throw ErrorLoadingException("Failed to parse load response")
        val title = res.title.orEmpty()
        val description = res.description.orEmpty()
        val poster = res.image.orEmpty()
        val backgroundPoster = res.coverImage ?: res.hoverImage.orEmpty()
        val startYear = res.startYear?.toInt()
        val rating = Score.from10(res.rating ?: 0.0 )
        val tags = res.tags ?: emptyList()
        val href = "$mainUrl/api/movies/${res.id}/links"


        val tvtag = if (res.type?.contains("movie", ignoreCase = true) == true) {
            TvType.Movie
        } else {
            TvType.TvSeries
        }

        val recommendations = json.related.orEmpty().map { rel ->
            newMovieSearchResponse(rel.title.orEmpty(),"$mainUrl/api/${rel.type}s/${rel.slug}", type = TvType.Movie).apply {
                this.posterUrl = rel.image
            }
        }


        return if (tvtag == TvType.TvSeries) {
            val allSeasonDetails = fetchSeasonDetailsForShow(res, mainUrl)

            val episodesList = mutableListOf<Episode>()

            allSeasonDetails.forEach { season ->
                val seasonNumber = season.seasonNumber?.toInt() ?: return@forEach

                season.episodes.forEach { ep ->
                    episodesList += newEpisode("$mainUrl/api/shows/episode/${ep.id}/links") {
                        this.season = seasonNumber
                        this.episode = ep.episodeNumber?.toInt() ?: 0
                        this.name = ep.title.orEmpty()
                        this.posterUrl = ep.image
                        this.description = ep.description
                        this.runTime = ep.durationMinutes?.toInt()
                        addDate(ep.createdAt?.iso.toString())
                    }
                }
            }


            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesList) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.plot = description
                this.year = startYear
                this.score = rating
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.plot = description
                this.year = startYear
                this.score = rating
                this.tags = tags
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data).parsedSafe<Loadlinks>()?.data ?: return false
        res.links?.forEach { link ->
            val encoded = link.url ?: return@forEach

            val m3u8 = try {
                decryptAesCbcBase64Url(encoded)
            } catch (e: Exception) {
                Log.e(name, "Decryption failed for ${link.name}: ${e.message}")
                return@forEach
            }

            if (m3u8.isBlank()) return@forEach
            callback.invoke(
                newExtractorLink(
                    name,
                    link.name ?: name,
                    m3u8,
                    ExtractorLinkType.M3U8
                )
                {
                    this.referer = m3u8
                }
            )
        }
        return true
    }

    private val ENC_LINE_REGEX by lazy {
        Regex("""^enc\d+:.+""", RegexOption.IGNORE_CASE)
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            Log.d(name, "Request URL: $url")

            val response = chain.proceed(request)
            val body = response.body
            val contentType = body.contentType()

            // Detect playlist; skip everything else (segments, TS, MP4, etc.)
            val isPlaylistUrl =
                url.endsWith(".m3u8", ignoreCase = true) ||
                        url.contains("playlist", ignoreCase = true)

            val ctString = contentType?.toString() ?: ""
            val isPlaylistContentType =
                ctString.contains("mpegurl", ignoreCase = true) ||
                        ctString.contains("m3u8", ignoreCase = true)

            if (!isPlaylistUrl && !isPlaylistContentType) {
                Log.d(name, "Not a playlist response, skipping rewrite")
                return@Interceptor response
            }

            val originalText = try {
                body.string()
            } catch (e: Exception) {
                Log.e(name, "Failed to read playlist body: ${e.message}")
                return@Interceptor response
            }

            Log.d(name, "Original playlist:\n$originalText")

            if (!originalText.contains("enc", ignoreCase = true)) {
                Log.d(name, "No enc lines found, returning original playlist")
                return@Interceptor response.newBuilder()
                    .body(originalText.toResponseBody(contentType))
                    .build()
            }

            val rewritten = originalText
                .lineSequence()
                .joinToString("\n") { rawLine ->
                    val line = rawLine.trimEnd()
                    val trimmed = line.trimStart()

                    if (ENC_LINE_REGEX.matches(trimmed)) {
                        Log.d(name, "Found encrypted line: $trimmed")
                        val decrypted = decryptStream(trimmed)
                        Log.d(name, "Decrypted line: ${decrypted ?: "FAILED"}")
                        decrypted ?: line
                    } else {
                        line
                    }
                }

            Log.d(name, "Rewritten playlist:\n$rewritten")

            response.newBuilder()
                .body(rewritten.toResponseBody(contentType))
                .build()
        }
    }




}


private suspend fun fetchSeasonDetailsForShow(
    showData: LoadData,
    mainUrl: String
): List<SeasonEpisodes> = coroutineScope {
    val showSlug = showData.slug ?: return@coroutineScope emptyList()

    showData.seasons.orEmpty()
        .mapNotNull { season ->
            val seasonSlug = season.slug ?: return@mapNotNull null

            async {
                val seasonUrl = "$mainUrl/api/shows/$showSlug/season/$seasonSlug/all-episodes"

                val root = app.get(seasonUrl)
                    .parsedSafeLarge<EpisodesRoot>()

                root?.let {
                    SeasonEpisodes(
                        seasonNumber = it.season?.seasonNumber,
                        episodes = it.data.orEmpty()
                    )
                }
            }
        }
        .awaitAll()
        .filterNotNull()
}



