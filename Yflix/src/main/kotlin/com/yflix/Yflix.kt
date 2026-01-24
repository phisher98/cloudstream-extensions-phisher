package com.yflix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
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
import com.lagradost.cloudstream3.getQualityFromString
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
import java.net.URLEncoder
import kotlin.random.Random


class Yflix : MainAPI() {
    override var mainUrl = YflixPlugin.currentYflixServer
    override var name = YflixPlugin.getCurrentServerName()
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.OVA,
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.Cartoon,
        TvType.AsianDrama
    )

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(this.select("a.poster").attr("href"))
        val title = this.select("a.title").text()
        val posterUrl = fixUrl(this.select("a.poster img").attr("data-src"))
        val quality = this.select("div.quality").text()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }


    companion object {
        private const val SIMKL = "https://api.simkl.com"
        private const val TMDB_API_KEY = "1865f43a0549ca50d341dd9ab8b29f49"
        const val TMDBAPI = "https://orange-voice-abcf.phisher16.workers.dev"

        const val TMDBIMAGEBASEURL = "https://image.tmdb.org/t/p/original"
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

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query,1).items

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val link = "$mainUrl/browser?keyword=$query&page=$page"
        val res = app.get(link).documentLarge
        return res.select("div.item").map { it.toSearchResult() }
            .toNewSearchResponseList()
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

        val res = app.get("${request.data}&page=$page", headers).documentLarge
        val items = res.select("div.item").map { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val keyword = url.substringAfter("/watch/").substringBefore(".")
        val poster = document.select("div.poster img").attr("src")
        val title = document.selectFirst("h1.title")?.text().orEmpty()
        val plot = document.selectFirst("div.description")?.text()
        val year = document.select("div.metadata.set span:nth-child(4)").text().toIntOrNull()
        val rating = document.selectFirst("div.metadata.set span.IMDb")?.ownText()
        val contentRating = document.select("div.metadata.set span.ratingR").text()
        val genres = document.select("li:contains(Genres:) a").map { it.text() }
        val backgroundPoster =
            document.selectFirst("div.detail-bg,div.site-movie-bg")?.attr("style")?.substringAfter("url('")
                ?.substringBefore("'")
        val dataId = document.select("#movie-rating").attr("data-id")
        val decoded = decode(dataId)

        val epRes = app.get("$mainUrl/ajax/episodes/list?keyword=$keyword&id=$dataId&_=$decoded")
            .parsedSafe<Response>()?.getDocument()

        val movieNode = epRes?.selectFirst("ul.episodes a")
        val allLinks = epRes?.select("ul.episodes a") ?: emptyList()
        val recommendations =
            document.select("div.film-section.md div.item").map { it.toSearchResult() }

        if (allLinks.size == 1 && movieNode != null && movieNode.text().contains("Movie", ignoreCase = true)) {
            val movieId = movieNode.attr("eid")

            val tmdbMovieId = runCatching { fetchtmdb(title,true) }.getOrNull()

            val imdbIdFromMovie = tmdbMovieId?.let { id ->
                runCatching {
                    val url = "$TMDBAPI/movie/$id/external_ids?api_key=$TMDB_API_KEY"
                    val jsonText = app.get(url).textLarge
                    JSONObject(jsonText).optString("imdb_id").takeIf { it.isNotBlank() }
                }.getOrNull()
            }

            val logoPath = imdbIdFromMovie?.let {
                "https://live.metahub.space/logo/medium/$it/img"
            }

            val simklIdMovie = imdbIdFromMovie?.let { imdb ->
                runCatching {
                    val simklJson =
                        JSONObject(app.get("$SIMKL/movies/$imdb?client_id=${com.lagradost.cloudstream3.BuildConfig.SIMKL_CLIENT_ID}").text)
                    simklJson.optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
                }.getOrNull()
            }

            val movieCreditsJsonText = tmdbMovieId?.let { id ->
                runCatching {
                    app.get("$TMDBAPI/movie/$id/credits?api_key=$TMDB_API_KEY&language=en-US").textLarge
                }.getOrNull()
            }

            val bgurl = runCatching {
                val json = app.get(
                    "$TMDBAPI/movie/$tmdbMovieId/images?api_key=$TMDB_API_KEY&language=en-US&include_image_language=en,null"
                ).textLarge

                val backdrops = JSONObject(json).optJSONArray("backdrops")
                val bestBackdrop = backdrops?.optJSONObject(0)?.optString("file_path")?.takeIf { it.isNotBlank() }

                bestBackdrop?.let { "https://image.tmdb.org/t/p/original$it" }
            }.getOrNull()



            val movieCastList = parseCredits(movieCreditsJsonText)

            return newMovieLoadResponse(title, url, TvType.Movie, movieId) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgurl ?: backgroundPoster ?: poster
                try { this.logoUrl = logoPath } catch(_:Throwable){}
                this.plot = plot
                this.year = year
                this.contentRating = contentRating
                this.tags = genres
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                imdbIdFromMovie?.let { addImdbId(it) }
                this.actors = movieCastList
            }
        }

        val episodes = ArrayList<Episode>()
        val tmdbShowId = runCatching { fetchtmdb(title,false) }.getOrNull()
        if (title.contains("Special"))
        {
            Log.d("Phisher",tmdbShowId.toString())

        }
        val imdbIdFromShow = tmdbShowId?.let { id ->
            runCatching {
                val url = "$TMDBAPI/tv/$id/external_ids?api_key=$TMDB_API_KEY"
                val jsonText = app.get(url).textLarge
                JSONObject(jsonText).optString("imdb_id").takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        val logoPath = imdbIdFromShow?.let {
            "https://live.metahub.space/logo/medium/$it/img"
        }

        val simklIdShow = imdbIdFromShow?.let { imdb ->
            runCatching {
                val simklJson =
                    JSONObject(app.get("$SIMKL/tv/$imdb?client_id=${com.lagradost.cloudstream3.BuildConfig.SIMKL_CLIENT_ID}").text)
                simklJson.optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
            }.getOrNull()
        }

        // fetch show credits once (used for actors)
        val showCreditsJsonText = tmdbShowId?.let { id ->
            runCatching {
                app.get("$TMDBAPI/tv/$id/credits?api_key=$TMDB_API_KEY&language=en-US").textLarge
            }.getOrNull()
        }
        val castList: List<ActorData> = parseCredits(showCreditsJsonText)

        val bgurl = runCatching {
            val json = app.get(
                "$TMDBAPI/tv/$imdbIdFromShow/images?api_key=$TMDB_API_KEY&language=en-US&include_image_language=en,null"
            ).textLarge

            val backdrops = JSONObject(json).optJSONArray("backdrops")
            val bestBackdrop = backdrops?.optJSONObject(0)?.optString("file_path")?.takeIf { it.isNotBlank() }

            bestBackdrop?.let { "https://image.tmdb.org/t/p/original$it" }
        }.getOrNull()

        epRes?.select("ul.episodes")?.forEach { seasonBlock ->
            val seasonNumber = seasonBlock.attr("data-season").toIntOrNull() ?: 1
            val tmdbSeasonJson = tmdbShowId?.let { id ->
                runCatching {
                    app.get("$TMDBAPI/tv/$id/season/$seasonNumber?api_key=$TMDB_API_KEY&language=en-US").textLarge
                }.getOrNull()?.let { JSONObject(it) }
            }

            val tmdbEpisodeMap = tmdbSeasonJson?.optJSONArray("episodes")?.let { arr ->
                val map = HashMap<Int, JSONObject>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val epNum = o.optInt("episode_number", -1)
                    if (epNum >= 0) map[epNum] = o
                }
                map
            }

            seasonBlock.select("a").forEachIndexed { index, ep ->
                val epNum = ep.attr("num").toIntOrNull() ?: (index + 1)
                val tmdbEpJson = tmdbEpisodeMap?.get(epNum)
                val epTitle = tmdbEpJson?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: ep.selectFirst("span:last-child")?.text()?.trim().orEmpty()
                val airDate = tmdbEpJson?.optString("air_date")?.takeIf { it.isNotBlank() }
                    ?: ep.attr("title").trim().takeIf { it.isNotBlank() }
                val eid = ep.attr("eid")
                val desc = tmdbEpJson?.optString("overview")?.takeIf { it.isNotBlank() }
                    ?: ep.selectFirst("span.description")?.text()?.takeIf { it.isNotBlank() }
                val posterUrl = tmdbEpJson?.optString("still_path")?.takeIf { it.isNotBlank() }
                    ?.let { "$TMDBIMAGEBASEURL$it" }
                val score =
                    tmdbEpJson?.optDouble("vote_average")?.let { Score.from10(it.toString()) }
                episodes += newEpisode(eid) {
                    this.name = epTitle
                    this.season = seasonNumber
                    this.episode = epNum
                    this.posterUrl = posterUrl
                    this.description = desc
                    this.score = score
                    this.addDate(airDate)
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.backgroundPosterUrl = bgurl ?: backgroundPoster ?: poster
            try { this.logoUrl = logoPath } catch(_:Throwable){}
            this.plot = plot
            this.year = year
            this.contentRating = contentRating
            this.tags = genres
            this.score = Score.from10(rating)
            this.recommendations = recommendations
            addImdbId(imdbIdFromShow)
            this.actors = castList
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val eid = data.substringAfterLast("/")
        try {
            val decodetoken = decode(eid)
            val listResp = app.get("$mainUrl/ajax/links/list?eid=$eid&_=$decodetoken")
                .parsedSafe<Response>()
            val document = listResp?.getDocument()

            if (document == null) {
                Log.d(name, "No document returned for links list")
                return false
            }

            val servers = document.select("li.server,div.server")
            if (servers.isEmpty()) {
                Log.d(name, "No servers found in the links list")
                return false
            }

            servers.forEach { serverNode ->
                try {
                    val lid = serverNode.attr("data-lid").trim()
                    if (lid.isBlank()) {
                        Log.d(name, "Skipping server with empty lid")
                        return@forEach
                    }

                    val serverName =
                        serverNode.selectFirst("span")?.text()?.trim()?.ifEmpty { "Server" }
                    val decodelid = decode(lid)
                    val viewResp = app.get("$mainUrl/ajax/links/view?id=$lid&_=$decodelid")
                        .parsedSafe<Response>()

                    val result = viewResp?.result
                    if (result.isNullOrBlank()) {
                        Log.d(name, "Empty result for server $serverName (lid=$lid)")
                        return@forEach
                    }

                    val decodedIframePayload = try {
                        decodeReverse(result)
                    } catch (e: Exception) {
                        Log.d(name, "Failed to decodeReverse for lid=$lid : ${e.message}")
                        return@forEach
                    }


                    val iframeUrl = try {
                        extractVideoUrlFromJson(decodedIframePayload)
                    } catch (e: Exception) {
                        Log.d(name, "Failed to extract video url for lid=$lid : ${e.message}")
                        null
                    }

                    if (iframeUrl.isNullOrBlank()) {
                        Log.d(
                            name,
                            "No iframe/video url extracted for server $serverName (lid=$lid)"
                        )
                        return@forEach
                    }

                    val displayName = "⌜ $name ⌟  |  $serverName"
                    loadExtractor(iframeUrl, displayName, subtitleCallback, callback)
                } catch (inner: Exception) {
                    Log.d(name, "Error processing server node: ${inner.message}")
                }
            }

            return true
        } catch (e: Exception) {
            Log.d(name, "loadLinks failed: ${e.message}")
            return false
        }
    }


    data class Response(
        @get:JsonProperty("result") val result: String
    ) {
        fun getDocument(): Document {
            return Jsoup.parse(result)
        }
    }

    private fun extractVideoUrlFromJson(jsonData: String): String {
        val jsonObject = JSONObject(jsonData)
        return jsonObject.getString("url")
    }

    suspend fun fetchtmdb(title: String, isMovie: Boolean): Int? {
        val url =
            "$TMDBAPI/search/multi?api_key=$TMDB_API_KEY&query=" +
                    URLEncoder.encode(title, "UTF-8")

        val json = JSONObject(app.get(url).text)
        val results = json.optJSONArray("results") ?: return null

        val targetType = if (isMovie) "movie" else "tv"

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue

            if (item.optString("media_type") != targetType) continue

            val resultTitle = if (isMovie)
                item.optString("title")
            else
                item.optString("name")

            if (resultTitle.equals(title, ignoreCase = true)) {
                return item.optInt("id")
            }
        }
        return null
    }


    fun parseCredits(jsonText: String?): List<ActorData> {
        if (jsonText.isNullOrBlank()) return emptyList()
        val list = ArrayList<ActorData>()
        val root = JSONObject(jsonText)
        val castArr = root.optJSONArray("cast") ?: return list
        for (i in 0 until castArr.length()) {
            val c = castArr.optJSONObject(i) ?: continue
            val name = c.optString("name").takeIf { it.isNotBlank() } ?: c.optString("original_name").orEmpty()
            val profile = c.optString("profile_path").takeIf { it.isNotBlank() }?.let { "$TMDBIMAGEBASEURL$it" }
            val character = c.optString("character").takeIf { it.isNotBlank() }
            val actor = Actor(name, profile)
            list += ActorData(actor, roleString = character)
        }
        return list
    }


}

