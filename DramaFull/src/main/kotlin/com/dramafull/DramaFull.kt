package com.dramafull

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DramaFull : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "DramaFull"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie,TvType.TvSeries,TvType.AsianDrama)

    override val mainPage: List<MainPageData>
        get() {
            val basePages = mutableListOf(
                MainPageData("Recently Added", "-1:1"),
                MainPageData("TV-Shows", "1:3"),
                MainPageData("Movies", "2:4"),
                MainPageData("Most Watched", "-1:5")
            )

            // Only add adult sections if adult mode is enabled
            if (settingsForProvider.enableAdult) {
                basePages.addAll(
                    listOf(
                        MainPageData("Adult Recently Added", "-1:1:adult"),
                        MainPageData("Adult Movies", "2:6:adult"),
                        MainPageData("Adult TV-Shows", "1:3:adult"),
                        MainPageData("Adult Most Watched", "-1:5:adult"),
                        )
                )
            }

            return basePages
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (type, sort, adultFlag) = request.data.split(":").let {
            val t = it.getOrNull(0) ?: "-1"
            val s = it.getOrNull(1)?.toIntOrNull() ?: 1
            val a = it.getOrNull(2) ?: "normal"
            Triple(t, s, a)
        }

        val isAdultSection = adultFlag == "adult"

        val jsonPayload = """{
        "page": $page,
        "type": "$type",
        "country": -1,
        "sort": $sort,
        "adult": ${settingsForProvider.enableAdult},
        "adultOnly": $isAdultSection,
        "ignoreWatched": false,
        "genres": [],
        "keyword": ""
        }""".trimIndent()

        val payload = jsonPayload.toRequestBody("application/json".toMediaType())

        val home = app.post("$mainUrl/api/filter", requestBody = payload)
            .parsedSafe<Home>()
            ?.data
            ?.mapNotNull  { it.toSearchResult() }
            ?: emptyList()

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Daum.toSearchResult(): SearchResponse? {
        if (!settingsForProvider.enableAdult && this.isAdult.toInt() == 1) {
            return null
        }
        val title = this.name
        val href = "$mainUrl/film/${this.slug}"
        val poster = mainUrl + this.image
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/api/live-search/$query"
        return app.get(url)
            .parsedSafe<Search>()
            ?.data
            ?.mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).documentLarge
        val title = doc.selectFirst("div.right-info h1")?.text() ?: "UnKnown"
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")) ?: ""
        val genre = doc.select("div.genre-list a").map { it.text() }
        val year = title.substringAfterLast("(").substringBefore(")").toIntOrNull()
        val descript = doc.selectFirst("div.right-info p.summary-content")?.text()
        val type = if (doc.select("div.tab-content.episode-button").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val href= doc.select("div.last-episode a").attr("href")

        val recs = doc.select("div.film_list-wrap div.flw-item").mapNotNull {
            val a = it.select("img")
            val title = a.attr("alt")
            val aImg = a.attr("data-src")
            val href = it.select("a").attr("href")
            newMovieSearchResponse(title, href, TvType.Movie)
            {
                this.posterUrl = aImg
            }
        }


        if (type == TvType.TvSeries)
        {
            val episodes= doc.select("div.episode-item a").map {
                val title = it.text().substringBefore("(").trim()
                val epno = title.toIntOrNull()
                val href=it.attr("href")

                newEpisode(href)
                {
                    this.name = "Episode $title"
                    this.episode = epno
                }

            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes)
            {
                this.year = year
                this.tags = genre
                this.posterUrl = poster
                this.plot = descript
                this.recommendations = recs
            }
        }
        else
        {
            return newMovieLoadResponse(title, url, TvType.Movie, href)
            {
                this.year = year
                this.tags = genre
                this.posterUrl = poster
                this.plot = descript
                this.recommendations = recs
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).documentLarge
        val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
        val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/","/") ?: return false

        val res = app.get(signedUrl).text
        val resJson = JSONObject(res)
        val videoSource = resJson.optJSONObject("video_source") ?: return false
        val qualities = videoSource.keys().asSequence().toList()
            .sortedByDescending { it.toIntOrNull() ?: 0 }
        val bestQualityKey = qualities.firstOrNull() ?: return false
        val bestQualityUrl = videoSource.optString(bestQualityKey)


        callback(
            newExtractorLink(
                name,
                name,
                bestQualityUrl
            )
        )

        val subJson = resJson.optJSONObject("sub")
        subJson?.optJSONArray(bestQualityKey)?.let { array ->
            for (i in 0 until array.length()) {
                subtitleCallback(newSubtitleFile("English", mainUrl+array.getString(i)))
            }
        }
        return true
    }

}