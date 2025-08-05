package com.Coflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class Coflix : MainAPI() {
    override var mainUrl              = "https://coflix.cc"
    override var name                 = "Coflix"
    override val hasMainPage          = true
    override var lang                 = "fr"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.TvSeries)
    private  val coflixAPI             = "$mainUrl/wp-json/apiflix/v1"

    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "series" to "Series",
        "doramas" to "Doramas",
        "animes" to "Animes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("$coflixAPI/options/?years=&post_type=${request.data}&genres=&page=$page&sort=1").parsedSafe<Response>()
        val home= res?.results?.map { it.toSearchResult() }
        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home!!,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Result.toSearchResult(): SearchResponse {
        val title     = this.name
        val href      = fixUrl(this.url)
        val posterUrl = fetchImageUrl(this.path)
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Search2.toSearchResult(): SearchResponse {
        val title     = this.title
        val href      = fixUrl(this.url)
        val posterUrl = fetchImageUrl(this.image)
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun fetchImageUrl(html: String): String? {
        val document: Document = Jsoup.parse(html)
        val imgElement = document.selectFirst("img")
        val src = imgElement?.attr("src")
        return if (src?.startsWith("//") == true) {
            "https:$src"
        } else {
            src
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.get("$mainUrl/suggest.php?query=$query").toString().toJson()
        val objectMapper = jacksonObjectMapper()
        val parsedResponse: Search = objectMapper.readValue(json)
        val response = mutableListOf<SearchResponse>()
        parsedResponse.forEach { searchItem ->
            try {
                val searchResponse = searchItem.toSearchResult()
                response.add(searchResponse)
            } catch (e: Exception) {
                Log.e("Error mapping search result for:", "$searchItem $e")
            }
        }
        return response
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBeforeLast("En") ?: "Unknown"
        var poster = fixUrl(document.select("img.TPostBg").attr("src"))
        if (poster.isEmpty())
        {
            poster= fetchImageUrl(document.select("div.title-img img").toString())!!
        }
        val description = document.selectFirst("div.summary.link-co p")?.text()
        val type=if (url.contains("film")) TvType.Movie else TvType.TvSeries
        val imdbUrl=document.selectFirst("p.dtls a:contains(IMDb)")?.attr("href")
        val TMDbid=document.selectFirst("p.dtls a:contains(TMDb)")?.attr("href")?.substringAfterLast("/")
        val tags=document.select("div.meta.df.aic.fww a").map { it.text() }
        return if (type==TvType.TvSeries)
        {
            val episodes = mutableListOf<Episode>()
            document.select("section.sc-seasons ul li input").map {
                val dataseason=it.attr("data-season")
                val dataid=it.attr("post-id")
                app.get("$coflixAPI/series/$dataid/$dataseason").parsedSafe<EpRes>()?.episodes?.map { ep->
                    val season=ep.season.toIntOrNull()
                    val epnumber=ep.number.toIntOrNull()
                    val eptitle=ep.title
                    val epposter=fetchImageUrl(ep.image)
                    val ephref=ep.links
                    episodes+=newEpisode(ephref)
                    {
                        this.name=eptitle
                        this.season=season
                        this.episode=epnumber
                        this.posterUrl=epposter
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                addImdbUrl(imdbUrl)
                addTMDbId(TMDbid)
            }
        }
        else newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                addImdbUrl(imdbUrl)
                addTMDbId(TMDbid)
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val iframe = app.get(data).document.select("div.embed iframe").attr("src")
        app.get(iframe).document.select("div.OptionsLangDisp div.OD.OD_FR.REactiv li").amap {
            val base64encoded=it.attr("onclick").substringAfter("showVideo('").substringBefore("',")
            val url=base64Decode(base64encoded)
            loadExtractor(url,subtitleCallback, callback)
        }
        return true
    }
}
