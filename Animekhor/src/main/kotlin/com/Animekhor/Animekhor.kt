package com.Animekhor

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class Animekhor : MainAPI() {
    override var mainUrl              = "https://animekhor.org"
    override var name                 = "Animekhor"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=ongoing&type=&order=update" to "Recently Updated",
        "anime/?type=comic&order=update" to "Comic Recently Updated",
        "anime/?type=comic" to "Comic Series",
        "anime/?status=&type=ona&sub=&order=update" to "Donghua Recently Updated",
        "anime/?status=&type=ona" to "Donghua Series",
        "anime/?status=&sub=&order=latest" to "Latest Added",
        "anime/?status=&type=&order=popular" to "Popular",
        "anime/?status=completed&order=update" to "Completed",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").documentLarge
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
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx > a img")?.getsrcAttribute())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearchquery(): SearchResponse {
        val title     = this.select("div.bsx > a").attr("title")
        val href      = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx > a img")?.getsrcAttribute())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").documentLarge

            val results = document.select("div.listupd > article").mapNotNull { it.toSearchquery() }

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
        val title= document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href=document.selectFirst(".eplister li > a")?.attr("href") ?:""
        var poster = document.select("meta[property=og:image]").attr("content")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type=document.selectFirst(".spe")?.text().toString()
        val tvtag=if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val Eppage= document.selectFirst(".eplister li > a")?.attr("href") ?:""
            val doc= app.get(Eppage).documentLarge
            val epposter = doc.select("meta[property=og:image]").attr("content")
            val episodes=doc.select("div.episodelist > ul > li").map { info->
                        val href1 = info.select("a").attr("href")
                        val episode = info.select("a span").text().substringAfter("-").substringBeforeLast("-")
                        newEpisode(href1)
                        {
                            this.name=episode
                            this.posterUrl=epposter
                        }
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
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
        document.select(".mobius option").forEach { server->
            val base64 = server.attr("value")
            val regex = Regex("""src=["']([^"']+)["']""",RegexOption.IGNORE_CASE)
            val decodedUrl = base64Decode(base64)
            val matchResult = regex.find(decodedUrl)
            var url = matchResult?.groups?.get(1)?.value ?: "Not found"
            if (url.startsWith("//"))
            {
                    url= httpsify(url)
            }
            Log.d("Phisher",url)
                loadExtractor(url, referer = mainUrl, subtitleCallback, callback)

        }
        return true
    }

    private fun Element.getsrcAttribute(): String {
        val src = this.attr("src")
        val dataSrc = this.attr("data-src")

        return when {
            src.startsWith("http") -> src
            dataSrc.startsWith("http") -> dataSrc
            else -> ""
        }
    }

}
