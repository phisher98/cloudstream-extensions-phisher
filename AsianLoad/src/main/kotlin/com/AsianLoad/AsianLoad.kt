package com.AsianLoad

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

open class AsianLoad : MainAPI() {
    override var mainUrl = "https://embasic.pro"
    override var name = "AsianLoad"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "popular" to "Popular Series",
        "ongoing-series" to "Ongoing Series",
        "kshow" to "KDrama",
        "movies" to "Movies",
        "recently-added-raw" to "Recently Added Raw",
        "" to "Recently Added SUB"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val home = document.select("ul.listing.items li.video-block a").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.attr("href") ?: return null)
        val title = this.selectFirst("div.name")?.text()!!.substringBefore("Episode").trim()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search.html?keyword=$query").document
        return document.select("ul.listing.items li.video-block a").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("div.video-details span")?.text()?.substringBefore("Episode")?.trim() ?:"No Title"
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description=document.selectFirst("div.post-entry div")?.text()
        val episodes = document.select("ul.listing.items.lists li.video-block a").map {
            val name = it.selectFirst("div.name")?.text()?.substringAfter("Episode")?.trim()
            val link = fixUrlNull(it.attr("href"))
            val epNum = name?.toIntOrNull()
            val epposter=it.selectFirst("img")?.attr("src")
            newEpisode(link) {
                this.name = "Episode $name"
                this.episode = epNum
                this.posterUrl=epposter
            }
        }.reversed()

        if (episodes.size == 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, episodes[0].data) {
                posterUrl = poster
                plot=description
            }
        } else {
            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes = episodes) {
                posterUrl = poster
                plot=description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val href = app.get(data).document.selectFirst("iframe")?.attr("src") ?:""
        /*
        val script = app.get(data).document.selectFirst("script:containsData(pageProps)")?.data().toString().substringAfter("[").substringBefore("]")
        val regex = """"url":"(.*?)"""".toRegex()
        val matches = regex.findAll(script)
        matches.forEach { match ->
            val url = match.groupValues[1] .replace("\\u0026", "&") // The first capturing group is the URL
           Log.d("Phisher URL",url)
            loadExtractor(url,subtitleCallback,callback)
        }
         */
        loadExtractor(href,subtitleCallback,callback)
        return true
    }
}