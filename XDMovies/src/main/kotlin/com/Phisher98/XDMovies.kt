package com.phisher98

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
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
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
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
import org.jsoup.nodes.Element

class XDMovies : MainAPI() {
    override var mainUrl = "https://new.xdmovies.wtf"
    override var name = "XD Movies"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object {
        val headers = mapOf(
            "x-auth-token" to base64Decode("NzI5N3Nra2loa2Fqd25zZ2FrbGFrc2h1d2Q="),
            "x-requested-with" to "XMLHttpRequest"
        )

        private val gson = Gson()

        private const val CINEMETAURL = "https://cinemeta-live.strem.io"
        const val TMDBIMAGEBASEURL = "https://image.tmdb.org/t/p/original"
        const val TMDBAPI = "https://divine-darkness-fad4.phisher13.workers.dev"

        private val titleRegex = Regex("""S(\d{1,2})E(\d{1,3})""", RegexOption.IGNORE_CASE)
        private val seasonNumRegex1 = Regex("""season-(?:packs|episodes)-(\d+)""")
        private val seasonNumRegex2 = Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE)

        private fun extractTmdbId(url: String): Int? = url.substringAfterLast("-").toIntOrNull()

        private fun Element.safeText(selector: String) = this.selectFirst(selector)?.text().orEmpty()
        private fun Element.safeAttr(selector: String, attr: String) = this.selectFirst(selector)?.attr(attr).orEmpty()
    }

    override val mainPage = mainPageOf(
        "Homepage" to "HomePage",
        "category.php?ott=Netflix" to "Netflix",
        "category.php?ott=Amazon" to "Amazon Prime Video",
        "category.php?ott=DisneyPlus" to "Disney+",
        "category.php?ott=AppleTVPlus" to "Apple TV+",
        "category.php?ott=HBOMax" to "HBO Max",
        "category.php?ott=Hulu" to "Hulu",
        "category.php?ott=Zee5" to "Zee5",
        "category.php?ott=JioHotstar" to "Hotstar",
    )

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query,1)?.items

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${if (request.data.contains("Homepage")) "?" else "${request.data}&"}page=$page").documentLarge
        val home = document.select("div.container div.movie-grid a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.safeText("h3")
        val href = fixUrl(this.attr("href"))
        val posterUrl = fixUrlNull(this.safeAttr("img", "src"))
        val quality = this.selectFirst("div.quality-badge")?.ownText()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getSearchQuality(quality)
        }
    }


    private fun highestQuality(qualities: List<String>): String? {
        return qualities
            .mapNotNull { q ->
                q.filter { it.isDigit() }.toIntOrNull()?.let { res -> res to q }
            }
            .maxByOrNull { it.first }
            ?.second
    }


    private fun SearchData.SearchDataItem.toSearchResult(): SearchResponse {
        val isTv = type.equals("tv", ignoreCase = true) || type.equals("series", ignoreCase = true)
        val tvType = if (isTv) TvType.TvSeries else TvType.Movie
        val url = mainUrl + path
        val bestQuality = highestQuality(qualities)
        return newMovieSearchResponse(title, url, tvType) {
            this.posterUrl = TMDBIMAGEBASEURL + poster
            this.quality = getSearchQuality(bestQuality)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchData = app.get(
            "$mainUrl/php/search_api.php?query=$query&fuzzy=true",
            headers
        ).parsedSafe<SearchData>() ?: return null
        val results = searchData.mapNotNull { it.toSearchResult() }
        return results.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val infoDiv = document.selectFirst("div.info")
        val detailsWrapper = document.selectFirst("div.details-wrapper")
        val headerStyle = document.selectFirst("#movie-header")?.attr("style").orEmpty()
        val downloadRoot = document.selectFirst("#download-links, .download")

        val title = infoDiv?.safeText("h2").orEmpty()
        val poster = detailsWrapper?.selectFirst("img")?.attr("src").orEmpty()
        val backgroundPoster = headerStyle.substringAfter("url('").substringBefore("');")
        val audios = document.select("span.neon-audio").text().split(",").map { it.trim() }
        val tags = (document.select("p:contains(Genres:)").first()?.ownText()?.split(",")?.map { it.trim() } ?: emptyList()) + audios
        val firstAirDate = document.select("p:contains(First Air Date:)").text().removePrefix("First Air Date:").trim()
        val year = firstAirDate.substringBefore("-").toIntOrNull()
        val description = document.select("p.overview").text()
        val rating = Score.from10(document.select("p:contains(Rating:)").text().removePrefix("Rating:").trim().substringBefore("/").trim())
        val contentType = url.substringAfter("$mainUrl/").substringBefore("/")
        val source = document.select("#source-info span").text()
        val tvType = when {
            contentType.equals("anime", ignoreCase = true) -> TvType.Anime
            contentType.equals("tv", ignoreCase = true) || contentType.equals("series", ignoreCase = true) -> TvType.TvSeries
            else -> TvType.Movie
        }

        val tmdbTvTypeSlug = if (tvType == TvType.Movie) "movie" else "tv"
        val tvTypeSlugForCinemeta = if (tvType == TvType.Movie) "movie" else "series"
        val tmdbId = extractTmdbId(url) ?: 0

        val tmdbResText: String? = runCatching {
            app.get(
                "$TMDBAPI/$tmdbTvTypeSlug/$tmdbId/external_ids?api_key=1865f43a0549ca50d341dd9ab8b29f49"
            ).text
        }.getOrNull()

        val tmdbRes = gson.fromJson(tmdbResText, IMDB::class.java)
        val imdbId = tmdbRes?.imdbId

        val creditsJsonText = runCatching {
            app.get("$TMDBAPI/$tmdbTvTypeSlug/$tmdbId/credits?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US").text
        }.getOrNull()

        val actors = parseTmdbActors(creditsJsonText)

        val detailsJsonText = runCatching {
            app.get(
                "$TMDBAPI/$tmdbTvTypeSlug/$tmdbId?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US"
            ).text
        }.getOrNull()

        val genres: List<String> = detailsJsonText
            ?.let { JSONObject(it) }
            ?.optJSONArray("genres")
            ?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    array.optJSONObject(i)?.optString("name").takeIf { it!!.isNotBlank() }
                }
            }
            ?: emptyList()

        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = "https://api.themoviedb.org/3",
            apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
            type = tvType,
            tmdbId = tmdbId,
            appLangCode = "en"
        )

        val downloadLinks = document.select("div.download-item a")
            .mapNotNull { it.attr("href").trim().takeIf { link -> link.isNotEmpty() } }

        val href = JSONArray(downloadLinks).toString()

        val responseData = imdbId
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?.let {
                val jsonResponse = app.get("$CINEMETAURL/meta/$tvTypeSlugForCinemeta/$it.json").text
                if (jsonResponse.startsWith("{")) gson.fromJson(jsonResponse, ResponseData::class.java) else null
            }

        if (tvType == TvType.TvSeries || tvType == TvType.Anime) {
            val episodes = mutableListOf<Episode>()
            val seasonSections = downloadRoot?.select("div.season-section") ?: emptyList()

            for (seasonSection in seasonSections) {
                val seasonNum = seasonNumRegex1.find(seasonSection.html())?.groupValues?.get(1)?.toIntOrNull()
                    ?: seasonNumRegex2.find(seasonSection.selectFirst("button.toggle-season-btn")?.text().orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1

                val tmdbSeasonRes: TMDBRes? = runCatching {
                    val text = app.get(
                        "$TMDBAPI/tv/$tmdbId/season/$seasonNum?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US"
                    ).textLarge
                    gson.fromJson(text, TMDBRes::class.java)
                }.getOrNull()

                val episodeMap = mutableMapOf<Int, MutableList<String>>()
                val packs = mutableListOf<Pair<Int, Pair<String, String>>>() // index -> (link, title)

                seasonSection.select(".episode-card").forEach { card ->
                    val cardTitle = card.selectFirst(".episode-title")?.text().orEmpty()
                    val m = titleRegex.find(cardTitle)
                    val epNum = m?.groupValues?.get(2)?.toIntOrNull()
                        ?: (card.parent()?.children()?.indexOf(card)?.plus(1) ?: 1)
                    val links = card.select("a.movie-download-btn, a.download-button")
                        .mapNotNull { it -> it.attr("href").trim().takeIf { it.isNotEmpty() } }
                    if (links.isNotEmpty()) episodeMap.getOrPut(epNum) { mutableListOf() }.addAll(links)
                }

                seasonSection.select(".packs-grid .pack-card").forEachIndexed { idx, pack ->
                    val link = pack.selectFirst("a.download-button")?.attr("href")?.trim().takeIf { !it.isNullOrBlank() } ?: return@forEachIndexed
                    val title = "Episode ${idx + 1} [Packs/Zips]"
                    packs += Pair(idx + 1, Pair(link, title))
                }

                if (episodeMap.isNotEmpty()) {
                    for ((epNum, links) in episodeMap) {
                        val tmdbEpisode = tmdbSeasonRes?.episodes?.find { it.episodeNumber == epNum }
                        val info = responseData?.meta?.videos?.find { it.season == seasonNum && it.episode == epNum }
                        val name = tmdbEpisode?.name ?: info?.name ?: "Episode $epNum"
                        val desc = tmdbEpisode?.overview ?: info?.overview
                        val poster = tmdbEpisode?.stillPath?.let { TMDBIMAGEBASEURL + it }
                        val score = Score.from10(tmdbEpisode?.voteAverage)
                        val airDate = tmdbEpisode?.airDate

                        episodes += newEpisode(links.toJson()) {
                            this.name = name
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = poster
                            this.description = desc
                            this.score = score
                            this.addDate(airDate)
                        }
                    }
                } else if (packs.isNotEmpty()) {
                    for ((idx, pair) in packs) {
                        val (link, title) = pair
                        val epNum = idx
                        val tmdbEpisode = tmdbSeasonRes?.episodes?.find { it.episodeNumber == epNum }
                        val info = responseData?.meta?.videos?.find { it.season == seasonNum && it.episode == epNum }
                        val name = title
                        val desc = tmdbEpisode?.overview ?: info?.overview
                        val poster = tmdbEpisode?.stillPath?.let { TMDBIMAGEBASEURL + it }
                        val score = Score.from10(tmdbEpisode?.voteAverage)
                        val airDate = tmdbEpisode?.airDate

                        episodes += newEpisode(listOf(link).toJson()) {
                            this.name = name
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = poster
                            this.description = desc
                            this.score = score
                            this.addDate(airDate)
                        }
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.year = year
                this.plot = description
                this.tags = tags.ifEmpty { genres }
                this.score = rating
                this.contentRating = source
                this.actors=actors
                addImdbId(imdbId)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, href) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backgroundPoster
            try { this.logoUrl = logoUrl } catch(_:Throwable){}
            this.year = year
            this.plot = description
            this.tags = tags.ifEmpty { genres }
            this.score = rating
            this.contentRating = source
            this.actors=actors
            addImdbId(imdbId)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        val links = runCatching {
            JSONArray(data).let { arr ->
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
        }.getOrElse {
            listOf(data.trim()).filter { it.isNotEmpty() }
        }

        var success = false

        links.forEach { link ->
            runCatching {
                loadExtractor(link, name, subtitleCallback, callback)
                success = true
            }.onFailure {
                Log.e("XDMovies", "Failed to load link: $link")
            }
        }

        return success
    }

}
