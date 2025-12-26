package com.Filmycab

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

class Filmycab : MainAPI() {
    override var mainUrl: String = runBlocking {
        FilmycabProvider.getDomains()?.Filmycab ?: "https://filmycab.casa"
    }
    override var name = "FilmyCab"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "" to "Homepage",
        "page-cat/6/Animation-Movies.html" to "Animation Movies",
        "page-cat/5/Web-Series.html" to "Web Series",
        "page-cat/12/Bengali-Movies.html" to "Bengali Movies",
        "page-cat/11/Bhojpuri-Movies.html" to "Bhojpuri Movies",
        "page-cat/1/Bollywood-Movies.html" to "Bollywood Movies",
        "page-cat/9/Gujarati-Movies.html" to "Gujarati Movies",
        "page-cat/7/Hindi-HQ-Dub-Movies.html" to "Hindi HQ Dub Movies",
        "page-cat/4/Hollywood-Movies.html" to "Hollywood Movies",
        "page-cat/8/Marathi-Movies.html" to "Marathi Movies",
        "page-cat/10/Odia-Movie.html" to "Odia Movie",
        "page-cat/3/Punjabi-Movies.html" to "Punjabi Movies",
        "page-cat/2/South-Movies.html" to "South Movies",
    )



    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = try {
            app.get("$mainUrl/${request.data}/?to-page=$page").documentLarge
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch main page: ${e.message}")
        }
        val items = document.select("div.thumb").mapNotNull { it.toSearchResult() }

        if (items.size < 4) {
            return newHomePageResponse(emptyList(), hasNext = false)
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false),
            hasNext = true
        )
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("a") ?: return null
        val img = selectFirst("img") ?: return null
        val href = fixUrl(aTag.attr("href"))
        val title = img.attr("alt").substringBefore("(").trim()
        val lang = selectFirst("div.lang")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val fullTitle = if (lang != null) "$title [$lang]" else title
        val posterUrl = fixUrlNull(img.attr("src"))
        val quality = selectFirst("div.quality")?.text()
        return newMovieSearchResponse(fullTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val document = app.get("${mainUrl}/site-search.html?to-search=$query&to-page=$page").documentLarge
        val results = document.select("div.thumb").mapNotNull { it.toSearchResult() }.toNewSearchResponseList()
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title = document.selectFirst("div.l1:contains(Name:)")?.ownText()?.substringBefore("(") ?: "Unknown Title"
        val href = document.selectFirst("div.dlbtn a")?.attr("href") ?: ""
        val poster = document.select("div.thumbb > img").attr("src")
        val backgroundPoster = document.selectFirst("div.cover img")?.attr("src") ?: poster
        val actors = document
            .selectFirst("div.l1:contains(Starcast:)")
            ?.text()
            ?.substringAfter("Starcast:", "")
            ?.split(",", " and ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val genres = document
            .selectFirst("div.l1:contains(Genre:)")
            ?.text()
            ?.substringAfter("Genre:", "")
            ?.split(",", " and ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val year = document.selectFirst("div.l1:contains(Release Date:)")?.ownText()?.trim()?.toIntOrNull()
        val description = document.selectFirst("div.l1:contains(Summary:)")?.text()?.trim()
        val typedetails = document.select("div.l1:contains(Name:)").text()
        val tvtag = if (typedetails.contains("Series",ignoreCase = true)) TvType.TvSeries else TvType.Movie


        val recommendations = document.select("div.thumb").mapNotNull { it.toSearchResult() }

        return if (tvtag == TvType.TvSeries) {
            newMovieLoadResponse(title, url, TvType.TvSeries,href) {
                this.posterUrl = poster
                this.year = year
                this.backgroundPosterUrl = backgroundPoster
                this.plot = description
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
        document.select("div.dlink a").forEach {
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

