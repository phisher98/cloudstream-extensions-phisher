package com.phisher98

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class XDMovies : MainAPI() {
    override var mainUrl              = "https://xdmovies.site"
    override var name                 = "XD Movies"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = true
    override val instantLinkLoading   = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)


    companion object
    {
        private const val simkl = "https://api.simkl.com"
        val headers = mapOf(
            "x-auth-token" to base64Decode("NzI5N3Nra2loa2Fqd25zZ2FrbGFrc2h1d2Q="),
            "x-requested-with" to "XMLHttpRequest"
        )
        private val client = OkHttpClient()
        private val gson = Gson()

        private const val CINEMETAURL = "https://cinemeta-live.strem.io"
        const val TMDBIMAGEBASEURL = "https://image.tmdb.org/t/p/w500"
        const val BGPOSTER = "https://image.tmdb.org/t/p/original"
    }

    override val mainPage = mainPageOf(
        "php/fetch_media.php?sort=timestamp" to "Latest Movies/Series",
        "php/fs.php?ott=Netflix" to "Netflix",
        "php/fs.php?ott=Amazon" to "Amazon Prime Video",
        "php/fs.php?ott=DisneyPlus" to "Disney+",
        "php/fs.php?ott=AppleTVPlus" to "Apple TV+",
        "php/fs.php?ott=HBOMax" to "HBO Max",
        "php/fs.php?ott=Hulu" to "Hulu",
        "php/fs.php?ott=Zee5" to "Zee5",
        "php/fs.php?ott=JioHotstar" to "Hotstar",
    )

    private suspend fun fetchUrl(url: String, headers: Map<String, String> = emptyMap()): String? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) response.body.string() else null
                }
            } catch (e: Exception) {
                Log.e("MainPage", "Network request failed: ${e.localizedMessage}")
                null
            }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val url = "$mainUrl/${request.data}"

        val res: List<Any>? = try {
            val responseText = fetchUrl(url, headers) ?: return null

            if (request.data.contains("fetch_media.php")) {
                gson.fromJson(responseText, Array<HomePageHome>::class.java).toList()
            } else {
                gson.fromJson(responseText, Home::class.java)?.data
            }
        } catch (e: Exception) {
            Log.e("MainPage", "Failed to parse main page: ${e.localizedMessage}")
            null
        }

        val home = res?.mapNotNull {
            when (it) {
                is Home.Data -> it.toSearchResult()
                is HomePageHome -> it.toSearchResult()
                else -> null
            }
        }.orEmpty()

        return if (home.isEmpty()) null else newHomePageResponse(request.name, home)
    }

    private fun Home.Data.toSearchResult(): SearchResponse {
        val (url, tvType) = buildApiUrlAndType(this.tmdb_id, this.type)
        val poster = TMDBIMAGEBASEURL + this.poster_path
        val year = this.release_date.substringBefore("-").toIntOrNull()
        return newMovieSearchResponse(this.title, url, tvType) {
            this.posterUrl = poster
            this.year = year
        }
    }

    private fun HomePageHome.toSearchResult(): SearchResponse {
        val (url, tvType) = buildApiUrlAndType(this.tmdbId.toInt(), this.type)
        val poster = TMDBIMAGEBASEURL + this.posterPath
        val year = this.releaseDate.substringBefore("-").toIntOrNull()
        return newMovieSearchResponse(this.title, url, tvType) {
            this.posterUrl = poster
            this.year = year
        }
    }

    private fun SearchData.SearchDataItem.toSearchResult(): SearchResponse? {
        if (title.isEmpty() || tmdb_id == 0) return null
        val (url, tvType) = buildApiUrlAndType(tmdb_id, type)
        return newMovieSearchResponse(title, url, tvType) {
            this.posterUrl = TMDBIMAGEBASEURL + poster
        }
    }

    private fun buildApiUrlAndType(tmdbId: Int, type: String): Pair<String, TvType> {
        val apiSlug = if (type.equals("tv", ignoreCase = true)) "abc456" else "xyz123"
        val tvType = if (type.equals("tv", ignoreCase = true)) TvType.TvSeries else TvType.Movie
        val url = "$mainUrl/api/$apiSlug?tmdb_id=$tmdbId"
        return url to tvType
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchData = app.get("$mainUrl/php/search_api.php?query=$query&fuzzy=true", headers)
            .parsedSafe<SearchData>() ?: return null
        val results = searchData.mapNotNull { it.toSearchResult() }
        return results.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val resString = try {
            if (url.contains("details.html")) {
                val type = url.substringAfterLast("&type=")
                val apiSlug = if (type.equals("tv", ignoreCase = true)) "abc456" else "xyz123"
                val id = url.substringAfterLast("id=").substringBefore("&")
                val apiUrl = "$mainUrl/api/$apiSlug?tmdb_id=$id"
                fetchUrl(apiUrl, headers) ?: throw Exception("Failed to fetch API data")
            } else {
                fetchUrl(url, headers) ?: throw Exception("Failed to fetch page data")
            }
        } catch (e: Exception) {
            Log.e("LoadFunction", "Failed to get data: ${e.localizedMessage}")
            throw e
        }

        val json = JSONObject(resString)

        val title = json.optString("title").takeIf { it.isNotBlank() } ?: json.optString("name").orEmpty()
        val poster = json.optString("poster_path").takeIf { it.isNotBlank() }?.let { TMDBIMAGEBASEURL + it } ?: ""
        val backgroundPoster = json.optString("backdrop_path").takeIf { it.isNotBlank() }?.let { BGPOSTER + it } ?: ""
        val tags = json.optString("genres").takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() } ?: emptyList()
        val actors = json.optString("cast").takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() } ?: emptyList()
        val releaseDate = json.optString("release_date")
        val firstAirDate = json.optString("firstAirDate").ifEmpty { json.optString("first_air_date") }
        val year = (releaseDate.ifEmpty { firstAirDate }).takeIf { it.isNotBlank() }?.substringBefore("-")?.toIntOrNull()
        val description = json.optString("overview")
        val rating = Score.from10(json.optString("rating"))
        val totalEpisodes = json.optInt("total_episodes", -1)
        val tvType = if (totalEpisodes > 0) TvType.TvSeries else TvType.Movie
        val tvTypeslug = if (totalEpisodes > 0) "series" else "movie"
        val tmdbtvTypeslug = if (totalEpisodes > 0) "tv" else "movie"
        val tmdbId = url.substringAfter("id=").substringBefore("&")
        val href = "$mainUrl/details.html?id=$tmdbId&type=$tmdbtvTypeslug"
        val source = json.optString("source").takeIf { it.isNotBlank() } ?: json.optString("movie_source").orEmpty()

        val downloadLinks = mutableListOf<String>()
        json.optJSONArray("download_links")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.getJSONObject(i).optString("download_link").takeIf { it.isNotBlank() }?.let { downloadLinks.add(it) }
            }
        }
        val downloadLinksJson = JSONArray(downloadLinks).toString()

        val tmdbResText = fetchUrl("https://orange-voice-abcf.phisher16.workers.dev/$tmdbtvTypeslug/$tmdbId/external_ids?api_key=1865f43a0549ca50d341dd9ab8b29f49")
        val tmdbRes = tmdbResText?.let { gson.fromJson(it, IMDB::class.java) }
        val imdbId = tmdbRes?.imdbId

        val simklid = runCatching {
            tmdbRes?.imdbId?.takeIf { it.isNotBlank() }?.let { imdb ->
                val path = if (tvType == TvType.Movie) "movies" else "tv"
                val json = JSONObject(app.get("$simkl/$path/$imdb").text)
                json.optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
            }
        }.getOrNull()

        val responseData = imdbId?.takeIf { it.isNotBlank() && it != "0" }?.let {
            val jsonResponse = fetchUrl("$CINEMETAURL/meta/$tvTypeslug/$it.json") ?: return@let null
            if (jsonResponse.startsWith("{")) gson.fromJson(jsonResponse, ResponseData::class.java) else null
        }

        if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            val downloadData = json.optJSONObject("download_data")
            val seasonsArray = downloadData?.optJSONArray("seasons")

            if (seasonsArray != null) {
                for (s in 0 until seasonsArray.length()) {
                    val seasonObj = seasonsArray.optJSONObject(s) ?: continue
                    val seasonNum = seasonObj.optInt("season_num", 1)
                    val episodesArray = seasonObj.optJSONArray("episodes")
                    val packsArray = seasonObj.optJSONArray("packs")

                    val tmdbSeasonResText = fetchUrl(
                        "https://orange-voice-abcf.phisher16.workers.dev/tv/$tmdbId/season/$seasonNum?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US"
                    )
                    val tmdbSeasonRes = tmdbSeasonResText?.let { gson.fromJson(it, TMDBRes::class.java) }

                    // Process episodes array or fallback to packs
                    if (episodesArray != null && episodesArray.length() > 0) {
                        for (e in 0 until episodesArray.length()) {
                            val episodeObj = episodesArray.optJSONObject(e) ?: continue
                            val epNum = episodeObj.optInt("episode_number", e + 1)
                            val versionsArray = episodeObj.optJSONArray("versions") ?: continue

                            val allLinks = mutableListOf<String>()
                            val versionNames = mutableListOf<String>()
                            for (v in 0 until versionsArray.length()) {
                                val ver = versionsArray.optJSONObject(v) ?: continue
                                ver.optString("download_link").takeIf { it.isNotBlank() }?.let { allLinks.add(it) }
                                val resolution = ver.optString("resolution").ifBlank { "unknown" }
                                val codec = ver.optString("codec").ifBlank { "" }
                                val size = ver.optString("size").ifBlank { "" }
                                versionNames += "$resolution $codec $size".trim()
                            }
                            if (allLinks.isEmpty()) continue

                            val tmdbEpisode = tmdbSeasonRes?.episodes?.find { it.episodeNumber == epNum }
                            val epName = "S${seasonNum}E${epNum} (${versionNames.joinToString(" / ")})"
                            val info = responseData?.meta?.videos?.find { it.season == seasonNum && it.episode == epNum }
                            episodes += newEpisode(allLinks.toJson()) {
                                this.name = tmdbEpisode?.name ?: info?.name ?: epName
                                this.season = seasonNum
                                this.episode = epNum
                                this.posterUrl = tmdbEpisode?.stillPath?.let { TMDBIMAGEBASEURL + it }
                                this.description = tmdbEpisode?.overview ?: info?.overview
                                this.score = Score.from10(tmdbEpisode?.voteAverage)
                                this.addDate(tmdbEpisode?.airDate)
                            }
                        }
                    } else if (packsArray != null && packsArray.length() > 0) {
                        for (p in 0 until packsArray.length()) {
                            val pack = packsArray.optJSONObject(p) ?: continue
                            val downloadLink = pack.optString("download_link").takeIf { it.isNotBlank() } ?: continue
                            val resolution = pack.optString("resolution").ifBlank { "unknown" }
                            val size = pack.optString("size").ifBlank { "" }
                            val title = pack.optString("custom_title").ifBlank { "S${seasonNum}E${p + 1}" }

                            episodes += newEpisode(listOf(downloadLink).toJson()) {
                                this.name = title + " ($resolution $size)".trim()
                                this.season = seasonNum
                                this.episode = p + 1
                            }
                        }
                    }
                }
            }

            return newTvSeriesLoadResponse(title, href, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating
                this.contentRating = source
                addActors(actors)
                addImdbId(imdbId)
                addSimklId(simklid)
            }
        } else if (tvType == TvType.Anime) {
            val episodes = mutableListOf<Episode>()
            val downloadData = json.optJSONObject("download_data")
            val seasonsArray = downloadData?.optJSONArray("seasons")

            if (seasonsArray != null) {
                for (s in 0 until seasonsArray.length()) {
                    val seasonObj = seasonsArray.optJSONObject(s) ?: continue
                    val seasonNum = seasonObj.optInt("season_num", 1)
                    val episodesArray = seasonObj.optJSONArray("episodes")
                    val packsArray = seasonObj.optJSONArray("packs")

                    val tmdbSeasonResText = fetchUrl(
                        "https://orange-voice-abcf.phisher16.workers.dev/tv/$tmdbId/season/$seasonNum?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US"
                    )
                    val tmdbSeasonRes =
                        tmdbSeasonResText?.let { gson.fromJson(it, TMDBRes::class.java) }

                    // Process episodes array or fallback to packs
                    if (episodesArray != null && episodesArray.length() > 0) {
                        for (e in 0 until episodesArray.length()) {
                            val episodeObj = episodesArray.optJSONObject(e) ?: continue
                            val epNum = episodeObj.optInt("episode_number", e + 1)
                            val versionsArray = episodeObj.optJSONArray("versions") ?: continue

                            val allLinks = mutableListOf<String>()
                            val versionNames = mutableListOf<String>()
                            for (v in 0 until versionsArray.length()) {
                                val ver = versionsArray.optJSONObject(v) ?: continue
                                ver.optString("download_link").takeIf { it.isNotBlank() }
                                    ?.let { allLinks.add(it) }
                                val resolution = ver.optString("resolution").ifBlank { "unknown" }
                                val codec = ver.optString("codec").ifBlank { "" }
                                val size = ver.optString("size").ifBlank { "" }
                                versionNames += "$resolution $codec $size".trim()
                            }
                            if (allLinks.isEmpty()) continue

                            val tmdbEpisode =
                                tmdbSeasonRes?.episodes?.find { it.episodeNumber == epNum }
                            val epName =
                                "S${seasonNum}E${epNum} (${versionNames.joinToString(" / ")})"
                            val info =
                                responseData?.meta?.videos?.find { it.season == seasonNum && it.episode == epNum }
                            episodes += newEpisode(allLinks.toJson()) {
                                this.name = tmdbEpisode?.name ?: info?.name ?: epName
                                this.season = seasonNum
                                this.episode = epNum
                                this.posterUrl =
                                    tmdbEpisode?.stillPath?.let { TMDBIMAGEBASEURL + it }
                                this.description = tmdbEpisode?.overview ?: info?.overview
                                this.score = Score.from10(tmdbEpisode?.voteAverage)
                                this.addDate(tmdbEpisode?.airDate)
                            }
                        }
                    } else if (packsArray != null && packsArray.length() > 0) {
                        for (p in 0 until packsArray.length()) {
                            val pack = packsArray.optJSONObject(p) ?: continue
                            val downloadLink =
                                pack.optString("download_link").takeIf { it.isNotBlank() }
                                    ?: continue
                            val resolution = pack.optString("resolution").ifBlank { "unknown" }
                            val size = pack.optString("size").ifBlank { "" }
                            val title =
                                pack.optString("custom_title").ifBlank { "S${seasonNum}E${p + 1}" }

                            episodes += newEpisode(listOf(downloadLink).toJson()) {
                                this.name = title + " ($resolution $size)".trim()
                                this.season = seasonNum
                                this.episode = p + 1
                            }
                        }
                    }
                }
            }

            return newTvSeriesLoadResponse(title, href, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating
                this.contentRating = source
                addActors(actors)
                addImdbId(imdbId)
                addSimklId(simklid)
            }
        } else {
            return newMovieLoadResponse(title, href, TvType.Movie, downloadLinksJson) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating
                this.contentRating = source
                addActors(actors)
                addImdbId(imdbId)
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
        if (data.isBlank()) return false

        val links = try {
            val type = object : TypeToken<ArrayList<String>>() {}.type
            Gson().fromJson(data, type)
        } catch (_: Exception) {
            null
        } ?: arrayListOf(data)

        var success = false
        for (rawLink in links) {
            val normalizedLink = rawLink.trim()

            if (normalizedLink.isEmpty()) continue

            try {
                if (normalizedLink.contains("hubcloud", ignoreCase = true)) {
                    HubCloud().getUrl(normalizedLink, "HubCloud", subtitleCallback, callback)
                } else {
                    loadExtractor(normalizedLink, name, subtitleCallback, callback)
                }
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return success
    }
}