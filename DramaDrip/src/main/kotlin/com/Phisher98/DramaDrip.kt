package com.Phisher98

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

class DramaDrip : MainAPI() {
    override var mainUrl              = "https://dramadrip.com"
    override var name                 = "DramaDrip"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.AsianDrama,TvType.TvSeries)
    private val cinemeta_url = "https://v3-cinemeta.strem.io/meta"

    companion object {
        private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        private var cachedDomains: DomainsParser? = null

        suspend fun getDomains(forceRefresh: Boolean = false): DomainsParser? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<DomainsParser>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }
    }

    override val mainPage = mainPageOf(
        "drama/ongoing" to "Ongoing Dramas",
        "latest" to "Latest Releases",
        "drama/chinese-drama" to "Chinese Dramas",
        "drama/japanese-drama" to "Japanese Dramas",
        "drama/korean-drama" to "Korean Dramas",
        "movies" to "Movies",
        "web-series" to "Web Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
        val href = this.select("h2.entry-title > a").attr("href")
        val imgElement = this.selectFirst("img")
        val srcset = imgElement?.attr("srcset")

        val highestResUrl = srcset
            ?.split(",")
            ?.map { it.trim() }
            ?.mapNotNull {
                val parts = it.split(" ")
                if (parts.size == 2) parts[0] to parts[1].removeSuffix("w").toIntOrNull() else null
            }
            ?.maxByOrNull { it.second ?: 0 }
            ?.first

        val posterUrl = highestResUrl ?: imgElement?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val dramadripAPI = getDomains()?.dramadrip
        val document = app.get("$dramadripAPI/?s=$query").document
        val results = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }

            if (tmdbId == null && tmdbType == null && "themoviedb.org" in text) {
                Regex("/(movie|tv)/(\\d+)").find(text)?.let { match ->
                    tmdbType = match.groupValues[1] // movie or tv
                    tmdbId = match.groupValues[2]   // numeric ID
                }
            }
        }
        val tvType = when (true) {
            (tmdbType?.contains("Movie",ignoreCase = true) == true) -> TvType.Movie
            else -> TvType.TvSeries
        }

        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()?.substringBefore("(")?.trim().toString()
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val descriptions = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val typeset = if (tvType == TvType.TvSeries) "series" else "movie"
        val responseData = if (tmdbId?.isNotEmpty() == true) {
            val jsonResponse = app.get("$cinemeta_url/$typeset/$imdbId.json").text
            if(jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                val gson = Gson()
                gson.fromJson(jsonResponse, ResponseData::class.java)
            } else null } else null
        var cast: List<String> = emptyList()

        var background: String = image
        var description: String? = null
        if(responseData != null) {
            description = responseData.meta?.description ?: descriptions
            cast = responseData.meta?.cast ?: emptyList()
            background = responseData.meta?.background ?: image
        }


        val hrefs: List<String> = document.select("div.wp-block-button > a")
            .mapNotNull { linkElement ->
                val link = linkElement.attr("href")
                val page = app.get(link).document
                page.select("div.wp-block-button.movie_btn a")
                    .eachAttr("href")
            }.flatten()

        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")

        val recommendations = document.select("div.entry-related-inner-content article").mapNotNull {
            val recName = it.select("h3").text().substringAfter("Download")
            val recHref = it.select("h3 a").attr("href")
            val recPosterUrl = it.select("img").attr("src")
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        if (tvType == TvType.TvSeries) {
            val tvSeriesEpisodes = mutableMapOf<Pair<Int, Int>, MutableList<String>>()

            val seasonBlocks = document.select("div.su-accordion h2")

            for (seasonHeader in seasonBlocks) {
                val seasonText = seasonHeader.text()
                if (seasonText.contains("ZIP", ignoreCase = true)) {
                    Log.d("Skip", "Skipping ZIP season: $seasonText")
                } else {
                    val seasonMatch = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                        .find(seasonText)
                    val season = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

                    if (season != null) {
                        val linksBlock = seasonHeader.nextElementSibling()
                        val qualityLinks = linksBlock?.select("div.wp-block-button a")
                            ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                            ?.distinct() ?: emptyList()

                        for (qualityPageLink in qualityLinks) {
                            try {
                                val episodeDoc = app.get(qualityPageLink).document
                                val episodeButtons = episodeDoc.select("a:contains(Episode)")

                                for (btn in episodeButtons) {
                                    val ephref = btn.attr("href")
                                    val epText = btn.text()

                                    if (ephref.isNotBlank()) {
                                        val epNo = Regex("""Episode\s*0*([0-9]+)""", RegexOption.IGNORE_CASE)
                                            .find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                                            ?: Regex("""\b([0-9]{1,2})\b""")
                                                .find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()

                                        if (epNo != null) {
                                            val key = season to epNo
                                            tvSeriesEpisodes.getOrPut(key) { mutableListOf() }.add(ephref)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("EpisodeFetch", "Failed to load: $qualityPageLink $e")
                            }
                        }
                    }
                }
            }

            val finalEpisodes = tvSeriesEpisodes.map { (seasonEpisode, links) ->
                val (season, epNo) = seasonEpisode
                val info = responseData?.meta?.videos?.find { it.season == season && it.episode == epNo }

                newEpisode(links.distinct().toJson()) {
                    this.name=info?.name ?: "Episode $epNo"
                    this.posterUrl=info?.thumbnail
                    this.season = season
                    this.episode = epNo
                    this.description = info?.overview
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, finalEpisodes) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        }
        else {
            return newMovieLoadResponse(title, url, TvType.Movie, hrefs) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = tryParseJson<List<String>>(data).orEmpty()
        if (links.isEmpty()) return false
        for (link in links) {
            try {
                val finalLink = if ("unblockedgames" in link) {
                    bypassHrefli(link)
                } else {
                    link
                }
                if (finalLink != null) {
                    loadExtractor(finalLink, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                Log.e("LoadLinks", "Failed to load link: $link $e")
            }
        }
        return true
    }

}
