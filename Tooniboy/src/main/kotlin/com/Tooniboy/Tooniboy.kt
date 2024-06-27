package com.Tooniboy

//import android.util.Log
import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.Base64

class Tooniboy : MainAPI() {
    override var mainUrl              = "https://www.tooniboy.com"
    override var name                 = "Tooniboy"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.Cartoon)

    override val mainPage = mainPageOf(
        "series" to "Series",
        "movies" to "Movies",
        "category/crunchyroll" to "Crunchyroll",
        "category/netflix" to "Netflix",
        "category/cartoon-network" to "Cartoon Network",
        "category/disney" to "Disney",
        "category/hungama" to "Hungama",
        "category/sony-yay" to "Sony Yay",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home     = document.select("#site article").mapNotNull { it.toSearchResult() }

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
        val title     = this.select("header > h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("figure > img").attr("data-src").toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearch(): SearchResponse {
        val title     = this.select("header > h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("figure > img").attr("src").toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("#site article").mapNotNull { it.toSearch() }

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
        val document = app.get(url).document
        val title= document.selectFirst("#site header h2.title")?.text()?.trim().toString().replace("Watch Online","")
        val poster = document.select("figure.im.brd1 img").attr("data-src")
        val description = document.selectFirst("#site article p")?.text()?.trim()
        val tags = document.select("div.rght.fg1 > a").map { it.text() }
        val tvtag=if (url.contains("series")) TvType.TvSeries else TvType.Movie
        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
                document.select("div.serie-sidebar article").forEach {
                        val href = it.selectFirst("a")?.attr("href") ?:""
                        val posterUrl=it.selectFirst("figure > img")?.attr("data-src")
                        val episode = it.select("header h2").text().toString()
                        val seasonnumber= it.selectFirst("header span")?.text()?.substringBefore("-")?.trim()?.toInt()
                        episodes.add(Episode(href, episode, posterUrl = posterUrl, season = seasonnumber))
                    }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.tags=tags
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.tags=tags
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("div.op-srv.brd1").forEach {
            val encodedlink=it.attr("data-src")
            val serverurl=base64ToUtf8(encodedlink) ?:""
            val truelink= app.get(serverurl).document.selectFirst("iframe")?.attr("src") ?:""
            Log.d("Phisher",truelink)
            if (truelink.contains("gdmirrorbot"))
            {
                val links=GDmirrorbot(truelink)
                links.forEach { url->
                    loadExtractor(url,subtitleCallback, callback)
                }
            }
            else
                if (truelink.contains("streamruby"))
                {
                    StreamRuby().getUrl(truelink)
                }
            else
                loadExtractor(truelink,subtitleCallback, callback)
        }
        return true
    }


    suspend fun GDmirrorbot(url: String): MutableList<String> {
        val urllist= mutableListOf<String>()
        val links= app.get(url).text
        val pattern="data-link='(.*?)'".toRegex()
        val matches=pattern.findAll(links)
        matches.forEach { matchResult ->
            val link = matchResult.groups[1]?.value
            link?.let {
                urllist.add(it)
            }
        }
        return urllist
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun base64ToUtf8(base64String: String): String? {
        return try {
            val decodedBytes = Base64.getDecoder().decode(base64String)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            println("Error decoding Base64: ${e.message}")
            null
        }
    }
}
