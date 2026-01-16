package com.hdhub4u

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
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
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.Normalizer


class HDhub4uProvider : MainAPI() {
    override var mainUrl: String = runBlocking {
        HDhub4uPlugin.getDomains()?.HDHUB4u ?: "https://hdhub4u.rehab"
    }
    override var name = "HDHub4U"
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
        const val TMDBAPI = "https://wild-surf-4a0d.phisher1.workers.dev"
        const val TAG = "EpisodeParser"
    }

    override val mainPage = mainPageOf(
        "" to "Latest",
        "category/bollywood-movies/" to "Bollywood",
        "category/hollywood-movies/" to "Hollywood",
        "category/hindi-dubbed/" to "Hindi Dubbed",
        "category/south-hindi-movies/" to "South Hindi Dubbed",
        "category/category/web-series/" to "Web Series",
        "category/adult/" to "Adult",
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
        ).documentLarge
        val home = doc.select(".recent-movies > li.thumb").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val titleText = post
            .select("figcaption:nth-child(2) > a:nth-child(1) > p:nth-child(1)")
            .text()
        val title = cleanTitle(titleText)
        val url = post.select("figure:nth-child(1) > a:nth-child(2)").attr("href")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select("figure:nth-child(1) > img:nth-child(1)").attr("src")
            this.quality = getSearchQuality(titleText)
        }
    }

    private fun Document.toSearchResult(): SearchResponse {
        return newMovieSearchResponse(
            name = postTitle,
            url = permalink,
            type = TvType.Movie
        ) {
            posterUrl = postThumbnail
        }
    }


    override suspend fun search(query: String, page: Int): SearchResponseList {
        val response = app.get(
            "https://search.pingora.fyi/collections/post/documents/search" +
                    "?q=$query" +
                    "&query_by=post_title,category" +
                    "&query_by_weights=4,2" +
                    "&sort_by=sort_by_date:desc" +
                    "&limit=15" +
                    "&highlight_fields=none" +
                    "&use_cache=true" +
                    "&page=$page",
            headers = headers,
            referer = mainUrl
        ).parsedSafe<Search>()

        return response?.hits!!.map { hit -> hit.document.toSearchResult() }.toNewSearchResponseList()
    }


    private fun extractLinksATags(aTags: Elements): List<String> {
        val allowedDomains = Regex("""https://(.*\.)?(hdstream4u|hubstream)\..*""")

        return aTags
            .mapNotNull { it.attr("href") }
            .filter { allowedDomains.containsMatchIn(it) }
            .distinct()
    }



    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, headers = headers).documentLarge
        var title = doc.select(
            ".page-body h2[data-ved=\"2ahUKEwjL0NrBk4vnAhWlH7cAHRCeAlwQ3B0oATAfegQIFBAM\"], " +
                    "h2[data-ved=\"2ahUKEwiP0pGdlermAhUFYVAKHV8tAmgQ3B0oATAZegQIDhAM\"]"
        ).text()
        val seasontitle = title
        val seasonNumber = Regex("(?i)\\bSeason\\s*(\\d+)\\b").find(seasontitle)?.groupValues?.get(1)?.toIntOrNull()

        val image = doc.select("meta[property=og:image]").attr("content")
        val plot = doc.selectFirst(".kno-rdesc .kno-rdesc")?.text()
        val tags = doc.select(".page-meta em").eachText().toMutableList()
        val poster = doc.select("main.page-body img.aligncenter").attr("src")
        val trailer = doc.selectFirst(".responsive-embed-container > iframe:nth-child(1)")?.attr("src")
            ?.replace("/embed/", "/watch?v=")

        val typeraw = doc.select("h1.page-title span").text()
        val tvtype = if (typeraw.contains("movie", ignoreCase = true)) TvType.Movie else TvType.TvSeries
        val isMovie = tvtype == TvType.Movie

        var actorData: List<ActorData> = emptyList()
        var genre: List<String>? = null
        var year = ""
        var background: String = image
        var description: String? = null

        val imdbUrl = doc.select("div span a[href*='imdb.com']").attr("href")
            .ifEmpty {
                val tmdbHref = doc.select("div span a[href*='themoviedb.org']").attr("href")
                val isTv = tmdbHref.contains("/tv/")
                val tmdbId = tmdbHref.substringAfterLast("/").substringBefore("-").substringBefore("?")

                if (tmdbId.isNotEmpty()) {
                    val type = if (isTv) "tv" else "movie"
                    val imdbId = app.get(
                        "$TMDBAPI/$type/$tmdbId/external_ids?api_key=$TMDBAPIKEY"
                    ).parsedSafe<IMDB>()?.imdbId
                    imdbId ?: ""
                } else {
                    ""
                }
            }

        var tmdbIdResolved = ""
        run {
            val tmdbHref = doc.select("div span a[href*='themoviedb.org']").attr("href")
            if (tmdbHref.isNotBlank()) {
                tmdbIdResolved = tmdbHref.substringAfterLast("/").substringBefore("-").substringBefore("?")
            }
        }

        if (tmdbIdResolved.isBlank() && imdbUrl.isNotBlank()) {
            val imdbIdOnly = imdbUrl.substringAfter("title/").substringBefore("/")

            try {
                val findJson = JSONObject(
                    app.get(
                        "$TMDBAPI/find/$imdbIdOnly" +
                                "?api_key=$TMDBAPIKEY&external_source=imdb_id"
                    ).text
                )

                tmdbIdResolved = if (isMovie) {
                    findJson
                        .optJSONArray("movie_results")
                        ?.optJSONObject(0)
                        ?.optInt("id")
                        ?.toString()
                        .orEmpty()
                } else {
                    findJson
                        .optJSONArray("tv_results")
                        ?.optJSONObject(0)
                        ?.optInt("id")
                        ?.toString()
                        .orEmpty()
                }
            } catch (_: Exception) {
                // ignore resolve errors
            }
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

            val imdbid = detailsJson
                .optJSONObject("external_ids")
                ?.optString("imdb_id")
                ?.takeIf { it.isNotBlank() }

            val logoPath = imdbid?.let {
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
            if (tvtype == TvType.TvSeries && seasonNumber != null) {
                try {
                    val seasonText = app.get("$TMDBAPI/tv/$tmdbIdResolved/season/$seasonNumber?api_key=$TMDBAPIKEY").text
                    if (seasonText.isNotBlank()) {
                        val seasonJson = JSONObject(seasonText)
                        seasonJson.optJSONArray("episodes")?.let { epArr ->
                            for (i in 0 until epArr.length()) {
                                val ep = epArr.optJSONObject(i) ?: continue
                                val epNum = ep.optInt("episode_number")
                                val epName = ep.optString("name")
                                val epDesc = ep.optString("overview")
                                val epThumb = ep.optString("still_path").takeIf { it.isNotBlank() }?.let { TMDBBASE + it }
                                val epAir = ep.optString("air_date")
                                val epRating = ep.optString("vote_average").let { Score.from10(it.toString()) }

                                videos.add(
                                    VideoLocal(
                                        title = epName,
                                        season = seasonNumber,
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
                    // ignore season fetch errors
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
                    logo = logoPath
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
        }

        if (tvtype == TvType.Movie) {
            val movieList = mutableListOf<String>()
            movieList.addAll(
                doc.select("h3 a:matches(480|720|1080|2160|4K), h4 a:matches(480|720|1080|2160|4K)")
                    .map { it.attr("href") } + extractLinksATags(doc.select(".page-body > div a"))
            )

            return newMovieLoadResponse(title, url, TvType.Movie, movieList) {
                this.backgroundPosterUrl = background
                try { this.logoUrl = responseData?.meta?.logo } catch(_:Throwable){}
                this.posterUrl = poster
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                this.actors = actorData
                this.score = responseData?.meta?.rating
                addTrailer(trailer)
                addImdbUrl(imdbUrl)
            }
        } else {
            val episodesData = mutableListOf<Episode>()
            val epLinksMap = mutableMapOf<Int, MutableList<String>>()
            val episodeRegex = Regex("EPiSODE\\s*(\\d+)", RegexOption.IGNORE_CASE)

            doc.select("h3, h4").forEach { element ->
                val episodeNumberFromTitle = episodeRegex.find(element.text())?.groupValues?.get(1)?.toIntOrNull()
                val baseLinks = element.select("a[href]").mapNotNull { it -> it.attr("href").takeIf { it.isNotBlank() } }

                val isDirectLinkBlock = element.select("a").any {
                    it.text().contains(Regex("1080|720|4K|2160", RegexOption.IGNORE_CASE))
                }
                val allEpisodeLinks = mutableSetOf<String>()

                if (isDirectLinkBlock) {
                    baseLinks.forEach { url ->
                        try {
                            val resolvedUrl = getRedirectLinks(url.trim())
                            val episodeDoc = app.get(resolvedUrl).documentLarge

                            episodeDoc.select("h5 a").forEach { linkElement ->
                                val text = linkElement.text()
                                val link = linkElement.attr("href").takeIf { it.isNotBlank() } ?: return@forEach
                                val epNum = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()

                                if (epNum != null) {
                                    epLinksMap.getOrPut(epNum) { mutableListOf() }.add(link)
                                } else {
                                    Log.w(TAG, "Could not parse episode number from: $text")
                                }
                            }
                        } catch (_: Exception) {
                            Log.e(TAG, "Error resolving direct link for URL: $url")
                        }
                    }
                } else if (episodeNumberFromTitle != null) {
                    if (element.tagName() == "h4") {
                        var nextElement = element.nextElementSibling()
                        while (nextElement != null && nextElement.tagName() != "hr") {
                            val siblingLinks = nextElement.select("a[href]").mapNotNull { it -> it.attr("href").takeIf { it.isNotBlank() } }
                            allEpisodeLinks.addAll(siblingLinks)
                            nextElement = nextElement.nextElementSibling()
                        }
                    }

                    if (baseLinks.isNotEmpty()) {
                        allEpisodeLinks.addAll(baseLinks)
                    }

                    if (allEpisodeLinks.isNotEmpty()) {
                        Log.d(TAG, "Adding links for episode $episodeNumberFromTitle: ${allEpisodeLinks.distinct()}")
                        epLinksMap.getOrPut(episodeNumberFromTitle) { mutableListOf() }.addAll(allEpisodeLinks.distinct())
                    }
                }
            }

            epLinksMap.forEach { (epNum, links) ->
                val info = responseData?.meta?.videos?.find { it.season == seasonNumber && it.episode == epNum }

                episodesData.add(
                    newEpisode(links) {
                        this.name = info?.title ?: "Episode $epNum"
                        this.season = seasonNumber
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
                try { this.logoUrl = responseData?.meta?.logo } catch(_:Throwable){}
                this.posterUrl = poster
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                this.actors = actorData
                this.score = responseData?.meta?.rating
                addTrailer(trailer)
                addImdbUrl(imdbUrl)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linksList: List<String> = data.removePrefix("[").removeSuffix("]").replace("\"", "").split(',', ' ').map { it.trim() }.filter { it.isNotBlank() }
        for (link in linksList) {
            try {
                val finalLink = if ("?id=" in link) {
                    getRedirectLinks(link)
                } else {
                    link
                }
                if (finalLink.contains("Hubdrive",ignoreCase = true))
                {
                    Hubdrive().getUrl(finalLink,"", subtitleCallback,callback)
                } else loadExtractor(finalLink, subtitleCallback, callback)
            } catch (e: Exception) {
                Log.e("Phisher", "Failed to process $link: ${e.message}")
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
            Regex("\\b(4k|ds4k|uhd|2160p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.FourK,

            // CAM / THEATRE SOURCES FIRST
            Regex("\\b(hdts|hdcam|hdtc)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HdCam,
            Regex("\\b(camrip|cam[- ]?rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip,
            Regex("\\b(cam)\\b", RegexOption.IGNORE_CASE) to SearchQuality.Cam,

            // WEB / RIP
            Regex("\\b(web[- ]?dl|webrip|webdl)\\b", RegexOption.IGNORE_CASE) to SearchQuality.WebRip,

            // BLURAY
            Regex("\\b(bluray|bdrip|blu[- ]?ray)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,

            // RESOLUTIONS
            Regex("\\b(1440p|qhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,
            Regex("\\b(1080p|fullhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
            Regex("\\b(720p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.SD,

            // GENERIC HD LAST
            Regex("\\b(hdrip|hdtv)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,

            Regex("\\b(dvd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.DVD,
            Regex("\\b(hq)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HQ,
            Regex("\\b(rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip
        )


        for ((regex, quality) in patterns) if (regex.containsMatchIn(u)) return quality
        return null
    }
}