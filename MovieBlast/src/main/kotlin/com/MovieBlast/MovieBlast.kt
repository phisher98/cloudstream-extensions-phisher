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
        val headers = mapOf(
            "hash256" to base64Decode("ODZkYzAzMjQ0YWRkZGIzY2JlZGJmMGFlMzYwNzRhNzM2ZWUyOTNhNjQ3NzRiMThlODJhNjI0NGVhZmQwZGYzMA=="),
            "packagename" to base64Decode("Y29tLm1vdmllYmxhc3QNCg==").trim(),
            "signature" to base64Decode("MzA4MjAyZTQzMDgyMDFjYzAyMDEwMTMwMGQwNjA5MmE4NjQ4ODZmNzBkMDEwMTA1MDUwMDMwMzczMTE2MzAxNDA2MDM1NTA0MDMwYzBkNDE2ZTY0NzI2ZjY5NjQyMDQ0NjU2Mjc1NjczMTEwMzAwZTA2MDM1NTA0MGEwYzA3NDE2ZTY0NzI2ZjY5NjQzMTBiMzAwOTA2MDM1NTA0MDYxMzAyNTU1MzMwMjAxNzBkMzIzNDMxMzIzMTM5MzEzNTMyMzMzNTMzNWExODBmMzIzMDM1MzQzMTMyMzEzMjMxMzUzMjMzMzUzMzVhMzAzNzMxMTYzMDE0MDYwMzU1MDQwMzBjMGQ0MTZlNjQ3MjZmNjk2NDIwNDQ2NTYyNzU2NzMxMTAzMDBlMDYwMzU1MDQwYTBjMDc0MTZlNjQ3MjZmNjk2NDMxMGIzMDA5MDYwMzU1MDQwNjEzMDI1NTUzMzA4MjAxMjIzMDBkMDYwOTJhODY0ODg2ZjcwZDAxMDEwMTA1MDAwMzgyMDEwZjAwMzA4MjAxMGEwMjgyMDEwMTAwYmU1OWEzNGJkYWYyZDI1MzFlMjUyYWE1ZTJmMDg0ODkzMDJmNjYxNTE0YzYyOWMwZjQwM2M3MzZiMWY4OTEwYmJhYzM1Mzg5OWQ4YzI5ZDkzZTE4ODQxZGQxNTc5OTkwN2Q4MTM2OTk5YmI3NTFhMjlkNjU3ZTU0MDMzNjRlMTBiODZjOWI1ZWFhYjRjODY4MDNmN2RmMTZjNDc0OTQ5OWUwMGUxOThlOGY4ZGJlODdjMTdlZDU5OTdjMzk1ZWRhZmE0OWQzN2IxNTliYWVmZWNkYzhlMTU1Mzg2MDQ0ZjIyNGJhMmJmYTM2MzllZmM0YWM0YTYzODc1ODM4MjVlZTUxM2M5ZWE1OTRkNDQ5NmNmYjY4OWE5MzM2M2U3MGFkMWM5OWY4YTIyZTBhNGUxOWZiNzBiY2JlYmVjOTM3M2U0MWE0NTVlMmU0YWEwYWY4ZDJiODk2ZTRmZjVjYjM4Y2VlNTliMmM4YmU4NjI3MWJlYTEwYjAwM2EzYTY3NDBmZDM0MmZkOTk1MDk3MjdmMmI5YTFjYmZhZTczMGY1MTU0OGI5YzczMzBjNTI1MzBiNGNjMjVhOGJkZTRjNmY1MmE3N2IyYzI2OTYyYmNkMmRjYzNmZWI1MTcwYWJlMjY5YWVjNjJlMDE4M2QxZjNkMDcyYTliNGZlODZiYjc2M2YwMjAzMDEwMDAxMzAwZDA2MDkyYTg2NDg4NmY3MGQwMTAxMDUwNTAwMDM4MjAxMDEwMDM2NDU1MTA5NzNkYjA3ODIzZTlkY2I5YzA1N2RhN2RkYTE4M2M2NzFhMzhlZGUxYjYwOGJjNzkxNzQwNWJiZDZlM2Y5NTVkMzFkZmU2ZWIyMjAzOGMxODE4YjgzYTczMzVlMzA2MDZkZGFjMzMxYjVkYjI5MDYzYzhkM2MxZTdmZmQyM2VmNzUyZDFhYWJhMjhkM2NlMzFhMTZlOWViYjNlMGE1NTI5ZDc3NDdmZWY2ZGE3OWZjMTljMjQ2NzZjMWQ4MTJkMjA5ZDJhMmRhM2E4ZmE2YTQzZDhjOWE0Y2MxZTFmNWUwMzA5ZDBlNjkzNzZkZWM3YWE1ZTA2MjViZTI0ODQwOWNlZTg2MjZmODlkNjdiZDQ3N2JhZjU5MzdjMDM2MmVlZjEyNDkxYmI3OWU3OTFjZGRlMjEwZmY5Yzc4NTNkNWViZGIzZWY2ZTgxOTA0YmMwNjA0ODk2Mjk1Mzg3NTEzYzY4ZDM5YzA5MWQwZmIxMWRlOTA0OTQwMmEzY2IwZTc5NzVjMzI4ZmU4ZDM0YjlmNmVjYWUyY2E0NWYyZGFiM2IwOTA3NWJhYjEzNjA5NzdjM2FmMzc3NTkxNjgyMjU4OTJhNjJmYmY2NGY4YzI4Y2VkMjY2NGE2NWU2MWI2ODM3YmEwMTAzZTQ4NGE1OWI5YzQ3MTVkNzU5ZWUz"),
            "User-Agent" to name)

        val res = app
            .get("$mainUrl/api/search/$safeQuery/$token",headers = headers)
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
                val headers = mapOf(
                    "Accept-Encoding" to "identity",
                    "Connection" to "Keep-Alive",
                    "Icy-MetaData" to "1",
                    "Referer" to name,
                    "User-Agent" to name,
                    "x-request-x" to base64Decode("Y29tLm1vdmllYmxhc3QNCg==").trim()
                )
                val signed = generateSignedUrl(httpsify(loadUrl.link))
                callback.invoke(
                    newExtractorLink(
                        "${loadUrl.server}",
                        "$name ${loadUrl.lang}",
                        signed,
                        INFER_TYPE
                    ) {
                        this.quality = matchQualityFromString(loadUrl.server)
                        this.headers = headers
                    }
                )
            }
        }
        return true
    }


    fun httpsify(url: String): String {
        return if (!url.startsWith("http")) "https://$url" else url
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


