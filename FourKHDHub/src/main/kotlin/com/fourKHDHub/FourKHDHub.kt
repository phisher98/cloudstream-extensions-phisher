package com.fourKHDHub

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.nodes.Element

class FourKHDHub : MainAPI() {
    override var mainUrl: String = runBlocking {
        FourKHDHubProvider.getDomains()?.n4khdhub ?: "https://4khdhub.dad"
    }
    override var name                 = "4K HDHUB"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.TvSeries)


    companion object
    {
        const val TMDBAPI = "https://wild-surf-4a0d.phisher1.workers.dev"
        const val TMDB_API_KEY = "1865f43a0549ca50d341dd9ab8b29f49"
        private const val SIMKL = "https://api.simkl.com"
        const val TMDBIMAGEBASEURL = "https://image.tmdb.org/t/p/original"

    }

    override val mainPage = mainPageOf(
        "" to "Home",
        "category/movies" to "Latest Movies",
        "category/hindi-movies" to "Hindi Movies",
        "category/english-movies" to "English Movies",
        "category/series" to "Latest Episodes",
        "category/korean-series" to "Korean Series",
        "category/netflix" to "Netflix",
        "category/amazon_prime_video" to "Amazon Prime Video",
        "category/jiohotstar" to "JioHotstar",
        "category/disney" to "Disney+",
        "category/Apple_TV" to "Apple TV+",
        "category/anime" to "Anime",
        "category/2160p-HDR" to "4K HDR",
        "category/imdb" to "Top IMDb"
    )


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}/page/$page"
        val document = app.get(url).documentLarge
        val results = document.select("div.card-grid a").mapNotNull {
                it.toSearchResult()
        }
        return newHomePageResponse(request.name, results)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text() ?: return null
        val href = this.attr("href")
        val posterUrl = this.select("img").attr("src")
        val tags = select("span.movie-card-format").map { it.text() }
        val quality = getSearchQuality(tags)
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").documentLarge
        val results = document.select("div.card-grid a").mapNotNull {
            it.toSearchResult()
        }
        return results
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title = document.selectFirst("h1.page-title")?.text()?.substringBefore("(")?.trim().toString()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.mt-2 span")?.text()?.toIntOrNull()
        val tvType = if ("Movies" in tags) TvType.Movie else TvType.TvSeries
        val isMovie = tvType == TvType.Movie
        val tmdbId = runCatching { fetchtmdb(title,isMovie) }.getOrNull()

        val hrefs: List<String> = document.select("div.download-item a").eachAttr("href")

        val description = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val trailer = document.selectFirst("#trailer-btn")?.attr("data-trailer-url")

        val recommendations = document.select("div.card-grid-small a").mapNotNull {
            it.toSearchResult()
        }

        var tmdbTitle: String? = null
        var tmdbOverview: String? = null
        var tmdbYear: Int? = null
        var tmdbRating: Double? = null
        var tmdbPoster: String? = null
        var tmdbBackdrop: String? = null
        var tmdbActors: List<ActorData> = emptyList()

        if (tmdbId != null) {
            val type = if (tvType == TvType.Movie) "movie" else "tv"
            val tmdbJson = runCatching {
                JSONObject(
                    app.get("$TMDBAPI/$type/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=credits")
                        .textLarge
                )
            }.getOrNull()

            if (tmdbJson != null) {
                tmdbTitle = tmdbJson.optString("title").ifBlank { tmdbJson.optString("name").ifBlank { null } }
                tmdbOverview = tmdbJson.optString("overview").takeIf { it.isNotBlank() }
                val date = tmdbJson.optString("release_date").ifBlank { tmdbJson.optString("first_air_date") }
                tmdbYear = date.takeIf { it.isNotBlank() }?.substringBefore("-")?.toIntOrNull()
                tmdbRating = tmdbJson.optDouble("vote_average").takeIf { it != 0.0 }
                tmdbPoster = tmdbJson.optString("poster_path").takeIf { it.isNotBlank() }?.let { TMDBIMAGEBASEURL + it }
                tmdbBackdrop = tmdbJson.optString("backdrop_path").takeIf { it.isNotBlank() }?.let { TMDBIMAGEBASEURL + it }

                tmdbActors = buildList {
                    tmdbJson.optJSONObject("credits")
                        ?.optJSONArray("cast")
                        ?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: obj.optString("original_name").takeIf { it.isNotBlank() }
                                if (name.isNullOrBlank()) continue
                                val profile = obj.optString("profile_path").takeIf { it.isNotBlank() }?.let { TMDBIMAGEBASEURL + it }
                                val character = obj.optString("character").takeIf { it.isNotBlank() }
                                add(ActorData(Actor(name, profile), roleString = character))
                            }
                        }
                }
            }
        }

        val fixedTitle = tmdbTitle ?: title
        val fixedPoster = tmdbPoster ?: poster
        val fixedBackdrop = tmdbBackdrop ?: poster
        val fixedPlot = tmdbOverview ?: description
        val fixedYear = tmdbYear ?: year
        val finalActorsFromTmdb = tmdbActors

        return if (tvType == TvType.TvSeries) {
            val tvSeriesEpisodes = mutableListOf<Episode>()
            val episodesMap = mutableMapOf<Pair<Int, Int>, MutableList<String>>()
            val maxEpisodePerSeason = mutableMapOf<Int, Int>()
            val imdbIdFromSeries = tmdbId?.let { id ->
                runCatching {
                    val url = "$TMDBAPI/tv/$id/external_ids?api_key=$TMDB_API_KEY"
                    JSONObject(app.get(url).textLarge).optString("imdb_id").takeIf { it.isNotBlank() }
                }.getOrNull()
            }

            val logoPath = imdbIdFromSeries?.let {
                "https://live.metahub.space/logo/medium/$it/img"
            }


            val simklIdseries = imdbIdFromSeries?.let { imdb ->
                runCatching {
                    JSONObject(app.get("$SIMKL/tv/$imdb?client_id=${BuildConfig.SIMKL_CLIENT_ID}").text)
                        .optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
                }.getOrNull()
            }

            document.select("div.episodes-list div.season-item").forEach { seasonElement ->
                val seasonText = seasonElement.select("div.episode-number").text()
                val season = Regex("""S?([1-9][0-9]*)""").find(seasonText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@forEach

                seasonElement.select("div.episode-download-item").forEach { episodeItem ->
                    val episodeText = episodeItem.select("div.episode-file-info span.badge-psa").text()
                    val episode = Regex("""Episode-0*([1-9][0-9]*)""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: return@forEach

                    val hrefsForEp = episodeItem.select("a").mapNotNull { it.attr("href").takeIf { it.isNotBlank() } }
                    if (hrefsForEp.isNotEmpty()) {
                        val key = season to episode
                        episodesMap.getOrPut(key) { mutableListOf() }.addAll(hrefsForEp)
                        maxEpisodePerSeason[season] = maxOf(maxEpisodePerSeason.getOrDefault(season, 0), episode)
                    }
                }
            }

            val tmdbSeasonCache = mutableMapOf<Int, JSONObject?>()
            if (tmdbId != null) {
                val seasonsToFetch = episodesMap.keys.map { it.first }.distinct()
                for (s in seasonsToFetch) {
                    tmdbSeasonCache[s] = runCatching {
                        JSONObject(app.get("$TMDBAPI/tv/$tmdbId/season/$s?api_key=$TMDB_API_KEY").text)
                    }.getOrNull()
                }
            }


            episodesMap.toSortedMap(compareBy({ it.first }, { it.second })).forEach { (seasonEpisode, hrefsList) ->
                val (season, episode) = seasonEpisode

                var epName: String? = null
                var epOverview: String? = null
                var epThumb: String? = null
                var epAir: String? = null
                var epRating: Double? = null

                tmdbSeasonCache[season]?.optJSONArray("episodes")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val epObj = arr.optJSONObject(i) ?: continue
                        if (epObj.optInt("episode_number") == episode) {
                            epName = epObj.optString("name").takeIf { it.isNotBlank() }
                            epOverview = epObj.optString("overview").takeIf { it.isNotBlank() }
                            epThumb = epObj.optString("still_path").takeIf { it.isNotBlank() }?.let { TMDBIMAGEBASEURL + it }
                            epAir = epObj.optString("air_date").takeIf { it.isNotBlank() }
                            epRating = epObj.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 }
                            break
                        }
                    }
                }

                tvSeriesEpisodes += newEpisode(hrefsList.distinct()) {
                    this.season = season
                    this.episode = episode
                    this.name = epName ?: "Episode $episode"
                    this.posterUrl = epThumb
                    this.description = epOverview
                    addDate(epAir)
                    this.score = safeScoreFrom10(epRating)
                }
            }

            document.select("div.download-item").forEach { item ->
                val headerText = item.select("div.flex-1.text-left.font-semibold").text()

                val season = Regex("""S([0-9]+)""").find(headerText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@forEach

                val size = Regex("""(\d+(?:\.\d+)?\s*GB)""").find(headerText)?.groupValues?.get(1) ?: "Unknown Size"
                val quality = Regex("""(\d{3,4}p)""").find(headerText)?.groupValues?.get(1) ?: "Unknown Quality"

                val hrefList = item.select("a").mapNotNull { it.attr("href").takeIf { it.isNotBlank() } }

                val fileTitle = item.select("div.file-title").text()
                    .replace(Regex("""\[[^]]*]"""), "")
                    .replace(Regex("""\(.+?\)"""), "")

                if (hrefList.isNotEmpty()) {
                    var nextEpisode = maxEpisodePerSeason.getOrDefault(season, 0) + 1

                    var epName: String? = null
                    var epOverview: String? = null
                    var epThumb: String? = null
                    var epAir: String? = null
                    var epRating: Double? = null

                    tmdbSeasonCache[season]?.optJSONArray("episodes")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val epObj = arr.optJSONObject(i) ?: continue
                            if (epObj.optInt("episode_number") == nextEpisode) {
                                epName = epObj.optString("name").takeIf { it.isNotBlank() }
                                epOverview = epObj.optString("overview").takeIf { it.isNotBlank() }
                                epThumb = epObj.optString("still_path").takeIf { it.isNotBlank() }?.let { TMDBIMAGEBASEURL + it }
                                epAir = epObj.optString("air_date").takeIf { it.isNotBlank() }
                                epRating = epObj.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 }
                                break
                            }
                        }
                    }

                    tvSeriesEpisodes += newEpisode(hrefList.distinct()) {
                        this.season = season
                        this.episode = nextEpisode
                        this.name = epName ?: "S${season.toString().padStart(2, '0')} – $fileTitle [$quality, $size]".trim()
                        this.posterUrl = epThumb
                        this.description = epOverview
                        addDate(epAir)
                        this.score = safeScoreFrom10(epRating)

                    }

                    nextEpisode++
                    maxEpisodePerSeason[season] = nextEpisode - 1
                }
            }

            newTvSeriesLoadResponse(fixedTitle, url, TvType.TvSeries, tvSeriesEpisodes) {
                this.posterUrl = fixedPoster
                this.backgroundPosterUrl = fixedBackdrop
                try { this.logoUrl = logoPath } catch(_:Throwable){}
                this.year = fixedYear
                this.plot = fixedPlot
                this.tags = tags
                this.recommendations = recommendations
                this.actors = finalActorsFromTmdb
                this.score = safeScoreFrom10(tmdbRating)
                addTrailer(trailer)
                addSimklId(simklIdseries)
            }
        } else {
            val imdbIdFromMovie = tmdbId?.let { id ->
                runCatching {
                    val url = "$TMDBAPI/movie/$id/external_ids?api_key=$TMDB_API_KEY"
                    JSONObject(app.get(url).textLarge).optString("imdb_id").takeIf { it.isNotBlank() }
                }.getOrNull()
            }

            val logoPath = imdbIdFromMovie?.let {
                "https://live.metahub.space/logo/medium/$it/img"
            }

            val simklIdMovie = imdbIdFromMovie?.let { imdb ->
                runCatching {
                    JSONObject(app.get("$SIMKL/movies/$imdb?client_id=${BuildConfig.SIMKL_CLIENT_ID}").text)
                        .optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
                }.getOrNull()
            }

            val movieCreditsJsonText = tmdbId?.let { id ->
                runCatching {
                    app.get("${TMDBAPI}/movie/$id/credits?api_key=$TMDB_API_KEY&language=en-US").textLarge
                }.getOrNull()
            }
            val movieCastList = parseCredits(movieCreditsJsonText)
            val finalMovieActors = tmdbActors.ifEmpty { movieCastList }

            newMovieLoadResponse(fixedTitle, url, TvType.Movie, hrefs) {
                this.posterUrl = fixedPoster
                this.backgroundPosterUrl = fixedBackdrop
                try { this.logoUrl = logoPath } catch(_:Throwable){}
                this.year = fixedYear
                this.plot = fixedPlot
                this.tags = tags
                this.recommendations = recommendations
                this.actors = finalMovieActors
                this.score = safeScoreFrom10(tmdbRating)
                addTrailer(trailer)
                addSimklId(simklIdMovie)
            }
        }
    }

    private val LINK_REGEX =
        Regex("""https?://[^\s'",\]\[]+""", RegexOption.IGNORE_CASE)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (data.isBlank()) return false

        val hubCloud = HubCloud()
        val hubDrive = Hubdrive()
        val hubCdn = Hubcdnn()

        LINK_REGEX.findAll(data)
            .map { it.value }
            .forEach { rawLink ->

                val resolvedLink = try {
                    if ("id=" in rawLink.lowercase())
                        getRedirectLinks(rawLink)
                    else
                        rawLink
                } catch (e: Exception) {
                    Log.e("Phisher", "Redirect failed for $rawLink — ${e.message}")
                    return@forEach
                }

                if (resolvedLink.isBlank()) {
                    Log.e("Extractor", "Resolved link is empty for $rawLink")
                    return@forEach
                }

                val lower = resolvedLink.lowercase()

                when {
                    "hubcloud" in lower -> {
                        hubCloud.getUrl(
                            resolvedLink,
                            name,
                            subtitleCallback,
                            callback
                        )
                    }

                    "hubdrive" in lower -> {
                        hubDrive.getUrl(
                            resolvedLink,
                            name,
                            subtitleCallback,
                            callback
                        )
                    }

                    "hubcdn" in lower -> {
                        hubCdn.getUrl(
                            resolvedLink,
                            name,
                            subtitleCallback,
                            callback
                        )
                    }

                    else -> {
                        loadExtractor(
                            resolvedLink,
                            name,
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }

        return true
    }
}
