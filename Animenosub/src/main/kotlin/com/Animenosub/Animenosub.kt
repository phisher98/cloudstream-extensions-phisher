package com.Animenosub

//import android.util.Log
import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.Base64

class Animenosub : MainAPI() {
    override var mainUrl              = "https://animenosub.com/"
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
        val document = app.get("$mainUrl/${request.data}$page").document
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
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src").toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

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

    @SuppressLint("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title       = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href=document.selectFirst(".eplister li > a")?.attr("href") ?:""
        var poster = document.select("div.ime > img").attr("src").toString()
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type=document.selectFirst(".spe")?.text().toString()
        val tvtag=if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val Eppage= document.selectFirst(".eplister li > a")?.attr("href") ?:""
            val doc= app.get(Eppage).document
            @Suppress("NAME_SHADOWING") val episodes=doc.select("div.episodelist > ul > li").map { info->
                        val href = info.select("a").attr("href") ?:""
                        val episode = info.select("a span").text().substringAfter("-").substringBeforeLast("-")
                        val poster=info.selectFirst("a img")?.attr("src") ?:""
                        Episode(href, episode, posterUrl = poster)
            }
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

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select(".mobius option").forEach { server->
            val base64 = server.attr("value")
            val url=base64.decodeBase64()
            if (url.contains("vidmoly"))
            {
                val newurl=url.substringAfter("=\"").substringBefore("\"")
                val link= "http:$newurl"
                loadExtractor(link,referer = url,subtitleCallback, callback)
            }
            else {
                val link = url.substringAfter("src=\"").substringBefore("\"")
                if (!link.contains("http"))
                {
                    @Suppress("NAME_SHADOWING") val link = url.substringAfter("SRC=\"").substringBefore("\"")
                    loadExtractor(link, referer = link, subtitleCallback, callback)
                }
                else
                loadExtractor(link, referer = link, subtitleCallback, callback)
            }
        }
        return true
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun String.decodeBase64(): String {
        val decodedBytes = Base64.getDecoder().decode(this)
        return String(decodedBytes, Charsets.UTF_8)
    }
}
