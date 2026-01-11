package com.Filmyfiy

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

class Filmyfiy : MainAPI() {
    override var mainUrl: String = runBlocking {
        FilmyfiyProvider.getDomains()?.Filmyfiy ?: "https://www.filmyfiy.mov"
    }
    override var name = "Filmyfiy"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "" to "Homepage",
        "page-3/10/All-Time-Best-Bollywood-Hindi-Movies" to "Bollywood Movies",
        "page-3/42/Web-Series" to "Web Series",
        "page-3/58/HQ-Dubbed-Movies-UnCut" to "HQ Dubbed Movies",
        "page-3/11/South-Hindi-Dubbed-Movies-Collection" to "South Hindi Dubbed Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = try {
            app.get("$mainUrl/${request.data}/$page").document
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch main page: ${e.message}")
        }
        val items = document.select("tr").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false),
            hasNext = true
        )
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("td[align=left] a:has(b), td[align=left] a:not(:has(img))")
            ?: return null
        val img = selectFirst("img") ?: return null

        val href = fixUrl(aTag.attr("href"))
        val rawText = aTag.text().trim()

        val title = rawText.substringBefore("(").trim()

        val lang = rawText
            .substringAfter("(", "")
            .substringBefore(")")
            .takeIf { it.isNotBlank() }

        val fullTitle = lang?.let { "$title [$it]" } ?: title

        val posterUrl = fixUrlNull(img.attr("src"))
        val qualityText = selectFirst("div.quality")?.text()

        return newMovieSearchResponse(fullTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(qualityText)
        }
    }


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val document = app.get("${mainUrl}/site-1.html?to-search=$query").documentLarge
        val results = document.select("div.A2").mapNotNull { el ->
                val aTag = el.selectFirst("a:has(b), a:not(:has(img))") ?: return@mapNotNull null
                val img = el.selectFirst("img") ?: return@mapNotNull null

                val href = fixUrl(aTag.attr("href"))
                val rawText = aTag.text().trim()

                val title = rawText.substringBefore("(").trim()
                val extra = rawText
                    .substringAfter("(", "")
                    .substringBefore(")")
                    .takeIf { it.isNotBlank() }

                val fullTitle = extra?.let { "$title [$it]" } ?: title
                val posterUrl = fixUrlNull(img.attr("src"))

                newMovieSearchResponse(fullTitle, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }.toNewSearchResponseList()
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title = document.selectFirst("div.fname:contains(Name:) > div")?.text()?.substringBefore("(")?.trim() ?: "Unknown Title"
        val href = document.selectFirst("div.dlbtn a")?.attr("href") ?: ""
        val poster = document.select("div.movie-thumb img").attr("src")
        val backgroundPoster = document.selectFirst("div.cover img")?.attr("src") ?: poster
        val actors =document
            .selectFirst("div.fname:contains(Starcast:) > div")
            ?.text()
            ?.split(",", " and ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val genres = document
            .selectFirst("div.fname:contains(Genre:) > div")
            ?.text()
            ?.split(",", " and ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val year = document.selectFirst("div.fname:contains(Release Date:) > div")?.text()?.trim()?.toIntOrNull()
        val duration = document.selectFirst("div.fname:contains(Duration:) > div")?.text()?.trim()
        val description = document.selectFirst("div.fname:contains(Description:) > div")?.text()?.trim()
        val tvtag = if (Regex("""(?i)\b(s(eason)?\s*\d{1,2})\b""").containsMatchIn(url)) TvType.TvSeries else TvType.Movie


        val recommendations = document.select("tr").mapNotNull { it.toSearchResult() }

        return if (tvtag == TvType.TvSeries) {
            newMovieLoadResponse(title, url, TvType.TvSeries,href) {
                this.posterUrl = poster
                this.year = year
                this.backgroundPosterUrl = backgroundPoster
                this.plot = description
                this.duration = getDurationFromString(duration)
                this.tags = genres
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.year = year
                this.backgroundPosterUrl = backgroundPoster
                this.plot = description
                this.duration = getDurationFromString(duration)
                this.tags = genres
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge
        document.select("div.dlink.dl a").forEach {
            val href= it.attr("href")
            if (href.contains("filesdl"))
            {
                Filesdl().getUrl(href,"",subtitleCallback,callback)
            }
            else loadExtractor(href,"",subtitleCallback,callback)
        }
        return true
    }
}

