package com.animeNexus

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.UUID


class AnimeNexus : MainAPI() {
    override var mainUrl = "https://anime.nexus"
    private val api="https://api.anime.nexus"
    override var name = "AnimeNexus"
    override val hasMainPage = true
    override val hasQuickSearch =true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object
    {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        )
    }

    override val mainPage = mainPageOf(
        "/api/anime/latest/all" to "Latest",
        "api/anime/shows?sortBy=name+asc&hasVideos=true&page=1&includes%5B%5D=poster&includes%5B%5D=genres&status%5B%5D=Currently+Airing" to "Currently Airing",
        "api/anime/shows?sortBy=name+asc&hasVideos=false&page=1&includes%5B%5D=poster&includes%5B%5D=genres&type%5B%5D=Movie" to "Movie",
        "api/anime/shows?sortBy=name+asc&hasVideos=true&page=1&includes%5B%5D=poster&includes%5B%5D=genres&status%5B%5D=Finished+Airing" to "Finished Airing"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val jsonResponse = app.get("$api/${request.data}").text
        val response: AnimeNexusHome? = try {
            Gson().fromJson(jsonResponse, object : TypeToken<AnimeNexusHome>() {}.type)
        } catch (e: Exception) {
            null
        }
        val home = response?.data?.map { it.toSearchResult() }
        return newHomePageResponse(request.name, home ?: emptyList())
    }

    private fun Daum.toSearchResult(): SearchResponse {
        val title = this.name
        val href = "$mainUrl/series/${this.id}/${this.slug}"
        val posterUrl = this.poster?.original
        return newAnimeSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val jsonResponse = app.get("$api/api/anime/shows?search=$query&sortBy=name+asc&page=1&includes%5B%5D=poster", headers = headers).text
        val response: AnimeNexusHome? = try {
            Gson().fromJson(jsonResponse, object : TypeToken<AnimeNexusHome>() {}.type)
        } catch (e: Exception) {
            null
        }
        return response?.data?.map { it.toSearchResult() }
    }



    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val animeId = url.substringAfter("series/").substringBefore("/") // Extract anime ID

        val response = Gson().fromJson(
            app.get("$api/api/anime/details/episodes?id=$animeId").text,
            AnimeNexusLoad::class.java
        )

        val title = document.select("div.absolute > picture.relative img").attr("alt")
        val imageElement = document.select("div.absolute > picture.relative img").first()
        val srcset = imageElement?.attr("srcset")

        val poster = srcset
            ?.split(",")
            ?.map { it.trim() }
            ?.find { it.endsWith("3840w") }
            ?.substringBefore("3840w")
            ?.trim()
            ?.replace(".avif", ".jpg")

        val lastPage = response.meta?.lastPage
        val perPage = response.meta?.perPage
        val description = document.select("div.relative p").text()
        val allEpisodes = mutableListOf<Episode>()
        // Loop through all pages and collect episodes
        (1..lastPage!!).forEach { page ->
            val pageResponse = Gson().fromJson(
                app.get("$api/api/anime/details/episodes?id=$animeId&page=$page&perPage=$perPage&order=asc").text,
                AnimeNexusLoad::class.java
            )

            allEpisodes += pageResponse.data.map { episode ->
                newEpisode(episode.id) {
                    name = episode.title?.ifEmpty { "Episode ${episode.number}" }
                    posterUrl = episode.image?.original
                }
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            plot=description
            addEpisodes(DubStatus.Subbed, allEpisodes)
        }
    }



    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodeId = data.substringAfterLast("/")
        val api = "$api/api/anime/details/episode/stream?id=$episodeId"
        val jsonResponse = app.get(api, headers = headers).text
        val response = Gson().fromJson(jsonResponse, Stream::class.java)
        val id=response.data.next.id
        val m3u8 = response?.data?.hls.orEmpty()

        val mpd = response?.data?.mpd.orEmpty()
        val subtitles = response?.data?.subtitles.orEmpty()
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
            "Origin" to "https://anime.nexus",
            "Referer" to "https://anime.nexus/",
            "Videoid" to id
        )

        if (m3u8.isNotBlank()) {
            callback(
                newExtractorLink(
                    name,
                    name,
                    m3u8,
                    INFER_TYPE
                )
                {
                    this.referer="https://anime.nexus/"
                    this.headers=headers
                    this.quality=Qualities.P1080.value
                }
            )
        }

        if (mpd.isNotBlank()) {
            callback(
                newExtractorLink(
                    "$name DASH",
                    "$name DASH",
                    mpd,
                    INFER_TYPE
                )
                {
                    this.referer="https://anime.nexus/"
                    this.headers=headers
                    this.quality=Qualities.P1080.value
                }
            )
        }

        subtitles.forEach { subtitle ->
            subtitleCallback(
                SubtitleFile(
                    subtitle.label,
                    subtitle.src
                )
            )
        }

        return m3u8.isNotBlank()
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val fingerprint = UUID.randomUUID().toString()
            val sessionId = "anon_" + UUID.randomUUID().toString()

            val originalRequest = chain.request()
            val originalUrl = originalRequest.url.toString()
            val videoId = originalRequest.header("Videoid") ?: ""

            val isManifest = originalUrl.contains(".m3u8", ignoreCase = true)
            val isSegment = listOf(".ts", ".m4s", ".mp4", ".mkv").any {
                originalUrl.contains(it, ignoreCase = true)
            }

            fun fetchToken(requestType: String, segmentPath: String? = null): String? {
                return try {
                    val client = OkHttpClient()

                    val json = JSONObject().apply {
                        put("videoId", videoId)
                        put("m3u8Url", originalUrl)
                        put("requestType", requestType)
                        put("sessionId", sessionId)
                        put("fingerprint", fingerprint)
                        if (requestType == "segment" && segmentPath != null) {
                            put("segmentPath", segmentPath)
                        }
                    }

                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url("https://nexus.phisher98.workers.dev/get-token")
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return null
                        val json1 = JSONObject(response.body.string())
                        return json1.optString("token")
                    }
                } catch (e: Exception) {
                    Log.e("TokenInterceptor", "Token fetch failed: ${e.message}")
                    null
                }
            }

            fun buildModifiedRequest(token: String, requestType: String, segmentPath: String? = null): Request {
                val separator = if (originalUrl.contains("?")) "&" else "?"
                val queryParams = buildString {
                    append("token=$token&requestType=$requestType&sessionId=$sessionId")
                    if (requestType == "segment" && segmentPath != null) {
                        append("&segmentPath=$segmentPath")
                    }
                }

                val newUrl = "$originalUrl$separator$queryParams"
                Log.d("TokenInterceptor", "Modified URL: $newUrl")

                return originalRequest.newBuilder()
                    .url(newUrl)
                    .header("X-Session-ID", sessionId)
                    .header("X-Video-UUID", videoId)
                    .header("X-Client-Fingerprint", fingerprint)
                    .removeHeader("Videoid")
                    .build()
            }

            fun generateTokenizedRequest(): Request {
                val requestType = when {
                    isManifest -> "manifest"
                    isSegment -> "segment"
                    else -> return originalRequest
                }

                val segmentPath = if (requestType == "segment") URL(originalUrl).path else null
                val token = fetchToken(requestType, segmentPath)
                return if (!token.isNullOrBlank()) {
                    buildModifiedRequest(token, requestType, segmentPath)
                } else {
                    originalRequest
                }
            }

            var request = generateTokenizedRequest()
            var response = chain.proceed(request)

            val isTokenInvalid = response.peekBody(Long.MAX_VALUE).string()
                .contains("Token validation failed", ignoreCase = true)

            val isHttpFailure = !response.isSuccessful

            if (isTokenInvalid || isHttpFailure) {
                response.close()
                Log.w("TokenInterceptor", "Retrying request with new token due to failed response or token error.")
                request = generateTokenizedRequest()
                response = chain.proceed(request)
            }

            return@Interceptor response
        }
    }

}

