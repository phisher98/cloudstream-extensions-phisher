package com.hdhub4u

import android.annotation.SuppressLint
import com.google.gson.Gson
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
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import org.jsoup.select.Elements


class HDhub4uProvider : MainAPI() {
    override var mainUrl = "https://hdhub4u.soccer"
    override var name = "HDHub4U"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.NSFW
    )
    private val cinemeta_url = "https://v3-cinemeta.strem.io/meta"
    override val mainPage = mainPageOf(
        "/" to "Latest",
        "/category/bollywood-movies/" to "Bollywood",
        "/category/hollywood-movies/" to "Hollywood",
        "/category/hindi-dubbed/" to "Hindi Dubbed",
        "/category/south-hindi-movies/" to "South Hindi Dubbed",
        "/category/category/web-series/" to "Web Series",
        "/category/adult/" to "Adult",
    )
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    private suspend fun getMainUrl() {
        newMainUrl.ifEmpty {
            val response =
                app.get("https://hdhublist.com/?re=hdhub", allowRedirects = true, cacheTime = 60)
            if (response.isSuccessful) {
                // Method 1: Extract from <meta http-equiv="refresh">
                val doc = response.document
                val metaRefresh = doc.selectFirst("meta[http-equiv=refresh]")
                if (metaRefresh != null) {
                    val content = metaRefresh.attr("content")
                    val urlMatch = Regex("""url=(.+)""").find(content)
                    if (urlMatch != null) {
                        newMainUrl = urlMatch.groupValues[1]
                    }
                }

                // Method 2: Extract from location.replace in <BODY>
                val bodyOnLoad = doc.selectFirst("body[onload]")
                if (bodyOnLoad != null) {
                    val onLoad = bodyOnLoad.attr("onload")
                    val urlMatch = Regex("""location\.replace\(['"](.+)['"]\)""").find(onLoad)
                    if (urlMatch != null) {
                        newMainUrl = urlMatch.groupValues[1].replace("+document.location.hash", "")
                    }
                }
            } else newMainUrl = mainUrl
        }
    }

    private var newMainUrl = ""

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        getMainUrl()
        val doc = app.get(
            "$newMainUrl/${request.data}page/$page/",
            cacheTime = 60,
            headers = headers,
            allowRedirects = true
        ).document
        val home = doc.select(".recent-movies > li.thumb").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select("figcaption:nth-child(2) > a:nth-child(1) > p:nth-child(1)").text().substringBefore("(")
        val url = post.select("figure:nth-child(1) > a:nth-child(2)").attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select("figure:nth-child(1) > img:nth-child(1)").attr("src")
            this.quality = getSearchQuality(title)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        getMainUrl()
        val doc = app.get(
            "$newMainUrl/?s=$query",
            cacheTime = 60,
            headers = headers
        ).document
        return doc.select(".recent-movies > li.thumb").mapNotNull { toResult(it) }
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


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url, cacheTime = 60, headers = headers
        ).document
        var title = doc.select(
            ".page-body h2[data-ved=\"2ahUKEwjL0NrBk4vnAhWlH7cAHRCeAlwQ3B0oATAfegQIFBAM\"], " +
                    "h2[data-ved=\"2ahUKEwiP0pGdlermAhUFYVAKHV8tAmgQ3B0oATAZegQIDhAM\"]"
        ).text()
        val seasontitle=title
        val seasonNumber = Regex("(?i)\\bSeason\\s*(\\d+)\\b").find(seasontitle)?.groupValues?.get(1)?.toIntOrNull()
        var image = doc.select(".aligncenter").attr("src")
        val plot = doc.selectFirst(".kno-rdesc .kno-rdesc")?.text()
        val tags = doc.select(".page-meta em").eachText()
        val trailer = doc.selectFirst(".responsive-embed-container > iframe:nth-child(1)")?.attr("src")
                ?.replace("/embed/", "/watch?v=")
        val links = extractLinksATags(doc.select(".page-body > div a"))
        val typeraw=doc.select("h1.page-title span").text()
        val tvtype=if (typeraw.contains("movie",ignoreCase = true)) TvType.Movie else TvType.TvSeries
        val tvtypeapi = if (typeraw.contains("movie", ignoreCase = true)) "movie" else "series"
        val imdbUrl = doc.select("div.kp-header div span a[href*='imdb.com']").attr("href")
        val responseData = if (imdbUrl.isNotEmpty()) {
            val imdbId = imdbUrl.substringAfter("title/").substringBefore("/")
            val jsonResponse = app.get("$cinemeta_url/$tvtypeapi/$imdbId.json").text
            if(jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                val gson = Gson()
                gson.fromJson(jsonResponse, ResponseData::class.java)
            } else null } else null
        var cast: List<String> = emptyList()
        val genre: List<String>? = null
        var imdbRating: String = ""
        var year: String = ""
        var background: String = image
        var description: String? = null
        if(responseData != null) {
            description = responseData.meta?.description ?: plot
            cast = responseData.meta?.cast ?: emptyList()
            title = responseData.meta?.name ?: title
            imdbRating = responseData.meta?.imdbRating ?: ""
            year = responseData.meta?.year ?: ""
            image = responseData.meta?.poster ?: image
            background = responseData.meta?.background ?: background
        }
        if (tvtype==TvType.Movie) {
            val movieList = mutableListOf<String>()

            movieList.addAll(
                doc.select("h3 a:matchesOwn(480|720|1080|2160|4K)")
                    .map { it.attr("href") }
            )

            return newMovieLoadResponse(title, url, TvType.Movie, movieList) {
                this.backgroundPosterUrl = background
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                addActors(cast)
                addTrailer(trailer)
                addImdbUrl(imdbUrl)
            }
        } else {
            val episodesData = mutableListOf<Episode>()
            val epLinksList = mutableListOf<MutableList<String>>()
            val episodeRegex = Regex("EPiSODE\\s*(\\d+)", RegexOption.IGNORE_CASE)

// Handling h3 elements
            doc.select("h3").forEachIndexed { index, h3Element ->
                val episodeLinks = h3Element.select("a:contains(EPiSODE), a:contains(WATCH)")
                    .map { it.attr("href") }
                    .filter { it.isNotEmpty() }
                val episodeInfo = responseData?.meta?.videos?.find { it.season == seasonNumber && it.episode == index+1 }
                if (episodeLinks.isNotEmpty()) {
                    epLinksList.add(episodeLinks.toMutableList())
                    episodesData.add(
                        newEpisode(episodeLinks) {
                            this.name = episodeInfo?.name ?: "Episode $index"
                            this.season = seasonNumber
                            this.episode = index
                            this.posterUrl = episodeInfo?.thumbnail
                            this.description=episodeInfo?.overview
                        }
                    )
                }
            }
// Handling h4 elements

            doc.select("h4:matches(EPiSODE \\d+)").forEach { h4Element ->
                val episodeNumber = episodeRegex.find(h4Element.text())?.groupValues?.get(1)?.toIntOrNull() ?: (episodesData.size + 1)
                val episodeLinks = mutableListOf<String>()
                var nextElement = h4Element.nextElementSibling()
                val episodeInfo = responseData?.meta?.videos?.find { it.season == seasonNumber && it.episode == episodeNumber }

                while (nextElement != null && nextElement.tagName() != "hr") {
                    episodeLinks.addAll(
                        nextElement.select("a[href]").mapNotNull { it.attr("href").takeIf { link -> link.isNotEmpty() } }
                    )
                    nextElement = nextElement.nextElementSibling()
                }

                if (episodeLinks.isNotEmpty()) {
                    epLinksList.add(episodeLinks)

                    episodesData.add(
                        newEpisode(episodeLinks) {
                            this.name = episodeInfo?.name ?: "Episode $episodeNumber"
                            this.season = seasonNumber
                            this.episode = episodeNumber
                            this.posterUrl = episodeInfo?.thumbnail
                            this.description=episodeInfo?.overview
                        }
                    )
                }
            }


            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.backgroundPosterUrl = background
                this.year = year.toIntOrNull()
                this.plot = description ?: plot
                this.tags = genre ?: tags
                addActors(cast)
                addTrailer(trailer)
                addImdbUrl(imdbUrl)
            }
        }
    }

    @SuppressLint("NewApi")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linksList = data
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
        linksList.forEach { link->
           if (link.contains("?id="))
           {
               val encoded= getRedirectLinks(link.trim())
               loadExtractor(encoded,subtitleCallback, callback)
           }
           else
           {
               loadExtractor(link,subtitleCallback, callback)
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
                lowercaseCheck.contains("webrip") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("bluray") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") || lowercaseCheck.contains(
                    "hdtv"
                ) -> SearchQuality.HD

                else -> null
            }
        }
        return null
    }

    /**
     * Extracts a four-digit year from a string, prioritizing years in parentheses and ensuring no word characters follow.
     *
     * Example:
     *
     * "This is (2023) movie" -> 2023
     *
     * "This is 1920x1080p" -> null
     *
     * "This is 2023 movie" -> 2023
     *
     * "This is 1999-2010 TvSeries" -> 1999
     *
     * @param check The input string.
     * @return The year as an integer, or `null` if no match is found.
     */
    private fun getYearFromString(check: String?): Int? {
        return check?.let {
            parenthesesYear.find(it)?.value?.toIntOrNull()
                ?: withoutParenthesesYear.find(it)?.value?.toIntOrNull()
        }
    }

    private val parenthesesYear = "(?<=\\()\\d{4}(?=\\))".toRegex()
    private val withoutParenthesesYear = "(19|20)\\d{2}(?!\\w)".toRegex()

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

    data class EpisodeLink(
        val source: String
    )
}