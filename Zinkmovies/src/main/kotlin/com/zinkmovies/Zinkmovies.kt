package com.zinkmovies

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
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
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.text.Normalizer


class Zinkmovies : MainAPI() {
    override var mainUrl: String = runBlocking {
        ZinkmoviesPlugin.getDomains()?.zinkmovies ?: "https://new7.zinkmovies.biz"
    }
    override var name = "Zinkmovies"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries ,TvType.Anime
    )
    companion object
    {
        const val TMDBAPIKEY = "1865f43a0549ca50d341dd9ab8b29f49"
        const val TMDBBASE = "https://image.tmdb.org/t/p/original"
        const val TMDBAPI = "https://api.themoviedb.org/3"
    }

    override val mainPage = mainPageOf(
        "" to "Home",
        "movies/" to "Movies",
        "tvshows/" to "Tv Shows",
        "genre/bollywood/" to "Bollywood",
        "genre/HOLLYWOOD-MOVIES/" to "Hollywood",
        "genre/animation/" to "Animation",
        "genre/anime/" to "Anime",
        "genre/korean/" to "KDrama",
    )
    private val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0","Cookie" to "xla=s4t")

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl/${request.data}page/$page/",
            cacheTime = 60,
            headers = headers,
            allowRedirects = true
        ).document
        val home = doc.select("article").filterNot { it.closest(".animation-1") != null || it.closest(".items.featured") != null }
            .mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val titleText = post.select("h3 a").text()
        val title = cleanTitle(titleText)
        val url = post.select("h3 a").attr("href")
        val poster = post.select("img").attr("data-lazy-src").ifBlank { post.select("img").attr("src") }
        val score = Score.from10(post.select("div.rating").text())
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster.replace("/w185/", "/w500/")
            this.score = score
            this.quality = getSearchQuality(post.select("span.quality").text())
        }
    }


    override suspend fun search(query: String, page: Int): SearchResponseList {
        val response = app.get("$mainUrl/page/$page/?s=$query").document.select("article")

        return response.map {
            val name = it.select("a").text()
            val href = it.select("a").attr("href")
            val poster = it.select("img").attr("data-lazy-src").ifBlank { it.select("img").attr("src") }
            newMovieSearchResponse(name,href, TvType.Movie)
            {
                this.posterUrl = poster.replace("/w92/", "/w500/")
            }
        }.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document
        var title = doc.select("div.sheader h1").text().substringBefore("(")
        val seasontitle = title
        val seasonNumber = Regex("(?i)\\bSeason\\s*(\\d+)\\b").find(seasontitle)?.groupValues?.get(1)?.toIntOrNull()

        val image = doc.select("meta[property=og:image]").attr("content")
        val plot = doc.select("meta[property=og:description]").attr("content")
        val tags = doc.select("div.sgeneros a").eachText().toMutableList()
        val poster = doc.select("img").attr("data-lazy-src").ifBlank { doc.select("img").attr("src") }.replace("/w185/", "/w500/")
        val trailer = doc.selectFirst(".responsive-embed-container > iframe:nth-child(1)")?.attr("src")
            ?.replace("/embed/", "/watch?v=")

        val tvtype = when {
            url.contains("/movies/", ignoreCase = true) -> TvType.Movie
            url.contains("/tvshows/", ignoreCase = true) -> TvType.TvSeries
            else -> TvType.Movie
        }

        val recommendations = doc.select("#single_relacionados article").map {
            val poster = it.select("img").attr("data-lazy-src").ifBlank { it.select("img").attr("src") }
            val href = it.select("a").attr("href")
            newMovieSearchResponse("",href, TvType.Movie)
            {
                this.posterUrl = poster
            }
        }

        var actorData: List<ActorData> = emptyList()
        var genre: List<String>? = null
        var year = ""
        var background: String = image
        var description: String? = null

        var tmdbIdResolved = ""

        runCatching {
            val query = title
                .substringBefore("(")
                .replace("Season $seasonNumber", "", ignoreCase = true)
                .trim()

            val type = if (tvtype == TvType.TvSeries) "tv" else "movie"

            val searchUrl =
                "$TMDBAPI/search/$type?api_key=$TMDBAPIKEY&query=${query}"

            val json = JSONObject(app.get(searchUrl).text)

            tmdbIdResolved = json
                .optJSONArray("results")
                ?.optJSONObject(0)
                ?.optInt("id")
                ?.toString()
                .orEmpty()
        }

        val responseData: ResponseDataLocal? = if (tmdbIdResolved.isBlank()) null else runCatching {
            val type = if (tvtype == TvType.TvSeries) "tv" else "movie"
            val detailsText = app.get(
                "$TMDBAPI/$type/$tmdbIdResolved?api_key=$TMDBAPIKEY&append_to_response=credits,external_ids"
            ).text
            val detailsJson = if (detailsText.isNotBlank()) JSONObject(detailsText) else JSONObject()

            var metaName = detailsJson.optString("name")
                .takeIf { it.isNotBlank() }
                ?: detailsJson.optString("title").takeIf { it.isNotBlank() }
                ?: title

            if (seasonNumber != null && !metaName.contains("Season $seasonNumber", ignoreCase = true)) {
                metaName = "$metaName (Season $seasonNumber)"
            }

            val metaDesc = detailsJson.optString("overview").takeIf { it.isNotBlank() } ?: plot

            val yearRaw = detailsJson.optString("release_date").ifBlank { detailsJson.optString("first_air_date") }
            val metaYear = yearRaw.takeIf { it.isNotBlank() }?.take(4)
            val metaRating = detailsJson.optString("vote_average")

            val metaBackground = detailsJson.optString("backdrop_path")
                .takeIf { it.isNotBlank() }?.let { TMDBBASE + it } ?: image

            val imdbId = detailsJson
                .optJSONObject("external_ids")
                ?.optString("imdb_id")
                ?.takeIf { it.isNotBlank() }

            val logoPath = imdbId?.let {
                "https://live.metahub.space/logo/medium/$it/img"
            }

            val actorDataList = mutableListOf<ActorData>()

            //cast
            detailsJson.optJSONObject("credits")?.optJSONArray("cast")?.let { castArr ->
                for (i in 0 until castArr.length()) {
                    val c = castArr.optJSONObject(i) ?: continue
                    val name = c.optString("name").takeIf { it.isNotBlank() } ?: c.optString("original_name").orEmpty()
                    val profile = c.optString("profile_path").takeIf { it.isNotBlank() }?.let { TMDBBASE + it }
                    val character = c.optString("character").takeIf { it.isNotBlank() }
                    val actor = Actor(name, profile)
                    actorDataList += ActorData(actor = actor, roleString = character)
                }
            }

            // genres
            val metaGenres = mutableListOf<String>()
            detailsJson.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(metaGenres::add)
                }
            }

            // episodes for TV season -> videos
            val videos = mutableListOf<VideoLocal>()

            if (tvtype == TvType.TvSeries) {

                try {

                    val totalSeasons = detailsJson.optInt("number_of_seasons")

                    for (season in 1..totalSeasons) {

                        val seasonText = app.get(
                            "$TMDBAPI/tv/$tmdbIdResolved/season/$season?api_key=$TMDBAPIKEY"
                        ).text

                        if (seasonText.isBlank()) continue

                        val seasonJson = JSONObject(seasonText)

                        seasonJson.optJSONArray("episodes")?.let { epArr ->

                            for (i in 0 until epArr.length()) {

                                val ep = epArr.optJSONObject(i) ?: continue

                                val epNum = ep.optInt("episode_number")

                                val epName = ep.optString("name")

                                val epDesc = ep.optString("overview")

                                val epThumb = ep.optString("still_path")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { TMDBBASE + it }

                                val epAir = ep.optString("air_date")

                                val epRating = ep.optString("vote_average")
                                    .let { Score.from10(it.toString()) }

                                videos.add(
                                    VideoLocal(
                                        title = epName,
                                        season = season,
                                        episode = epNum,
                                        overview = epDesc,
                                        thumbnail = epThumb,
                                        released = epAir,
                                        rating = epRating,
                                    )
                                )
                            }
                        }
                    }

                } catch (_: Exception) {
                }
            }

            ResponseDataLocal(
                MetaLocal(
                    name = metaName,
                    description = metaDesc,
                    actorsData = actorDataList.ifEmpty { null },
                    year = metaYear,
                    background = metaBackground,
                    genres = metaGenres.ifEmpty { null },
                    videos = videos.ifEmpty { null },
                    rating = Score.from10(metaRating),
                    logo = logoPath,
                    imdbId = imdbId
                )
            )
        }.getOrNull()

        if (responseData != null) {
            description = responseData.meta?.description ?: plot
            actorData = responseData.meta?.actorsData ?: emptyList()
            title = responseData.meta?.name ?: title
            year = responseData.meta?.year ?: ""
            background = responseData.meta?.background ?: image
            responseData.meta?.genres?.let { g ->
                genre = g
                for (gn in g) if (!tags.contains(gn)) tags.add(gn)
            }
            responseData.meta?.rating
            responseData.meta?.imdbId
        }

        if (tvtype == TvType.Movie) {
            val movieList = mutableListOf<String>()

            movieList.addAll(
                doc.select("div.movie-button-container a")
                    .map { it.attr("href") }
            )

            return newMovieLoadResponse(title, url, TvType.Movie, movieList) {
                this.backgroundPosterUrl = background
                this.recommendations = recommendations
                this.logoUrl = responseData?.meta?.logo
                this.posterUrl = poster
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                this.actors = actorData
                this.score = responseData?.meta?.rating
                addTrailer(trailer)
                addImdbId(responseData?.meta?.imdbId)
            }
        } else {
            val episodesData = mutableListOf<Episode>()
            val epLinksMap = mutableMapOf<Pair<Int, Int>, MutableList<String>>()

            val seasonRegex = Regex("Season\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val episodeRegex = Regex("EPISODE\\s*[-:]?\\s*(\\d+)", RegexOption.IGNORE_CASE)

            doc.select(".lgtagmessage").forEach { seasonElement ->

                val seasonNum = seasonRegex
                    .find(seasonElement.text())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: return@forEach

                var next = seasonElement.nextElementSibling()

                while (
                    next != null &&
                    !next.hasClass("lgtagmessage")
                ) {

                    if (next.hasClass("movie-button-container")) {

                        val seasonUrl = next
                            .selectFirst("a[href]")
                            ?.attr("href")
                            ?.trim()

                        if (!seasonUrl.isNullOrBlank()) {

                            try {

                                val seasonDoc = app.get(seasonUrl).document

                                seasonDoc.select(".entry-content a[href]").forEach { ep ->

                                    val text = ep.text()

                                    val epNum = episodeRegex
                                        .find(text)
                                        ?.groupValues
                                        ?.get(1)
                                        ?.toIntOrNull()

                                    val href = ep.attr("href").trim()

                                    if (
                                        epNum != null &&
                                        href.isNotBlank() &&
                                        !text.contains("zip", true)
                                    ) {

                                        epLinksMap
                                            .getOrPut(seasonNum to epNum) {
                                                mutableListOf()
                                            }
                                            .add(href)
                                    }
                                }

                            } catch (_: Exception) {
                                Log.e(name, "Failed season fetch: $seasonUrl")
                            }
                        }
                    }

                    next = next.nextElementSibling()
                }
            }

            epLinksMap.forEach { (key, links) ->

                val (seasonNum, epNum) = key

                val info = responseData?.meta?.videos?.find {
                    it.season == seasonNum &&
                            it.episode == epNum
                }

                episodesData.add(
                    newEpisode(links.distinct().toJson()) {
                        this.name = info?.title ?: "Episode $epNum"
                        this.season = seasonNum
                        this.episode = epNum
                        this.posterUrl = info?.thumbnail
                        this.description = info?.overview
                        this.score = info?.rating
                        addDate(info?.released)
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.backgroundPosterUrl = background
                this.recommendations = recommendations
                this.logoUrl = responseData?.meta?.logo
                this.posterUrl = poster
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                this.actors = actorData
                this.score = responseData?.meta?.rating

                addTrailer(trailer)
                addImdbId(responseData?.meta?.imdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val linksList = tryParseJson<List<String>>(data)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

        if (linksList.isEmpty()) return false

        linksList.amap { pageUrl ->
            generateZinkLinks(pageUrl).forEach { link ->
                if (link.name.contains("worker", true)) {
                    callback(
                        newExtractorLink(
                            source = "Zink Worker",
                            name = "Zink Worker",
                            url = link.url
                        ) {
                            this.quality = getIndexQuality(url)
                            INFER_TYPE
                        }
                    )
                } else {
                    loadExtractor(
                        link.url,
                        name,
                        subtitleCallback,
                        callback
                    )
                }
            }
        }

        return true
    }



    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     *
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    fun getSearchQuality(check: String?): SearchQuality? {
        val s = check ?: return null
        val u = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()

        val patterns = listOf(

            // CAM TYPES FIRST
            Regex("\\b(hdts|hdcam|hdtc)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HdCam,
            Regex("\\b(camrip|cam[- ]?rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip,
            Regex("\\bcam\\b", RegexOption.IGNORE_CASE) to SearchQuality.Cam,

            // WEB
            Regex("\\b(web[- ]?dl|webrip|webdl)\\b", RegexOption.IGNORE_CASE) to SearchQuality.WebRip,

            // BLURAY
            Regex("\\b(bluray|blu[- ]?ray|bdrip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,

            // UHD / 4K
            Regex("\\b(4k|2160p|uhd|ds4k)\\b", RegexOption.IGNORE_CASE) to SearchQuality.FourK,

            // RESOLUTIONS
            Regex("\\b(1440p|qhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
            Regex("\\b(1080p|fullhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
            Regex("\\b720p\\b", RegexOption.IGNORE_CASE) to SearchQuality.SD,

            // OTHER SOURCES
            Regex("\\b(hdrip|hdtv)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
            Regex("\\bdvd\\b", RegexOption.IGNORE_CASE) to SearchQuality.DVD,
            Regex("\\bhq\\b", RegexOption.IGNORE_CASE) to SearchQuality.HQ,

            // FALLBACK
            Regex("\\brip\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip
        )

        return patterns.firstNotNullOfOrNull { (regex, quality) ->
            quality.takeIf { regex.containsMatchIn(u) }
        }
    }
}