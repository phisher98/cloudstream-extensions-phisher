package com.Phisher98

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
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
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONArray
import org.json.JSONObject

class XDMovies : MainAPI() {
    override var mainUrl              = "https://xdmovies.site"
    override var name                 = "XD Movies"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = true
    override val instantLinkLoading   = true
    override val supportedTypes       = setOf(TvType.Movie)


    companion object
    {
        val headers = mapOf(
            "x-auth-token" to base64Decode("NzI5N3Nra2loa2Fqd25zZ2FrbGFrc2h1d2Q="),
            "x-requested-with" to "XMLHttpRequest"
        )

        private const val cinemeta_url = "https://cinemeta-live.strem.io"
        private val tmdbAPI = "https://94c8cb9f702d-tmdb-addon.baby-beamup.club"
        val tmdbImageBaseUrl = "https://image.tmdb.org/t/p/w500"
        val backgroundPoster = "https://image.tmdb.org/t/p/original"
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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val url = "$mainUrl/${request.data}"

        val res: List<Any>? = if (request.data.contains("fetch_media.php")) {
            val json = app.get(url, headers).text
            Gson().fromJson(json, Array<HomePageHome>::class.java).toList()
        } else {
            app.get(url, headers).parsedSafe<Home>()?.data
        }

        val home = res?.mapNotNull {
            when (it) {
                is Home.Data -> it.toSearchResult()
                is HomePageHome -> it.toSearchResult()
                else -> null
            }
        } ?: emptyList()

        if (home.isEmpty()) return null
        return newHomePageResponse(request.name, home)
    }

    private fun Home.Data.toSearchResult(): SearchResponse? {
        val title = this.title
        val poster = tmdbImageBaseUrl+this.poster_path
        val apiSlug = if (this.type.equals("tv", ignoreCase = true)) "abc456" else "xyz123"
        val url = "$mainUrl/api/$apiSlug?tmdb_id=${this.tmdb_id}"
        val year= this.release_date.substringBefore("-").toIntOrNull()
        val tvType = if (this.type.equals("tv", ignoreCase = true)) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, url, tvType) {
                this.posterUrl = poster
                this.year = year
        }
    }
    private fun HomePageHome.toSearchResult(): SearchResponse? {
        val title = this.title
        val poster = tmdbImageBaseUrl+this.posterPath
        val apiSlug = if (this.type.equals("tv", ignoreCase = true)) "abc456" else "xyz123"
        val url = "$mainUrl/api/$apiSlug?tmdb_id=${this.tmdbId}"
        val year= this.releaseDate.substringBefore("-").toIntOrNull()
        val tvType = if (this.type.equals("tv", ignoreCase = true)) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, url, tvType) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchData = app.get("$mainUrl/php/search_api.php?query=$query&fuzzy=true", headers).parsedSafe<SearchData>() ?: return null // returns null if parsing fails
        val results = searchData.mapNotNull { it.toSearchResult() }
        return results.toNewSearchResponseList()
    }

    private fun SearchData.SearchDataItem.toSearchResult(): SearchResponse? {
        if (title.isEmpty() || tmdb_id == 0) return null
        val apiSlug = if (this.type.equals("tv", ignoreCase = true)) "abc456" else "xyz123"
        val url = "$mainUrl/api/$apiSlug?tmdb_id=${this.tmdb_id}"
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = tmdbImageBaseUrl+poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val resString = app.get(url, headers).text
        val json = JSONObject(resString)
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: json.optString("name").orEmpty()
        val poster = json.optString("poster_path").takeIf { it.isNotBlank() }?.let { tmdbImageBaseUrl + it } ?: ""
        val backgroundposter = json.optString("backdrop_path").takeIf { it.isNotBlank() }?.let { backgroundPoster + it } ?: ""
        val tags = json.optString("genres").takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() } ?: emptyList()
        val actors = json.optString("cast").takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() } ?: emptyList()
        val releaseDate = json.optString("release_date")
        val source = json.optString("source").takeIf { it.isNotBlank() } ?: json.optString("movie_source").orEmpty()
        val firstAirDate = json.optString("firstAirDate").ifEmpty { json.optString("first_air_date") }
        val year = (releaseDate.ifEmpty { firstAirDate }).takeIf { it.isNotBlank() }?.substringBefore("-")?.toIntOrNull()
        val description = json.optString("overview")
        val rating = Score.from10(json.optString("rating"))
        val totalEpisodes = json.optInt("total_episodes", -1)
        val tvType = if (totalEpisodes > 0) TvType.TvSeries else TvType.Movie
        val tvTypeslug = if (totalEpisodes > 0) "series" else "movie"
        //val tmdbtvTypeslug = if (totalEpisodes > 0) "tv" else "movie"
        val tmdbid = url.substringAfterLast("=")
        //val href= "$mainUrl/details.html?id=$tmdbid&type=$tmdbtvTypeslug"


        val downloadLinks = mutableListOf<String>()
        val downloadsArray = json.optJSONArray("download_links")
        if (downloadsArray != null) {
            for (i in 0 until downloadsArray.length()) {
                val link = downloadsArray.getJSONObject(i).optString("download_link").takeIf { it.isNotBlank() } ?: continue
                downloadLinks.add(link)
            }
        }

        val downloadLinksJson = JSONArray(downloadLinks).toString()
        val tmdbres = app.get(
            "$tmdbAPI/meta/$tvTypeslug/tmdb:$tmdbid.json"
        ).parsedSafe<IMDB>()

        val imdbId = tmdbres?.imdbId

        val gson = Gson()
        val responseData = imdbId
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?.let {
                val jsonResponse = app.get("$cinemeta_url/meta/$tvTypeslug/$it.json").text
                if (jsonResponse.startsWith("{")) gson.fromJson(jsonResponse, ResponseData::class.java) else null }

        return if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()

            val downloadData = json.optJSONObject("download_data")
            val seasonsArray = downloadData?.optJSONArray("seasons")

            if (seasonsArray != null) {
                for (s in 0 until seasonsArray.length()) {
                    val seasonObj = seasonsArray.optJSONObject(s) ?: continue
                    val seasonNum = seasonObj.optInt("season_num", 1)
                    val episodesArray = seasonObj.optJSONArray("episodes") ?: continue

                    val tmdbRes = app.get(
                        "https://api.themoviedb.org/3/tv/$tmdbid/season/$seasonNum?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US"
                    ).parsedSafe<TMDBRes>()

                    Log.d(
                        "Phisher",
                        "Fetched TMDb season=$seasonNum → episodes=${tmdbRes?.episodes?.size ?: 0}"
                    )
                    for (e in 0 until episodesArray.length()) {
                        val episodeObj = episodesArray.optJSONObject(e) ?: continue
                        val epNum = episodeObj.optInt("episode_number", e + 1)

                        val versionsArray = episodeObj.optJSONArray("versions")
                        if (versionsArray == null || versionsArray.length() == 0) continue

                        val allLinks = mutableListOf<String>()
                        val versionNames = mutableListOf<String>()

                        for (v in 0 until versionsArray.length()) {
                            val ver = versionsArray.optJSONObject(v) ?: continue
                            val href = ver.optString("download_link").takeIf { it.isNotBlank() } ?: continue
                            allLinks += href

                            val resolution = ver.optString("resolution").ifBlank { "unknown" }
                            val codec = ver.optString("codec").ifBlank { "" }
                            val size = ver.optString("size").ifBlank { "" }
                            versionNames += "$resolution $codec $size".trim()
                        }

                        if (allLinks.isEmpty()) continue
                        val tmdbEpisode = tmdbRes?.episodes?.find { it.episodeNumber == epNum }
                        Log.d("Phisher", "Fetching TMDB season $seasonNum for tmdbId=$tmdbid")

                        val epName = "S${seasonNum}E${epNum} (${versionNames.joinToString(" / ")})"
                        val info = responseData?.meta?.videos?.find { it.season == seasonNum && it.episode == epNum }

                        episodes += newEpisode(allLinks.toJson()) {
                            this.name = tmdbEpisode?.name ?: info?.name ?: epName
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = (tmdbImageBaseUrl + tmdbEpisode?.stillPath)
                            this.description = tmdbEpisode?.overview ?: info?.overview
                            this.score = Score.from10(tmdbEpisode?.voteAverage)
                            this.addDate(tmdbEpisode?.airDate)
                        }
                    }
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating
                this.contentRating = source
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, downloadLinksJson) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating
                this.contentRating = source
                addActors(actors)
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
        val parsedList: List<String>? = tryParseJson<List<String>>(data)
        val fallbackList: List<String> = data
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() }
        val links = (parsedList?.map { it.trim() }?.filter { it.isNotBlank() } ?: fallbackList).distinct()
        if (links.isEmpty()) return false

        for (link in links) {
            if (link.contains("hubcloud", ignoreCase = true)) {
                HubCloud().getUrl(link, "HubCloud", subtitleCallback, callback)
            } else {
                loadExtractor(link, name, subtitleCallback, callback)
            }
            Log.d("Phisher", link)
        }
        return true
    }
}
