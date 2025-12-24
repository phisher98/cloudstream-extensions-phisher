package com.MovieBlast

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
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
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject

class MovieBlast : MainAPI() {
    override var mainUrl = base64Decode("aHR0cHM6Ly9hcHAuY2xvdWQtbWIueHl6")
    override var name = "MovieBlast"
    override val hasMainPage = true
    override var lang = "te"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    companion object {
        val headers = mapOf(
           "user-agent" to "okhttp/5.0.0-alpha.6"
        )
        val token = base64Decode("amR2aGhqdjI1NXZnaGhnZGh2ZmNoMjU2NTY1NmpoZGNnaGZkZg==")

    }

    private fun HomeDaum.isSeries(): Boolean {
        return when (type?.lowercase()) {
            "series", "serie", "tv", "show" -> true
            else -> contentType?.lowercase() == "series"
        }
    }

    private fun MediaDetailResponse.isSeries(): Boolean {
        return seasons.isNotEmpty()
    }


    override val mainPage = mainPageOf(
        "api/genres/pinned/all" to "Latest",
        "api/genres/trending/all" to "Trending",
        "api/genres/new/all" to "Recently Added",
        "api/genres/popularmovies/all" to "Popular • Movies",
        "api/genres/popularseries/all" to "Popular • Series",
        "api/media/seriesEpisodesAll" to "Latest • Series",
        "api/genres/recommended/all" to "Recommended",
        "api/genres/media/names/New%20HD%20Released" to "New HD Releases"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val res = app.get(
            "$mainUrl/${request.data}/$token?page=$page",
            headers
        ).parsedSafe<Home>()

        val items = res?.data?.asSequence()?.mapNotNull { it.toSearchResultSafe() }
            ?.distinctBy { it.url.ifBlank { "${it.name}-${it.posterUrl}" } }?.toList()
            ?: emptyList()

        return newHomePageResponse(request.name, items)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val safeQuery = query.trim().replace(" ", "%20")

        val res = app
            .get("$mainUrl/api/search/$safeQuery/$token")
            .parsedSafe<SearchRoot>()
            ?: return emptyList()

        return res.search.map { item ->
            val isSeries = item.type.contains("serie", ignoreCase = true)

            val path = if (isSeries) "series/show" else "media/detail"
            val href = "$mainUrl/api/$path/${item.id}/$token"

            newMovieSearchResponse(
                item.name,
                href,
                if (isSeries) TvType.TvSeries else TvType.Movie
            ) {
                posterUrl = item.posterPath
            }
        }
    }




    private fun HomeDaum.toSearchResultSafe(): SearchResponse? {
        val id = id ?: return null
        val title = name ?: return null

        val isSeries = isSeries()
        val path = if (isSeries) "series/show" else "media/detail"
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(
            title,
            "$mainUrl/api/$path/$id/$token",
            tvType
        ) {
            posterUrl = posterPath
        }
    }




    override suspend fun load(url: String): LoadResponse {
        val json = JSONObject(app.get(url).text)

        val title = json.optString("name", json.optString("title", "Unknown"))
        val poster = json.optString("poster_path").takeIf { it.isNotBlank() }

        val background = json.optString("backdrop_path_tv")
            .takeIf { it.isNotBlank() }
            ?: json.optString("backdrop_path")
                .takeIf { it.isNotBlank() }
            ?: poster
        Log.d("Phisher","$poster")
        val backdroppath = json.optString("backdrop_path")

        val overview = json.optString("overview")
        val releaseDate = json.optString("first_air_date",
            json.optString("release_date")
        )

        val voteAverage = json.optDouble("vote_average", -1.0)
            .takeIf { it >= 0 }

        val genres = json.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).mapNotNull {
                arr.optJSONObject(it)?.optString("name")
            }
        } ?: emptyList()

        val actors = json.optJSONArray("casterslist")?.let { arr ->
            (0 until arr.length()).mapNotNull {
                arr.optJSONObject(it)?.let { obj ->
                    val name = obj.optString("original_name") ?: return@let null
                    ActorData(
                        Actor(name, obj.optString("profile_path")),
                        roleString = obj.optString("character")
                    )
                }
            }
        } ?: emptyList()

        val isSeries = (json.optJSONArray("seasons")?.length() ?: 0) > 0

        return if (isSeries) {

            val episodes = mutableListOf<Episode>()

            val seasons = json.optJSONArray("seasons") ?: JSONArray()
            for (i in 0 until seasons.length()) {
                val seasonObj = seasons.getJSONObject(i)
                val seasonNumber = seasonObj.optInt("season_number", 0)

                val eps = seasonObj.optJSONArray("episodes") ?: continue
                for (j in 0 until eps.length()) {
                    val ep = eps.getJSONObject(j)

                    val videoUrls = ep.optJSONArray("videos")?.let { vids ->
                        (0 until vids.length()).mapNotNull { v ->
                            vids.optJSONObject(v)?.let {
                                LoadURL(
                                    it.optString("link"),
                                    it.optString("server"),
                                    it.optString("lang")
                                )
                            }
                        }
                    } ?: emptyList()

                    episodes += newEpisode(videoUrls.toJson()) {
                        name = ep.optString("name")
                        season = seasonNumber
                        episode = ep.optInt("episode_number", 0)
                        description = ep.optString("overview")
                        addDate(ep.optString("created_at"))
                        posterUrl = ep.optString("still_path_tv", ep.optString("still_path"))
                    }
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                backgroundPosterUrl = background ?: backdroppath ?: poster
                plot = overview
                tags = genres
                year = releaseDate.substringBefore("-").toIntOrNull()
                score = Score.from10(voteAverage)
                this.actors = actors
                addImdbId(json.optString("imdb_external_id"))
                addTMDbId(json.optLong("tmdb_id").toString())
            }

        } else {

            val videoUrls = json.optJSONArray("videos")?.let { vids ->
                (0 until vids.length()).mapNotNull { it ->
                    vids.optJSONObject(it)?.let {
                        LoadURL(
                            it.optString("link"),
                            it.optString("server"),
                            it.optString("lang")
                        )
                    }
                }
            } ?: emptyList()

            newMovieLoadResponse(title, url, TvType.Movie, videoUrls.toJson()) {
                posterUrl = poster
                backgroundPosterUrl = background ?: backdroppath
                plot = overview
                tags = genres
                year = releaseDate.substringBefore("-").toIntOrNull()
                score = Score.from10(voteAverage)
                this.actors = actors
                addImdbId(json.optString("imdb_external_id"))
                addTMDbId(json.optLong("tmdb_id").toString())
            }
        }
    }




    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = tryParseJson<List<LoadURL>>(data) ?: emptyList()
        links.forEach { loadUrl ->
            if (loadUrl.link!=null)
            {
                Log.d("Phisher", loadUrl.link)
                callback.invoke(
                    newExtractorLink(
                        "${loadUrl.server}",
                        "$name ${loadUrl.lang}",
                        url = loadUrl.link,
                        INFER_TYPE
                    ) {
                        this.quality = matchQualityFromString(loadUrl.server)
                    }
                )
            }
        }
        return true
    }

    fun matchQualityFromString(s: String?): Int {
        if (s.isNullOrBlank()) return Qualities.Unknown.value

        val v = s.lowercase()

        return when {
            "2160" in v || "4k" in v        -> Qualities.P2160.value
            "1440" in v                    -> Qualities.P1440.value
            "1080" in v || "fullhd" in v   -> Qualities.P1080.value
            "720" in v || "hd" in v        -> Qualities.P720.value
            "480" in v                     -> Qualities.P480.value
            "360" in v                     -> Qualities.P360.value
            else                           -> Qualities.Unknown.value
        }
    }

}


