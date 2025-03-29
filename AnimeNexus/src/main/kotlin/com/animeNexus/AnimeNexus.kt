package com.animeNexus

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import com.lagradost.cloudstream3.utils.Qualities



class AnimeNexus : MainAPI() {
    override var mainUrl = "https://anime.nexus"
    override var name = "AnimeNexus"
    override val hasMainPage = true
    override val hasQuickSearch =true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "api/anime/shows?sortBy=name+asc&hasVideos=true&page=1&includes[]=poster&status=Currently+Airing&type=TV" to "Currently Airing",
        "api/anime/recent" to "Recent",
        "api/anime/latest" to "Latest",
        "api/anime/shows?sortBy=name+asc&hasVideos=true&page=1&includes[]=poster&type=Movie" to "Movie"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val jsonResponse = app.get("$mainUrl/${request.data}").text
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
        val jsonResponse = app.get("$mainUrl/api/anime/shows?search=$query&sortBy=name+asc&page=1&includes%5B%5D=poster").text
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
            app.get("$mainUrl/api/anime/details/episodes?id=$animeId").text,
            AnimeNexusLoad::class.java
        )

        val title = document.select("span.block.font-black.leading-none.text-base.lg\\:text-2xl.xl\\:text-4xl.uppercase").text()
        val poster = document.select("div.relative.flex-1 > div.absolute.bg-cover")
            .attr("style").substringAfter("(").substringBefore(")")

        val lastPage = response.meta?.lastPage
        val perPage = response.meta?.perPage

        val allEpisodes = mutableListOf<Episode>()

        // Loop through all pages and collect episodes
        (1..lastPage!!).forEach { page ->
            val pageResponse = Gson().fromJson(
                app.get("$mainUrl/api/anime/details/episodes?id=$animeId&page=$page&perPage=$perPage&order=asc").text,
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
        val api = "$mainUrl/api/anime/details/episode/stream?id=$episodeId"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
            "Origin" to "https://anime.nexus",
            "Referer" to "https://anime.nexus/"
        )

        val jsonResponse = app.get(api, headers = headers).text
        val response = Gson().fromJson(jsonResponse, Stream::class.java)
        val m3u8 = response?.data?.hls.orEmpty()
        val mpd = response?.data?.mpd.orEmpty()
        val subtitles = response?.data?.subtitles.orEmpty()

        if (m3u8.isNotBlank()) {
            callback(
                ExtractorLink(
                    name = name,
                    source = name,
                    url = m3u8,
                    referer = "https://anime.nexus/",
                    quality = Qualities.P1080.value,
                    type = INFER_TYPE,
                    headers = headers
                )
            )
        }

        if (mpd.isNotBlank()) {
            callback(
                ExtractorLink(
                    name = "$name DASH",
                    source = "$name DASH",
                    url = mpd,
                    referer = "https://anime.nexus/",
                    quality = Qualities.P1080.value,
                    type = INFER_TYPE,
                    headers = headers
                )
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
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url.toString()

            val modifiedRequest = if (originalUrl.endsWith(".m3u8") || originalUrl.endsWith(".mp4") || originalUrl.endsWith(".m4s")) {
                originalRequest.newBuilder()
                    .url("$originalUrl?token=3486384170_1c4c10a8d20c5a7789113ab1f8a8938dfac53690")
                    .build()
            } else {
                originalRequest
            }

            chain.proceed(modifiedRequest)
        }
    }


}
