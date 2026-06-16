package com.cinefreak

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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.amap
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element
import java.net.URI
import java.text.Normalizer


open class Cinefreak : MainAPI() {
    override var mainUrl: String = runBlocking {
        CinefreakPlugin.getDomains()?.cinefreak ?: "https://cinefreak.nl"
    }
    override var name = "Cinefreak"
    override var lang = "bn"
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
        "animation" to "Animation",
        "bangla-movies" to "Bangla Movies",
        "bangla-dubbed" to "Bangla Dubbed",
        "chinese" to "Chinese",
        "dual-audio" to "Dual Audio",
        "english-movies" to "English Movies",
        "hindi-movies" to "Hindi Movies",
        "hindi-dubbed-movies" to "Hindi Dubbed Movies",
        "japanese" to "Japanese",
        "k-drama" to "K-Drama",
        "korean" to "Korean",
        "kannada" to "Kannada",
        "telugu" to "Telugu",
        "tamil" to "Tamil",
        "malayalam" to "Malayalam",
        "indonesian" to "Indonesian",
        "others" to "Others",
        "spanish" to "Spanish",
    )
    private val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0","Cookie" to "xla=s4t")

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/${request.data}/page/$page/",).document
        val home = doc.select("div.card-grid a").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val titleText = post.select("h3").text()
        val title = cleanTitle(titleText)
        val url = post.attr("href")
        val poster = post.select("img").attr("data-lazy-src").ifBlank { post.select("img").attr("src") }
        val score = Score.from10(post.select("div.rating").text())
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster.replace("/w185/", "/w500/")
            this.score = score
            this.quality = getSearchQuality(post.select("div.quality-badges span").text())
        }
    }


    override suspend fun search(query: String, page: Int): SearchResponseList {

        val results = app.get(
            "$mainUrl/search-api.php?q=${query}&pg=$page"
        ).parsedSafe<SearchData>()
            ?.results
            .orEmpty()

        return results.map { obj ->
            val href = if (obj.l.startsWith("http")) {
                obj.l
            } else {
                "$mainUrl/${obj.l}/"
            }

            val type = when {
                obj.t.contains("season", true) ||
                        obj.t.contains("series", true) ||
                        obj.t.contains("episode", true) ||
                        obj.t.contains("s0", true) -> TvType.TvSeries

                else -> TvType.Movie
            }

            val poster = if (obj.i.contains(name, ignoreCase = true)) "$mainUrl${obj.i.replace(getBaseUrl(obj.i), "")}" else obj.i
            newMovieSearchResponse(obj.t, href, type) {
                this.posterUrl = poster
            }
        }.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, headers = headers).document

        val fullTitleText = doc.select("h1.page-title").text()
        var title = fullTitleText.substringBefore(" Download").substringBefore(" [").trim()

        val seasonTitle = fullTitleText

        val seasonNumber = Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(seasonTitle)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val plot = doc.select("""meta[property=og:description]""")
            .attr("content")
            .ifBlank {
                doc.select("div.entry-content p").text()
            }

        val tags = doc.select("div.sgeneros a, span.badge.badge-outline a")
            .eachText()
            .toMutableList()

        val poster = doc.select("div.poster-image img")
            .attr("src")
            .ifBlank {
                doc.select("div.poster-image img")
                    .attr("data-lazy-src")
            }

        val trailer = doc.selectFirst(".responsive-embed-container iframe")
            ?.attr("src")
            ?.replace("/embed/", "/watch?v=")

        val tvtype = when {
            doc.select("div.ep-card").isNotEmpty() -> TvType.TvSeries
            else -> TvType.Movie
        }

        val recommendations = doc.select("#single_relacionados article").map {

            val recPoster = it.select("img")
                .attr("data-lazy-src")
                .ifBlank { it.select("img").attr("src") }

            val href = it.select("a").attr("href")

            newMovieSearchResponse("", href, TvType.Movie) {
                posterUrl = recPoster
            }
        }

        var actorData: List<ActorData> = emptyList()
        var genre: List<String>? = null
        var year = ""
        var background: String = poster
        var description: String? = null

        var tmdbIdResolved = ""

        runCatching {

            tmdbIdResolved = doc.select("""a[href*="themoviedb.org"]""")
                .attr("href")
                .substringAfterLast("/")
                .substringBefore("?")
                .trim()

            if (tmdbIdResolved.isBlank()) {

                val imdbId = doc.select("""a[href*="imdb.com/title/"]""")
                    .attr("href")
                    .substringAfter("/title/")
                    .substringBefore("/")
                    .substringBefore("?")
                    .trim()

                if (imdbId.isNotBlank()) {

                    val resultKey =
                        if (tvtype == TvType.TvSeries)
                            "tv_results"
                        else
                            "movie_results"

                    tmdbIdResolved =
                        parseJson<Map<String, List<Map<String, Any>>>>(
                            app.get(
                                "$TMDBAPI/find/$imdbId?api_key=$TMDBAPIKEY&external_source=imdb_id"
                            ).text
                        )[resultKey]
                            ?.firstOrNull()
                            ?.get("id")
                            ?.toString()
                            .orEmpty()
                }
            }

            if (tmdbIdResolved.isBlank()) {

                val query = title
                    .substringBefore("(")
                    .replace("Season $seasonNumber", "", true)
                    .trim()

                val type =
                    if (tvtype == TvType.TvSeries)
                        "tv"
                    else
                        "movie"

                tmdbIdResolved =
                    parseJson<Map<String, List<Map<String, Any>>>>(
                        app.get(
                            "$TMDBAPI/search/$type?api_key=$TMDBAPIKEY&query=$query"
                        ).text
                    )["results"]
                        ?.firstOrNull()
                        ?.get("id")
                        ?.toString()
                        .orEmpty()
            }
        }

        val responseData =
            if (tmdbIdResolved.isBlank()) null
            else runCatching {

                val type =
                    if (tvtype == TvType.TvSeries)
                        "tv"
                    else
                        "movie"

                val details =
                    parseJson<Map<String, Any>>(
                        app.get(
                            "$TMDBAPI/$type/$tmdbIdResolved?api_key=$TMDBAPIKEY&append_to_response=credits,external_ids"
                        ).text
                    )

                var metaName =
                    details["name"]?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: details["title"]?.toString()
                            ?.takeIf { it.isNotBlank() }
                        ?: title

                if (
                    seasonNumber != null &&
                    !metaName.contains("Season $seasonNumber", true)
                ) {
                    metaName += " (Season $seasonNumber)"
                }

                val metaDesc =
                    details["overview"]?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: plot

                val yearRaw =
                    details["release_date"]?.toString()
                        ?.ifBlank {
                            details["first_air_date"]?.toString()
                        }
                        .orEmpty()

                val metaYear =
                    yearRaw.takeIf { it.isNotBlank() }
                        ?.take(4)

                val metaRating =
                    details["vote_average"]?.toString()

                val metaBackground =
                    details["backdrop_path"]
                        ?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { TMDBBASE + it }
                        ?: poster

                val externalIds =
                    details["external_ids"] as? Map<*, *>

                val imdbId =
                    externalIds?.get("imdb_id")
                        ?.toString()
                        ?.takeIf { it.isNotBlank() }

                val logoPath =
                    imdbId?.let {
                        "https://live.metahub.space/logo/medium/$it/img"
                    }

                val actorDataList = mutableListOf<ActorData>()

                ((details["credits"] as? Map<*, *>)?.get("cast") as? List<*>)
                    ?.filterIsInstance<Map<*, *>>()
                    ?.forEach { cast ->

                        val actor = Actor(
                            cast["name"]?.toString().orEmpty(),
                            cast["profile_path"]
                                ?.toString()
                                ?.let { TMDBBASE + it }
                        )

                        actorDataList += ActorData(actor)
                    }

                val metaGenres =
                    (details["genres"] as? List<*>)
                        ?.filterIsInstance<Map<*, *>>()
                        ?.mapNotNull {
                            it["name"]?.toString()
                        }

                val videos = mutableListOf<VideoLocal>()

                if (tvtype == TvType.TvSeries) {

                    val totalSeasons =
                        details["number_of_seasons"]
                            ?.toString()
                            ?.toIntOrNull()
                            ?: 0

                    for (season in 1..totalSeasons) {

                        val seasonData =
                            parseJson<Map<String, Any>>(
                                app.get(
                                    "$TMDBAPI/tv/$tmdbIdResolved/season/$season?api_key=$TMDBAPIKEY"
                                ).text
                            )

                        (seasonData["episodes"] as? List<*>)
                            ?.filterIsInstance<Map<*, *>>()
                            ?.forEach { ep ->

                                videos += VideoLocal(
                                    title = ep["name"]?.toString(),
                                    season = season,
                                    episode = ep["episode_number"]
                                        ?.toString()
                                        ?.toIntOrNull(),
                                    overview = ep["overview"]?.toString(),
                                    thumbnail = ep["still_path"]
                                        ?.toString()
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { TMDBBASE + it },
                                    released = ep["air_date"]?.toString(),
                                    rating = Score.from10(
                                        ep["vote_average"]?.toString()
                                    )
                                )
                            }
                    }
                }

                ResponseDataLocal(
                    MetaLocal(
                        name = metaName,
                        description = metaDesc,
                        actorsData = actorDataList.ifEmpty { null },
                        year = metaYear,
                        background = metaBackground,
                        genres = metaGenres,
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
            background = responseData.meta?.background ?: poster

            responseData.meta?.genres?.let {
                genre = it
                tags += it.filterNot(tags::contains)
            }
        }

        if (tvtype == TvType.Movie) {

            val movieLinks = mutableListOf<EpisodeLinkHrefs>()
            val qualityCount = mutableMapOf<String, Int>()

            doc.select("h4.movie-title").forEach { titleEl ->

                val quality = Regex("""(480p|720p|1080p|2160p)""")
                    .find(titleEl.text())
                    ?.value
                    ?: return@forEach

                val container = titleEl.nextElementSibling()
                    ?: return@forEach

                container.select("a.dlbtn-download[href]")
                    .forEach { element ->

                        val href = element.attr("href").trim()

                        if (href.isNotEmpty()) {

                            val count = (qualityCount[quality] ?: 0) + 1
                            qualityCount[quality] = count

                            val qualityName = if (count == 1) {
                                quality
                            } else {
                                "${quality}_$count"
                            }

                            movieLinks.add(
                                EpisodeLinkHrefs(
                                    qualityName,
                                    href
                                )
                            )
                        }
                    }
            }

            val loadData = mapOf(
                "links" to movieLinks
            )

            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                loadData.toJson()
            ) {
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

        val episodesData = mutableListOf<Episode>()

        doc.select("div.ep-card").forEach { card ->

            val season =
                Regex("""S(\d+)""")
                    .find(card.select("span.season-number").text())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 1

            val epBadgeText = card.select("span.episode-badge").text()
            val episodeLabel =
                epBadgeText.replace(Regex("(?i)Episode\\s*"), "").trim().ifBlank {
                    card.select("div.ep-title").text().trim()
                }

            if (episodeLabel.isBlank()) return@forEach

            val firstEpisode =
                Regex("""\d+""")
                    .find(episodeLabel)
                    ?.value
                    ?.toIntOrNull()
                    ?: 1

            val info = responseData?.meta?.videos?.find {
                it.season == season &&
                        it.episode == firstEpisode
            }

            val links = mutableListOf<EpisodeLinkHrefs>()

            card.select("div.quality-grid a").forEach { a ->
                links += EpisodeLinkHrefs(
                    a.text().trim(),
                    a.attr("href").trim()
                )
            }

            val data = mapOf(
                "season" to season,
                "episode" to episodeLabel,
                "links" to links
            ).toJson()

            val epTitle = card.select("div.ep-title").text().trim()
            val defaultName = epTitle.takeIf { it.isNotBlank() } ?: "S$season Episode $episodeLabel"

            episodesData += newEpisode(data) {
                this.name = info?.title ?: defaultName
                this.season = season
                this.episode = firstEpisode
                this.posterUrl = info?.thumbnail
                this.description = info?.overview
                this.score = info?.rating
                addDate(info?.released)
            }
        }

        return newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            episodesData
        ) {
            this.backgroundPosterUrl = background
            this.recommendations = recommendations
            this.posterUrl = poster
            this.logoUrl = responseData?.meta?.logo
            this.year = year.toIntOrNull()
            this.plot = description ?: plot
            this.tags = genre ?: tags
            this.actors = actorData
            this.score = responseData?.meta?.rating
            addTrailer(trailer)
            addImdbId(responseData?.meta?.imdbId)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = tryParseJson<Map<String, Any>>(data)
            ?: return false

        val links = parsed["links"]
            ?.toJson()
            ?.let { tryParseJson<List<EpisodeLinkHrefs>>(it) }
            ?: return false

        if (links.isEmpty()) return false
        links.amap { link ->

            val decoded = runCatching {
                base64Decode(
                    Regex("""id=([^&]+)""")
                        .find(fixUrl(link.url))
                        ?.groupValues
                        ?.getOrNull(1)
                        ?: return@amap
                )
            }.getOrNull()
                ?.substringBefore("newgo32")
                ?.trim()
                ?: return@amap

            when {
                "neodrive" in decoded -> {
                    Neodrive().getUrl(
                        decoded,
                        link.quality,
                        subtitleCallback,
                        callback
                    )
                }

                "cinecloud" in decoded -> {
                    CineCloud().getUrl(
                        decoded,
                        link.quality,
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

    data class EpisodeLinkHrefs(
        val quality: String,
        val url: String
    )

    data class SearchData(
        val results: List<Result>,
        val total: Long,
        val page: Long,
        val totalPages: Long,
    )

    data class Result(
        val t: String,
        val l: String,
        val d: String,
        val c: String,
        val tg: String,
        val i: String,
        val q: String,
    )

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}