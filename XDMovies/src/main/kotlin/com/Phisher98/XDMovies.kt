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

        private const val cinemeta_url = "https://v3-cinemeta.strem.io/meta"
        val tmdbImageBaseUrl = "https://image.tmdb.org/t/p/w500"
        val backgroundPoster = "https://image.tmdb.org/t/p/original"
    }

    override val mainPage = mainPageOf(
        "/php/fs.php?ott=Netflix" to "Netflix",
        "/php/fs.php?ott=Amazon" to "Amazon Prime Video",
        "/php/fs.php?ott=DisneyPlus" to "Disney+",
        "/php/fs.php?ott=AppleTVPlus" to "Apple TV+",
        "/php/fs.php?ott=HBOMax" to "HBO Max",
        "/php/fs.php?ott=Hulu" to "Hulu",
        "/php/fs.php?ott=ParamountPlus" to "Paramount+",
        "/php/fs.php?ott=Peacock" to "Peacock",
        "/php/fs.php?ott=SonyLiv" to "SonyLIV",
        "/php/fs.php?ott=Zee5" to "Zee5",
        "/php/fs.php?ott=JioHotstar" to "Hotstar",
        "/php/fs.php?ott=Crunchyroll" to "Crunchyroll",
        "/php/fs.php?ott=Viki" to "Viki",
        "/php/fs.php?ott=YouTube" to "YouTube",
        "/php/fs.php?ott=Mubi" to "Mubi"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val res = app.get("$mainUrl/${request.data}",headers).parsedSafe<Home>()
        val home = res?.data?.mapNotNull { it.toSearchResult() } ?: emptyList()
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

        val downloadLinks = mutableListOf<String>()
        val downloadsArray = json.optJSONArray("download_links")
        if (downloadsArray != null) {
            for (i in 0 until downloadsArray.length()) {
                val link = downloadsArray.getJSONObject(i).optString("download_link").takeIf { it.isNotBlank() } ?: continue
                downloadLinks.add(link)
            }
        }

        val downloadLinksJson = JSONArray(downloadLinks).toString()
        val imdbId = app.get(
            "https://api.themoviedb.org/3/movie/${url.substringAfterLast("=")}/external_ids?api_key=1865f43a0549ca50d341dd9ab8b29f49"
        ).parsedSafe<IMDB>()?.imdbId
        val gson = Gson()
        val responseData = imdbId
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?.let {
                val jsonResponse = app.get("$cinemeta_url/$tvTypeslug/$it.json").text
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

                        val epName = "S${seasonNum}E${epNum} (${versionNames.joinToString(" / ")})"
                        val info = responseData?.meta?.videos?.find { it.season == seasonNum && it.episode == epNum }
                        episodes += newEpisode(allLinks.toJson()) {
                            this.name = info?.name ?: epName
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = info?.thumbnail
                            this.description = info?.overview
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
        try {
            val jsonArray = JSONArray(data)
            for (i in 0 until jsonArray.length()) {
                val link = jsonArray.optString(i).takeIf { it.isNotBlank() } ?: continue
                if (link.contains("hubcloud")) {
                    HubCloud().getUrl(link, "HubDrive", subtitleCallback, callback)
                } else loadExtractor(link,name,subtitleCallback,callback)
                Log.d("Phisher", link)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        return true
    }

}
