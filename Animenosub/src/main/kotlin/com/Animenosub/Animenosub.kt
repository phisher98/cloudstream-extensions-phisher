package com.Animenosub

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Animenosub : MainAPI() {
    override var mainUrl              = "https://animenosub.to"
    override var name                 = "Animenosub"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?type=tv&page=" to "Anime",
        "anime/?status=&type=tv&sub=sub&page=" to "Anime (SUB)",
        "anime/?status=&type=tv&sub=dub&page=" to "Anime (DUB)",
        "anime/?status=&type=movie&page=" to "Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page").documentLarge
        val home     = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.bsx > a").attr("title")
        val href      = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").documentLarge

            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title       = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href=document.selectFirst(".eplister li > a")?.attr("href") ?:""
        var poster = document.select("div.ime > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type=document.selectFirst(".spe")?.text().toString()
        val tvtag=if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val episodes=document.select("div.eplister > ul > li").map { info->
                        val href1 = info.select("a").attr("href")
                        val episode = info.select("a div.epl-title").text().substringAfter("-").substringBeforeLast("-")
                        val posterr=info.selectFirst("a img")?.attr("src") ?:""
                        newEpisode(href1)
                        {
                            this.name=episode
                            this.posterUrl=posterr
                        }
            }.reversed()
            if (poster.isEmpty())
            {
                poster=document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            if (poster.isEmpty())
            {
                poster=document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            }
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        document.select(".mobius option").amap { server ->
            val base64 = server.attr("value")
            val iframe = Jsoup.parse(base64Decode(base64)).select("iframe").attr("src")
            if (iframe.startsWith("//"))
            {
                val fixiframe=fixUrl(iframe)
                loadExtractor(fixiframe,referer = fixiframe,subtitleCallback, callback)
            }
            else {
                Log.d("Phisher",iframe)
                loadExtractor(iframe, referer = iframe, subtitleCallback, callback)
            }
        }
        return true
    }
}
