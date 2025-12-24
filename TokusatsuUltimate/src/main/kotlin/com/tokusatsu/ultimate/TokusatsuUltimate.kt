package com.tokusatsu.ultimate

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup

class TokusatsuUltimate : MainAPI() {
    override var mainUrl = "https://toku555.com/"
    override var name = "TokusatsuUltimate"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.TvSeries, TvType.Movie)

    companion object {
        private val mapper = jacksonObjectMapper()
        
        fun getType(t: String): TvType {
            return when {
                t.contains("movie", ignoreCase = true) -> TvType.Movie
                t.contains("tv", ignoreCase = true) || t.contains("series", ignoreCase = true) -> TvType.TvSeries
                else -> TvType.Anime
            }
        }

        fun getRating(score: String?): String? {
            if (score == null) return null
            return try {
                (score.toFloat() * 10).toString()
            } catch (e: Exception) {
                score
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when {
                t?.contains("Ongoing", ignoreCase = true) == true -> ShowStatus.Ongoing
                t?.contains("Completed", ignoreCase = true) == true -> ShowStatus.Completed
                else -> ShowStatus.Completed
            }
        }

        fun cleanTitle(title: String): String {
            // Remove common suffixes and clean up the title
            var cleaned = title
            val suffixes = listOf(" - Tokusatsu", " | Tokusatsu", " | Official")
            for (suffix in suffixes) {
                if (cleaned.endsWith(suffix)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length)
                }
            }
            return cleaned.trim()
        }
    }

    override val mainPage = mainPageOf(
        "kamen-rider/" to "Kamen Rider Series",
        "super-sentai/" to "Super Sentai Series",
        "tokusatsu-anime/" to "Tokusatsu Anime",
        "metal-heroes/" to "Metal Heroes",
        "recent/" to "Recently Updated",
        "popular/" to "Popular Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.endsWith("/")) {
            "${mainUrl}${request.data}page/$page/"
        } else {
            "${mainUrl}${request.data}/page/$page/"
        }
        
        val document = app.get(url).document
        val home = document.select("div.film-poster, .item, .series-item").mapNotNull { element ->
            val titleElement = element.selectFirst(".film-title a, .title a, h3 a") ?: element.selectFirst("a")
            
            if (titleElement != null) {
                val title = cleanTitle(titleElement.text().trim())
                val href = fixUrl(titleElement.attr("href"))
                val posterElement = element.selectFirst(".film-poster img, img")
                val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

                newAnimeSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = posterUrl
                }
            } else {
                null
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/${query}/"
        val document = app.get(searchUrl).document

        return document.select("div.film-poster, .item, .series-item").mapNotNull { element ->
            val titleElement = element.selectFirst(".film-title a, .title a, h3 a") ?: return@mapNotNull null
            val title = cleanTitle(titleElement.text().trim())
            val href = fixUrl(titleElement.attr("href"))
            val posterElement = element.selectFirst(".film-poster img, img")
            val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val titleElement = document.selectFirst("h1.heading-title, .title, .film-name") ?: 
            throw ErrorLoadingException("No title found")
        val title = cleanTitle(titleElement.text().trim())

        val posterElement = document.selectFirst(".film-poster img, .poster img, img[src*='image']")
        val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

        val year = document.selectFirst(".year, .date, .released")?.text()?.trim()
            ?.toIntOrNull()

        val description = document.selectFirst(".description, .summary, .overview, .synopsis, .content")?.text()?.trim()

        val tags = document.select(".genres a, .tags a, .category a").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        // Look for episode list
        document.select(".listing.items .item, .episodes .episode, .season-episodes .episode, .listing a").forEach { epElement ->
            val epTitleElement = epElement.selectFirst(".title, .episode-title, .name")
            val epHrefElement = epElement.selectFirst("a")
            val epNum = epElement.selectFirst(".num, .episode-num")?.text()?.trim()?.toIntOrNull()
            
            if (epHrefElement != null) {
                val epTitle = epTitleElement?.text()?.trim() ?: "Episode ${epNum ?: episodes.size + 1}"
                val epHref = fixUrl(epHrefElement.attr("href"))
                
                episodes.add(
                    newEpisode(epHref) {
                        this.name = epTitle
                        this.episode = epNum
                    }
                )
            }
        }

        // If no episodes found, try to find them in a different structure
        if (episodes.isEmpty()) {
            document.select("a[href*='/episode/'], a[href*='/watch/'], a[href*='/season/']").forEach { link ->
                val epNum = try {
                    val href = link.attr("href")
                    val regex = Regex("""(?:episode|ep|epi|watch|season).*?(\d+)""", RegexOption.IGNORE_CASE)
                    regex.find(href)?.groupValues?.get(1)?.toIntOrNull()
                } catch (e: Exception) {
                    null
                }
                
                val epTitle = link.text().trim().ifEmpty { 
                    "Episode ${episodes.size + 1}" 
                }
                
                episodes.add(
                    newEpisode(fixUrl(link.attr("href"))) {
                        this.name = epTitle
                        this.episode = epNum
                    }
                )
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = posterUrl
            this.year = year
            this.plot = description
            this.tags = tags
            
            addEpisodes(DubStatus.Subbed, episodes.reversed())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Look for embedded players or video sources 
        document.select("iframe, .player-iframe, .video-player, .play-video").forEach { element ->
            val iframeSrc = element.attr("src")
            if (iframeSrc.isNotEmpty()) {
                loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            }
        }

        // Look for video player links
        document.select("a[href*='player'], a[href*='watch']").forEach { link ->
            val linkHref = link.attr("href")
            if (linkHref.isNotEmpty()) {
                loadExtractor(fixUrl(linkHref), data, subtitleCallback, callback)
            }
        }

        // Look for direct video links
        document.select("script").forEach { script ->
            val scriptText = script.data().toString()
            // Look for video URLs in JavaScript
            val videoRegex = Regex("""(https?://[\w\d\-_.~:/?#\[\]@!\$&'()*+,;=%]+\.(?:mp4|m3u8|mkv|webm)[\w\d\-_.~:/?#\[\]@!\$&'()*+,;=%]*)""")
            videoRegex.findAll(scriptText).forEach { match ->
                val videoUrl = match.value
                callback.invoke(
                    newExtractorLink(
                        name,  // source
                        name,  // name
                        videoUrl,  // url
                        if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO  // type
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.headers = mapOf("Referer" to data)
                    }
                )
            }
        }

        return true
    }
}