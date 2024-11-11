package com.AsianLoad

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

open class AsianLoad : MainAPI() {
    override var mainUrl = "https://www.asianhdplay.in"
    override var name = "AsianLoad"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "popular" to "Popular Series",
        "status/ongoing" to "Ongoing Series",
        "status/completed" to "Completed Series",
        "kshow" to "KDrama",
        "movies" to "Movies",
        "recently-added-raw" to "Recently Added Raw",
        "" to "Recently Added SUB"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val home = document.select("div a.group").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.attr("href") ?: return null)
        val title = this.selectFirst("p")?.text()!!.substringBefore("Episode").trim()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src")?.replace("png","webp"))
        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?keywords=$query").document
        return document.select("div a.group").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.mb-4 h1")?.text()?.substringBefore("Episode")?.trim() ?:"No Title"
        val poster = document.selectFirst("a.group div img")?.attr("src")

        val episodes = document.select("div.mt-4 a").map {
            val name = it.selectFirst("div p")?.ownText()?.substringAfter("Episode")?.trim()
            val link = fixUrlNull(it.attr("href"))
            val epNum = name?.toIntOrNull()
            newEpisode(link) {
                this.name = name
                this.episode = epNum
            }
        }.reversed()

        if (episodes.size == 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, episodes[0].data) {
                posterUrl = poster
            }
        } else {
            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes = episodes) {
                posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        //val href = app.get(data).document.selectFirst("iframe")?.attr("src")
        val script = app.get(data).document.selectFirst("script:containsData(pageProps)")?.data().toString().substringAfter("[").substringBefore("]")
        val regex = """"url":"(.*?)"""".toRegex()
        val matches = regex.findAll(script)
        matches.forEach { match ->
            val url = match.groupValues[1] .replace("\\u0026", "&") // The first capturing group is the URL
           Log.d("Phisher URL",url)
            loadExtractor(url,subtitleCallback,callback)
        }
        return true
    }
}