package com.kissasian

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.Jsoup
import java.net.URI

class Kissasian : MainAPI() {
    override var mainUrl              = "https://ww2.kissasian.vip"
    override var name                 = "Kissasian"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries,TvType.Movie)

    companion object {
        val xmlHeader = mapOf("X-Requested-With" to "XMLHttpRequest")
    }

    override val mainPage = mainPageOf(
        "recently-updated" to "Recently Updated",
        "recently-added" to "Recently Added",
        "movie" to "Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val home     = document.select("div.film_list-wrap > div.flw-item").mapNotNull { it.toSearchResult() }

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
        val title     = this.select(".film-detail > .film-name > a").attr("title").trim()
        val href      = fixUrl(this.select("div.film-poster > a").attr("href")).replace("detail","watch")
        val posterUrl = fixUrlNull(this.select("div.film-poster > img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/search?keyword=$query&page=$i").document

            val results = document.select("div.film_list-wrap > div.flw-item").mapNotNull { it.toSearchResult() }

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
        val title = document.selectFirst(".anisc-detail > .film-name")?.text().toString()
        val poster = document.selectFirst(".anisc-poster img")?.attr("src")
        val description = document.selectFirst(".film-description.m-hide > .text")?.text()
        val tvtag=document.selectFirst("div.film-stats > span.item")?.text().toString()
        val tvType =
            if (tvtag=="MOVIE") TvType.Movie
           else TvType.AsianDrama
        val showname = URI(url).path.split("/").last()
        return if (tvType == TvType.AsianDrama) {
            val episodes =
                Jsoup.parse(
                    parseJson<Response>(
                        app.get(
                            "$mainUrl/ajax/v2/episode/list/$showname"
                        ).text
                    ).html
                ).select(".ss-list > a[href].ssl-item.ep-item").map {
                    newEpisode(showname+it.attr("href")) {
                        this.name = "Episode"+it.attr("player-title")
                        this.episode = it.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                    }
                }
            //Log.d("Phisher Epe", "$episodes")
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
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
        val parts = data.split("?ep=")
        val showname = parts[0].substringAfterLast("/")
        val ep = parts[1]
        Jsoup.parse(
            parseJson<Response>(
                app.get(
                    "$mainUrl/ajax/v2/episode/servers?episodeId=$showname-$ep"
                ).text
            ).html
        ).select("div.ps__-list > div.item.server-item").forEach {
            val serverid=it.attr("data-id")
            val apiRes =
                app.get("$mainUrl/ajax/v2/episode/sources?id=$serverid")
                    .parsedSafe<APISourceresponse>()
            val fstream=apiRes?.link.toString()
            val response = app.get(fstream,referer = mainUrl, headers = xmlHeader, interceptor = WebViewResolver(Regex("""ajax/getSources""")))
            Log.d("Test","$response")
            val regex = """"file":"(.*?)""""
            val matchResult = regex.toRegex().find(response.toString())
            val fileUrl = matchResult?.groups?.get(1)?.value
            if (fileUrl!=null)
            {
                callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = fileUrl,
                    referer = "$mainUrl/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
                )
            }
        }
        return true
    }

    private data class Response(
        @JsonProperty("status") val status: Boolean,
        @JsonProperty("html") val html: String
    )

    data class APISourceresponse(
        @JsonProperty("type") val type: String,
        @JsonProperty("link") val link: String? = null,
    )
}
