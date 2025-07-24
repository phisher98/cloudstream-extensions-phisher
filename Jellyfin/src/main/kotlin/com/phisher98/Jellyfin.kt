package com.phisher98

import android.content.SharedPreferences
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

open class Jellyfin(sharedPref: SharedPreferences? = null) : MainAPI() {
    override var name = "Jellyfin"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon
    )

    private val url = sharedPref?.getString("url", null)
    private val username = sharedPref?.getString("username", null)
    private val password = sharedPref?.getString("password", null)

    private var cachedAuth: Authparser? = null
    private var lastAuthTime: Long = 0
    private val authCacheDuration = 30 * 60 * 1000 // 30 minutes

    private fun buildAuthHeader(token: String? = null): String {
        return buildString {
            append("""MediaBrowser Client="Jellyfin Web", Device="Chrome", DeviceId="Example", Version="10.10.7"""")
            if (!token.isNullOrEmpty()) append(""", Token="$token"""")
        }
    }

    private suspend fun getValidAuth(): Authparser {
        val currentTime = System.currentTimeMillis()
        if (cachedAuth != null && (currentTime - lastAuthTime) < authCacheDuration) {
            return cachedAuth!!
        }
        val newAuth = authenticateJellyfin(username, password, url)
            ?: throw Exception("Authentication failed. Check Jellyfin credentials.")
        cachedAuth = newAuth
        lastAuthTime = currentTime
        return newAuth
    }

    private suspend fun <T> withAuthRetry(
        retries: Int = 3,
        delayMillis: Long = 300L,
        block: suspend (Authparser) -> T
    ): T {
        repeat(retries - 1) {
            try {
                return block(getValidAuth())
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                Log.d("AuthRetry", "Auth attempt ${it + 1} failed: $msg")
                if (msg.contains("401") || msg.contains("403") || msg.contains("token", ignoreCase = true)) {
                    cachedAuth = null
                    lastAuthTime = 0
                } else {
                    throw e
                }
                delay(delayMillis)
            }
        }
        return block(getValidAuth())
    }

    private suspend fun authenticateJellyfin(
        username: String?, password: String?, url: String?
    ): Authparser? {
        val requestUrl = "$url/Users/authenticatebyname"
        val headers = mapOf(
            "Authorization" to buildAuthHeader(),
            "Content-Type" to "application/json",
            "User-Agent" to "Mozilla/5.0"
        )
        val jsonInput = """{"Username":"$username","Pw":"$password"}"""
        return app.post(requestUrl, requestBody = jsonInput.toRequestBody(), headers = headers).parsedSafe()
    }

    override val mainPage = mainPageOf("HomePage" to "HomePage")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (username.isNullOrBlank() || password.isNullOrBlank() || url.isNullOrBlank()) {
            throw Exception("Please configure the extension with a valid URL, username, and password.")
        }

        return withAuthRetry { auth ->
            val headers = mapOf("Authorization" to buildAuthHeader(auth.accessToken))

            if (request.name.contains("HomePage")) {
                val api = "$url/UserViews?userId=${auth.user.id}"
                val userViews = app.get(api, headers).parsedSafe<Home>()
                    ?: throw Exception("Failed to load user views.")

                val homePageLists = userViews.items.mapNotNull { parentItem ->
                    val itemsApi = "$url/Users/${auth.user.id}/Items?ParentId=${parentItem.id}&SortOrder=Ascending"
                    val items = app.get(itemsApi, headers).parsedSafe<Home>()?.items.orEmpty()
                    if (items.isEmpty()) return@mapNotNull null
                    val list = items.map { toSearchResponseBase(it.id, it.name, it.type, auth.user.id) }
                    HomePageList(name = parentItem.name, list = list)
                }
                newHomePageResponse(homePageLists, hasNext = false)
            } else {
                throw Exception("Invalid homepage request.")
            }
        }
    }

    private fun toSearchResponseBase(id: String, name: String, typeStr: String?, userId: String): SearchResponse {
        val poster = "$url/Items/$id/Images/Primary"
        val type = when (typeStr?.lowercase()) {
            "tvshows", "shows", "series" -> TvType.TvSeries
            "movies", "movie" -> TvType.Movie
            else -> TvType.Movie
        }
        return newMovieSearchResponse(name, LoadData(name, poster, type, id, userId).toJson(), type) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String) = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return withAuthRetry { auth ->
            val headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to buildAuthHeader(auth.accessToken),
                "Content-Type" to "application/json"
            )
            val response = app.get(
                "$url/Items?userId=${auth.user.id}&limit=100&recursive=true&searchTerm=$query",
                headers = headers
            ).parsedSafe<SearchResult>()

            response?.items?.map { toSearchResponseBase(it.id, it.name, it.type, auth.user.id) } ?: emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val loadData = parseJson<LoadData>(url)
        val baseUrl = this.url ?: throw Exception("Jellyfin URL is not configured.")

        return withAuthRetry { auth ->
            val headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to buildAuthHeader(auth.accessToken),
                "Content-Type" to "application/json"
            )

            val id = loadData.id
            val userId = loadData.userid
            val type = loadData.type
            val movieApi = "$baseUrl/Users/$userId/Items/$id"
            val moviefetch = app.get(movieApi, headers).parsedSafe<MovieMetadata>()

            if (type == TvType.TvSeries) {
                val parentId = app.get(movieApi, headers).parsedSafe<SeriesInfo>()?.id
                val seasons = app.get("$baseUrl/Shows/$parentId/Seasons?userId=$userId", headers)
                    .parsedSafe<SeasonResponse>()?.items.orEmpty()
                    .filter { it.name.contains("Season", ignoreCase = true) }
                    .map { it.id to it.name }

                val episodes = seasons.flatMap { (seasonId, _) ->
                    val episoderes = app.get(
                        "$baseUrl/Shows/$parentId/Episodes?seasonId=$seasonId&userId=$userId", headers
                    ).parsedSafe<EpisodeJson>()?.items.orEmpty()

                    episoderes.map { item ->
                        val season = Regex("""\d+""").find(item.seasonName ?: "")?.value?.toIntOrNull() ?: 1
                        newEpisode(item.id) {
                            this.name = item.name
                            this.episode = item.indexNumber
                            this.season = season
                            this.posterUrl = item.getPosterUrl(baseUrl)
                        }
                    }
                }

                newTvSeriesLoadResponse(loadData.name, url, type, episodes) {
                    this.posterUrl = loadData.posterurl
                    this.plot = moviefetch?.overview
                    this.tags = moviefetch?.genres
                    addActors(moviefetch?.people?.map { it.name })
                    addImdbId(moviefetch?.providerIds?.imdb)
                    addImdbId(moviefetch?.providerIds?.tmdb)
                    addImdbUrl(moviefetch?.externalUrls?.firstOrNull { it.name.equals("IMDb", true) }?.url)
                    addTrailer(moviefetch?.remoteTrailers?.firstOrNull { it.url.contains("youtube", true) }?.url)
                }
            } else {
                newMovieLoadResponse(loadData.name, url, type, id) {
                    this.posterUrl = loadData.posterurl
                    this.plot = moviefetch?.overview
                    this.tags = moviefetch?.genres
                    addActors(moviefetch?.people?.map { it.name })
                    addImdbId(moviefetch?.providerIds?.imdb)
                    addImdbId(moviefetch?.providerIds?.tmdb)
                    addImdbUrl(moviefetch?.externalUrls?.firstOrNull { it.name.equals("IMDb", true) }?.url)
                    addTrailer(moviefetch?.remoteTrailers?.firstOrNull { it.url.contains("youtube", true) }?.url)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return withAuthRetry { auth ->
            val streamUrl = fetchPlaybackInfo(data.substringAfter("/"), auth.user.id)
            callback(newExtractorLink(name, name, streamUrl, INFER_TYPE) {
                this.quality = getQualityFromName(streamUrl)
            })
            true
        }
    }

    private suspend fun fetchPlaybackInfo(id: String, userId: String): String {
        return withAuthRetry { auth ->
            val apiUrl = "$url/Items/$id/PlaybackInfo"
            val headers = mapOf(
                "Authorization" to buildAuthHeader(auth.accessToken),
                "Content-Type" to "application/json"
            )
            val body = """
            {
                "UserId": "$userId",
                "StartTimeTicks": 0,
                "IsPlayback": true,
                "AutoOpenLiveStream": true,
                "MediaSourceId": "$id"
            }
            """.trimIndent()

            val response = retryRequest {
                app.post(apiUrl, headers, requestBody = body.toRequestBody("application/json".toMediaType()))
                    .parsedSafe<LoadURL>()
            }

            val mediaSource = response?.mediaSources?.firstOrNull()
            val httpPath = mediaSource?.path

            if (
                httpPath != null &&
                httpPath.startsWith("http", ignoreCase = true) &&
                mediaSource.supportsDirectPlay &&
                mediaSource.protocol.equals("http", ignoreCase = true)
            ) {
                val redirectUrl = app.get(httpPath, allowRedirects = false).headers["location"] ?: ""
                return@withAuthRetry if (redirectUrl.isNotEmpty()) redirectUrl else httpPath
            }

            mediaSource?.transcodingUrl?.let { transcodingUrl ->
                return@withAuthRetry if (transcodingUrl.startsWith("http", ignoreCase = true)) {
                    transcodingUrl
                } else {
                    "$url$transcodingUrl"
                }
            }

            return@withAuthRetry "$url/Videos/$id/stream.mp4?Static=true&mediaSourceId=$id"
        }
    }

    private fun EpisodeItem.getPosterUrl(baseUrl: String): String? {
        return imageTags?.primary?.let { "$baseUrl/Items/$id/Images/Primary?tag=$it" }
    }

    private suspend fun <T> retryRequest(
        times: Int = 3,
        delayMillis: Long = 300L,
        block: suspend () -> T?
    ): T? {
        repeat(times - 1) {
            try {
                val result = block()
                if (result != null) return result
            } catch (e: Exception) {
                if (!e.message.orEmpty().contains("500")) throw e
            }
            delay(delayMillis)
        }
        return try { block() } catch (e: Exception) {
            if (e.message.orEmpty().contains("500")) null else throw e
        }
    }
}
