package com.latanime

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Latanime : MainAPI() {
    override var mainUrl              = "https://latanime.org"
    override var name                 = "Latanime"
    override val hasMainPage          = true
    override var lang                 = "mx"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "animes?fecha=false&genero=false&letra=false&categoria=anime" to "Anime",
        "animes?fecha=false&genero=false&letra=false&categoria=Película" to "Película",
        "animes?fecha=false&genero=false&letra=false&categoria=especial" to "Especial",
        "animes?fecha=false&genero=false&letra=false&categoria=donghua" to "Donghua",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&p=$page").documentLarge
        val home     = document.select("div.row a").mapNotNull { it.toSearchResult() }
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
        val title     = this.select("h3").text()
        val href      = this.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/buscar?q=$query").documentLarge
        val results =document.select("div.row a").mapNotNull { it.toSearchResult() }
        return results
    }


    private fun getTvType(text: String): TvType {
        return when {
            text.contains("Episodios", ignoreCase = true) -> TvType.TvSeries
            else -> TvType.Movie
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val title= document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "Unknown"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim() ?: "Unknown"
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim() ?: "Unknown"
        val rawtype= document.select("p:nth-child(4)").text()
        val type = getTvType(rawtype)
        val tags=document.select("a div.btn").map { it.text() }
        val href=fixUrl(document.select("div.grid > article a").attr("href"))
        return if (type==TvType.TvSeries)
        {
            val episodes = mutableListOf<Episode>()
            document.select("div.row div.row a").map {
                val epposter=it.select("img").attr("data-src")
                val ephref=it.attr("href")
                episodes.add(
                    newEpisode(ephref)
                    {
                        this.posterUrl=epposter
                    })

            }
            newAnimeLoadResponse(title, url, TvType.Anime) {
                addEpisodes(DubStatus.Subbed,episodes)
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        }
        else newMovieLoadResponse(title, url, TvType.Movie, href) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge
        document.select("#play-video a").map {
            val href= base64Decode( it.attr("data-player")).substringAfter("=")
            loadExtractor(
                href,
                "",
                subtitleCallback,
                callback
            )
        }
        return true
    }

    private fun Element.getImageAttr(): String? {
        return this.attr("data-src")
            .takeIf { it.isNotBlank() && it.startsWith("http") }
            ?: this.attr("src").takeIf { it.isNotBlank() && it.startsWith("http") }
    }
}