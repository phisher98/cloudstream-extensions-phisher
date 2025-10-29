package com.Yflix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.phisher98.BuildConfig
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.random.Random


class Yflix : MainAPI() {
    override var mainUrl = "https://yflix.to"
    override var name = "YFlix"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.OVA, TvType.AnimeMovie, TvType.Anime, TvType.Cartoon, TvType.AsianDrama)

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(this.select("a.poster").attr("href"))
        val title = this.select("a.title").text()
        val posterUrl = fixUrl(this.select("a.poster img").attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    companion object {

        suspend fun decode(text: String?): String {
            return try {
                val res = app.get("${BuildConfig.YFXENC}?text=$text").text
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                ""
            }
        }

        private val JSON = "application/json; charset=utf-8".toMediaType()

        suspend fun decodeReverse(text: String): String {
            val jsonBody = """{"text":"$text"}""".toRequestBody(JSON)

            return try {
                val res = app.post(
                    BuildConfig.YFXDEC,
                    requestBody = jsonBody
                ).text
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                ""
            }
        }

    }


    override val mainPage =
        mainPageOf(
            "$mainUrl/browser?type[]=movie&sort=trending" to "Trending Movies",
            "$mainUrl/browser?type[]=tv&sort=trending" to "Trending TV Shows",
            "$mainUrl/browser?type%5B%5D=movie&type%5B%5D=tv&sort=imdb" to "Top IMDB",
            "$mainUrl/browser?type%5B%5D=movie&type%5B%5D=tv&sort=release_date" to "Latest Release"
        )

    override suspend fun search(query: String,page: Int): SearchResponseList? {
        val link = "$mainUrl/browser?keyword=$query&page=$page"
        val res = app.get(link).document
        return res.select("div.film-section.md div.item").map { it.toSearchResult() }.toNewSearchResponseList()
    }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        delay(Random.nextLong(1000, 2000))

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "$mainUrl/",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Cookie" to "usertype=guest; session=Mv2Y6x1b2I8SEw3fj0eNDfQYJM3CTpH9KjJc3ACK; cf_clearance=z9kEgtOSx3us4aluy5_5MfYEL6Ei8RJ3jCbcFTD2R1E-1745122952-1.2.1.1-UYjW2QUhPKUmojZE3XUE.gqHf3g5O6lvdl0qDCNPb5IjjavrpZIOpbE64osKxLbcblCAWynfNLv6bKSO75WzURG.FqDtfcu_si3MrCHECNtbMJC.k9cuhqDRcsz8hHPgpQE2fY8rR1z5Z4HfGmCw2MWMT6GelsZW_RQrTMHUYtIqjaEiAtxfcg.O4v_RGPwio_2J2V3rP16JbWO8wRh_dObNvWSMwMW.t44PhOZml_xWuh7DH.EIxLu3AzI91wggYU9rw6JJkaWY.UBbvWB0ThZRPTAJZy_9wlx2QFyh80AXU2c5BPHwEZPQhTQHBGQZZ0BGZkzoAB8pYI3f3eEEpBUW9fEbEJ9uoDKs7WOow8g"
        )

        val res = app.get("${request.data}&page=$page", headers).document
        val items = res.select("div.film-section.md div.item").map { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }



    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val keyword = url.substringAfter("/watch/").substringBefore(".")
        val poster = document.select("div.poster img").attr("src")
        val title = document.selectFirst("h1.title")?.text().orEmpty()
        val plot = document.selectFirst("div.detailWrap div.description")?.text()
        val year = document.select("div.metadata.set span:nth-child(4)").text().toIntOrNull()
        val rating = document.selectFirst("div.metadata.set span.IMDb")?.ownText()
        val contentrating = document.select("div.metadata.set span.ratingR").text()
        val genres = document.select("li:contains(Genres:) a").map { it.text() }


        val backgroundposter = document.selectFirst("div.detail-bg ")?.attr("style")?.substringAfter("url('")?.substringBefore("'")
        val dataid = document.select("#movie-rating").attr("data-id")

        val decoded = decode(dataid)
        val epRes = app.get("$mainUrl/ajax/episodes/list?keyword=$keyword&id=$dataid&_=$decoded")
            .parsedSafe<Response>()?.getDocument()

        val movieNode = epRes?.selectFirst("ul.episodes a")
        val allLinks = epRes?.select("ul.episodes a") ?: emptyList()

        val recommendations = document.select("div.film-section.md div.item").map { it.toSearchResult() }


        if (allLinks.size == 1 && movieNode != null &&
            movieNode.text().contains("Movie", ignoreCase = true)
        ) {
            val movieId = movieNode.attr("eid")

            return newMovieLoadResponse(title, url, TvType.Movie, movieId) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter ?: poster
                this.plot = plot
                this.year = year
                this.contentRating = contentrating
                this.tags = genres
                this.score = Score.from10(rating)
                this.recommendations = recommendations
            }
        }

        val episodes = mutableListOf<Episode>()
        epRes?.select("ul.episodes")?.forEach { seasonBlock ->
            val seasonNumber = seasonBlock.attr("data-season").toIntOrNull() ?: 1
            seasonBlock.select("a").forEachIndexed { index, ep ->
                val epNum = ep.attr("num").toIntOrNull() ?: (index + 1)
                val epTitle = ep.selectFirst("span:last-child")?.text()?.trim().orEmpty()
                val airDate = ep.attr("title").trim()
                val eid = ep.attr("eid")
                episodes.add(
                    newEpisode(eid) {
                        this.name = epTitle
                        this.episode = epNum
                        this.season = seasonNumber
                        addDate(airDate)
                    }
                )
            }
        }


        return newTvSeriesLoadResponse( title, url, TvType.TvSeries, episodes)
        {
            this.posterUrl = poster
            this.backgroundPosterUrl = backgroundposter ?: poster
            this.plot = plot
            this.year = year
            this.contentRating = contentrating
            this.tags = genres
            this.score = Score.from10(rating)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val eid= data.substringAfterLast("/")
        try {
            val decodetoken = decode(eid)
            val listResp = app.get("$mainUrl/ajax/links/list?eid=$eid&_=$decodetoken")
                .parsedSafe<Response>()
            val document = listResp?.getDocument()

            if (document == null) {
                Log.d("Yflix", "No document returned for links list")
                return false
            }

            val servers = document.select("li.server")
            if (servers.isEmpty()) {
                Log.d("Yflix", "No servers found in the links list")
                return false
            }

            servers.forEach { serverNode ->
                try {
                    val lid = serverNode.attr("data-lid").trim()
                    if (lid.isBlank()) {
                        Log.d("Yflix", "Skipping server with empty lid")
                        return@forEach
                    }

                    val serverName = serverNode.selectFirst("span")?.text()?.trim()?.ifEmpty { "Server" }
                    val decodelid = decode(lid)
                    val viewResp = app.get("$mainUrl/ajax/links/view?id=$lid&_=$decodelid")
                        .parsedSafe<Response>()

                    val result = viewResp?.result
                    if (result.isNullOrBlank()) {
                        Log.d("Yflix", "Empty result for server $serverName (lid=$lid)")
                        return@forEach
                    }

                    val decodedIframePayload = try {
                        decodeReverse(result)
                    } catch (e: Exception) {
                        Log.d("Yflix", "Failed to decodeReverse for lid=$lid : ${e.message}")
                        return@forEach
                    }


                    val iframeUrl = try {
                        extractVideoUrlFromJson(decodedIframePayload)
                    } catch (e: Exception) {
                        Log.d("Yflix", "Failed to extract video url for lid=$lid : ${e.message}")
                        null
                    }

                    if (iframeUrl.isNullOrBlank()) {
                        Log.d("Yflix", "No iframe/video url extracted for server $serverName (lid=$lid)")
                        return@forEach
                    }

                    val displayName = "⌜ YFlix ⌟  |  $serverName"
                    loadExtractor(iframeUrl, displayName, subtitleCallback, callback)
                } catch (inner: Exception) {
                    Log.d("Yflix", "Error processing server node: ${inner.message}")
                }
            }

            return true
        } catch (e: Exception) {
            Log.d("Yflix", "loadLinks failed: ${e.message}")
            return false
        }
    }


    data class Response(
        @JsonProperty("status") val status: Boolean,
        @JsonProperty("result") val result: String
    ) {
        fun getDocument(): Document {
            return Jsoup.parse(result)
        }
    }

    private fun extractVideoUrlFromJson(jsonData: String): String {
        val jsonObject = JSONObject(jsonData)
        return jsonObject.getString("url")
    }
}
