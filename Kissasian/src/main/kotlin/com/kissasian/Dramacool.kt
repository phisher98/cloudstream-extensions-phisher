package com.kissasian

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Dramacool : MainAPI() {
    override val supportedTypes = setOf(
        TvType.AsianDrama
    )
    override var lang = "en"
    override var mainUrl = "https://dramacool.city"
    override var name = "Dramacool"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "dramas" to "Drama",
        "movies" to "Movies",
        "k-shows" to "KShow",
        "most-watched" to "Popular Dramas",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page==1)
        {
            val document = app.get("$mainUrl/${request.data}").document
            val items = document.select(".box > li").mapNotNull {
                it.toSearchResult()
            }
            return newHomePageResponse(request.name, items)
        }
        else
        {
            val document = app.get("$mainUrl/${request.data}/page/$page").document
            val items = document.select(".box > li").mapNotNull {
                it.toSearchResult()
            }

            return newHomePageResponse(request.name, items)
        }

    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, LoadUrl(href, posterUrl).toJson()) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..5) {
            val document = app.get("${mainUrl}/discover/page/$i/?keyword=$query").document

            val results =
                document.select(".list-thumb li").mapNotNull {
                    val a = it.selectFirst("a") ?: return@mapNotNull null
                    val name = it.selectFirst("h2")?.text() ?: return@mapNotNull null
                    val posterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))
                    val href = a.attr("href")
                    newMovieSearchResponse(name, LoadUrl(href, posterUrl).toJson()) {
                        this.posterUrl = posterUrl
                    }
                }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = tryParseJson<LoadUrl>(url) ?: return null
        val document = app.get(d.url, referer = "$mainUrl/").document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".film-poster img")?.attr("src") ?: return null
        val description = document.selectFirst(".text_above_player > p")?.text()
        
        val episodes = document.select("#all-episodes ul li").mapNotNull { el ->
            el.select("a").mapNotNull {
                val href = fixUrl(it.attr("data-source"))
                newEpisode(href) {
                    name = it.text().trim()
                }
            }
        }.flatten()


        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = d.posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.substringAfter("id=")
        val json = app.get("https://dramasb.com/stream?id=$id", verify = false).text
        val linksRegex = "\"link\":\"(.*?)\"".toRegex()
        val servers = linksRegex.findAll(json).map {
            it.groupValues[1].replace("\\/", "/")
        }.toList()
        Log.d("Test12","$servers")
        servers.forEach{ url->
            if (url.contains("asian"))
            {
                val doc= app.get(url).document
                val links=doc.select("ul > li.linkserver").toString()
                val regex="data-video=\"(.*)\\?c".toRegex()
                //Log.d("Test12", links.toString())
                val allservers = regex.findAll(links).map {
                    it.groupValues[1]
                }.toList()
                Log.d("Test12", allservers.toString())
                allservers.forEach{
                    Log.d("Test12",it)
                    loadExtractor(it, subtitleCallback, callback)
                }
            }else
            {
                loadExtractor(url, subtitleCallback, callback)
            }
        }

        return true
    }

    data class LoadUrl(
        val url: String,
        val posterUrl: String?
    )

}
