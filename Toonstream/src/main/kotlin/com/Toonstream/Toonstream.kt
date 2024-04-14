package com.Toonstream

//import android.util.Log
import android.annotation.SuppressLint
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Toonstream : MainAPI() {
    override var mainUrl              = "https://toonstream.net"
    override var name                 = "Toonstream"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.Cartoon)

    override val mainPage = mainPageOf(
        "series" to "Series",
        "movies" to "Movies",
        "category/cartoon" to "Cartoon",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home     = document.select("#movies-a > ul > li").mapNotNull { it.toSearchResult() }

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
        val title     = this.select("article  > header > h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("article  > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("article  > div.post-thumbnail > figure > img").attr("data-src").toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearch(): SearchResponse {
        val title     = this.select("article  > header > h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("article  > a").attr("href"))
        val posterUrlRaw = this.select("article  > div.post-thumbnail > figure > img").attr("src").toString()
        return if (posterUrlRaw.contains("http")) {
            val posterUrl=posterUrlRaw
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            val posterUrl="https:$posterUrlRaw"
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("#movies-a > ul > li").mapNotNull { it.toSearch() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    @SuppressLint("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title       = document.selectFirst("header.entry-header > h1")?.text()?.trim().toString().replace("Watch Online","")
        val posterraw = document.select("div.bghd > img").attr("data-src")
        val poster="https:$posterraw"
        val description = document.selectFirst("div.description > p")?.text()?.trim()
        val tvtag=if (url.contains("series")) TvType.TvSeries else TvType.Movie
        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.aa-drp.choose-season > ul > li > a").forEach { info->
                val data_post=info.attr("data-post")
                val data_season=info.attr("data-season")
                val season=app.post("$mainUrl/wp-admin/admin-ajax.php", data = mapOf(
                    "action" to "action_select_season",
                    "season" to data_season,
                    "post" to data_post
                ), headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).document
                    season.select("article").forEach {
                        val href = it.selectFirst("article >a")?.attr("href") ?:""
                        val posterRaw=it.selectFirst("article > div.post-thumbnail > figure > img")?.attr("src")
                        @Suppress("NAME_SHADOWING") val poster="https:$posterRaw"
                        val episode = it.select("article > header.entry-header > h2").text().toString()
                        val seasonnumber=season.toString().substringAfter("<span class=\"num-epi\">").substringBefore("x").toIntOrNull()
                        episodes.add(Episode(href, episode, posterUrl = poster, season = seasonnumber))
                    }
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("#aa-options > div > iframe").forEach {
            val serverlink=it.attr("data-src")
            loadExtractor(serverlink,subtitleCallback, callback)
        }
        return true
    }
}
