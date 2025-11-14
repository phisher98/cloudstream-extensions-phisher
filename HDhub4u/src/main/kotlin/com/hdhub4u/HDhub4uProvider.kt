package com.hdhub4u

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element
import org.jsoup.select.Elements


class HDhub4uProvider : MainAPI() {
    override var mainUrl: String = runBlocking {
        HDhub4uPlugin.getDomains()?.HDHUB4u ?: "https://hdhub4u.menu"
    }
    override var name = "HDHub4U"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries ,TvType.Anime
    )
    private val cinemeta_url = "https://v3-cinemeta.strem.io/meta"
    override val mainPage = mainPageOf(
        "" to "Latest",
        "category/bollywood-movies/" to "Bollywood",
        "category/hollywood-movies/" to "Hollywood",
        "category/hindi-dubbed/" to "Hindi Dubbed",
        "category/south-hindi-movies/" to "South Hindi Dubbed",
        "category/category/web-series/" to "Web Series",
        "category/adult/" to "Adult",
    )
    private val headers =
        mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0","Cookie" to "xla=s4t")

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
        val title = post.select("figcaption:nth-child(2) > a:nth-child(1) > p:nth-child(1)").text()
        val url = post.select("figure:nth-child(1) > a:nth-child(2)").attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select("figure:nth-child(1) > img:nth-child(1)").attr("src")
            this.quality = getSearchQuality(title)
        }
    }

    override suspend fun search(query: String,page: Int): SearchResponseList {
        val doc = app.get(
            "$mainUrl/page/$page/?s=$query",
            cacheTime = 60,
            headers = headers
        ).documentLarge
        return doc.select(".recent-movies > li.thumb").mapNotNull { toResult(it) }.toNewSearchResponseList()
    }

    private fun extractLinksATags(aTags: Elements): List<String> {
        val links = mutableListOf<String>()
        val baseUrl: List<String> = listOf("https://hdstream4u.com", "https://hubstream.art")
        baseUrl.forEachIndexed { index, _ ->
            var count = 0
            for (aTag in aTags) {
                val href = aTag.attr("href")
                if (href.contains(baseUrl[index])) {
                    try {
                        links[count] = links[count] + " , " + href
                    } catch (_: Exception) {
                        links.add(href)
                        count++
                    }
                }
            }
        }
        return links
    }


    @Suppress("LABEL_NAME_CLASH")
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url, cacheTime = 60, headers = headers
        ).documentLarge
        var title = doc.select(
            ".page-body h2[data-ved=\"2ahUKEwjL0NrBk4vnAhWlH7cAHRCeAlwQ3B0oATAfegQIFBAM\"], " +
                    "h2[data-ved=\"2ahUKEwiP0pGdlermAhUFYVAKHV8tAmgQ3B0oATAZegQIDhAM\"]"
        ).text()
        val seasontitle=title
        val seasonNumber = Regex("(?i)\\bSeason\\s*(\\d+)\\b").find(seasontitle)?.groupValues?.get(1)?.toIntOrNull()
        val image = doc.select("meta[property=og:image]").attr("content")
        val plot = doc.selectFirst(".kno-rdesc .kno-rdesc")?.text()
        val tags = doc.select(".page-meta em").eachText()
        val poster = doc.select("main.page-body img.aligncenter").attr("src")
        val trailer = doc.selectFirst(".responsive-embed-container > iframe:nth-child(1)")?.attr("src")
                ?.replace("/embed/", "/watch?v=")
        extractLinksATags(doc.select(".page-body > div a"))
        val typeraw=doc.select("h1.page-title span").text()
        val tvtype=if (typeraw.contains("movie",ignoreCase = true)) TvType.Movie else TvType.TvSeries
        val tvtypeapi = if (typeraw.contains("movie", ignoreCase = true)) "movie" else "series"
        val imdbUrl = doc.select("div span a[href*='imdb.com']")
            .attr("href")
            .ifEmpty {
                // fallback to TMDb → fetch IMDb ID via TMDb API
                val tmdbHref = doc.select("div span a[href*='themoviedb.org']").attr("href")
                val isTv = tmdbHref.contains("/tv/")
                val tmdbId = tmdbHref.substringAfterLast("/").substringBefore("-").substringBefore("?")

                if (tmdbId.isNotEmpty()) {
                    val type = if (isTv) "tv" else "movie"
                    val imdbId = app.get(
                        "https://api.themoviedb.org/3/$type/$tmdbId/external_ids?api_key=1865f43a0549ca50d341dd9ab8b29f49"
                    ).parsedSafe<IMDB>()?.imdbId
                imdbId ?: "" } else { "" } }
        val responseData = if (imdbUrl.isNotEmpty()) {
            val imdbId = imdbUrl.substringAfter("title/").substringBefore("/")
            val jsonResponse = app.get("$cinemeta_url/$tvtypeapi/$imdbId.json").text
            if(jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                val gson = Gson()
                gson.fromJson(jsonResponse, ResponseData::class.java)
            } else null } else null
        var cast: List<String> = emptyList()
        val genre: List<String>? = null
        var year = ""
        var background: String = image
        var description: String? = null
        if(responseData != null) {
            description = responseData.meta?.description ?: plot
            cast = responseData.meta?.cast ?: emptyList()
            title = responseData.meta?.name ?: title
            year = responseData.meta?.year ?: ""
            background = responseData.meta?.background ?: image
        }
        if (tvtype==TvType.Movie) {
            val movieList = mutableListOf<String>()

            movieList.addAll(
                doc.select("h3 a:matches(480|720|1080|2160|4K), h4 a:matches(480|720|1080|2160|4K)")
                    .map { it.attr("href") }
            )

            return newMovieLoadResponse(title, url, TvType.Movie, movieList) {
                this.backgroundPosterUrl = background
                this.posterUrl= poster
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                addActors(cast)
                addTrailer(trailer)
                addImdbUrl(imdbUrl)
            }
        } else {
            val episodesData = mutableListOf<Episode>()
            val epLinksMap = mutableMapOf<Int, MutableList<String>>()
            val episodeRegex = Regex("EPiSODE\\s*(\\d+)", RegexOption.IGNORE_CASE)

            val TAG = "EpisodeParser"

            doc.select("h3, h4").forEach { element ->
                val episodeNumberFromTitle = episodeRegex.find(element.text())?.groupValues?.get(1)?.toIntOrNull()
                val baseLinks = element.select("a[href]").mapNotNull { it.attr("href").takeIf { it.isNotBlank() } }

                val isDirectLinkBlock = element.select("a").any {
                    it.text().contains(Regex("1080|720|4K|2160", RegexOption.IGNORE_CASE))
                }
                val allEpisodeLinks = mutableSetOf<String>()

                if (isDirectLinkBlock) {
                    baseLinks.forEach { url ->
                        try {
                            val resolvedUrl = getRedirectLinks(url.trim())
                            val episodeDoc = app.get(resolvedUrl).documentLarge

                            // Look for structure like: <h5><a>Episode 1 – 4GB</a></h5>
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
                    // Handle sibling links under <h4>
                    if (element.tagName() == "h4") {
                        var nextElement = element.nextElementSibling()
                        while (nextElement != null && nextElement.tagName() != "hr") {
                            val siblingLinks = nextElement.select("a[href]").mapNotNull { it.attr("href").takeIf { it.isNotBlank() } }
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
                        addDate(info?.released)
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.backgroundPosterUrl = background
                this.posterUrl= poster
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                addActors(cast)
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
    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("4k") || lowercaseCheck.contains("uhd") || lowercaseCheck.contains("2160p") -> SearchQuality.UHD
                lowercaseCheck.contains("1440p") || lowercaseCheck.contains("qhd") -> SearchQuality.BlueRay
                lowercaseCheck.contains("1080p") || lowercaseCheck.contains("fullhd") -> SearchQuality.HD
                lowercaseCheck.contains("720p") -> SearchQuality.SD
                lowercaseCheck.contains("webrip") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("bluray") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains("hdtc") -> SearchQuality.HdCam
                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hdtv") -> SearchQuality.HD
                lowercaseCheck.contains("hq") -> SearchQuality.HQ
                else -> null
            }
        }
        return null
    }

    data class Meta(
        val id: String?,
        val imdb_id: String?,
        val type: String?,
        val poster: String?,
        val logo: String?,
        val background: String?,
        val moviedb_id: Int?,
        val name: String?,
        val description: String?,
        val genre: List<String>?,
        val releaseInfo: String?,
        val status: String?,
        val runtime: String?,
        val cast: List<String>?,
        val language: String?,
        val country: String?,
        val imdbRating: String?,
        val slug: String?,
        val year: String?,
        val videos: List<EpisodeDetails>?
    )

    data class EpisodeDetails(
        val id: String?,
        val name: String?,
        val title: String?,
        val season: Int?,
        val episode: Int?,
        val released: String?,
        val overview: String?,
        val thumbnail: String?,
        val moviedb_id: Int?
    )

    data class ResponseData(
        val meta: Meta?
    )

}