package com.moontv

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
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.phisher98.BuildConfig
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URLEncoder
import kotlin.random.Random


class Moontv : MainAPI() {
    override var mainUrl = MoontvPlugin.currentMoontvServer
    override var name = MoontvPlugin.getCurrentServerName()
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
        val title = this.select(".title").text()
        val posterUrl = this.selectFirst("a.poster img")?.let {
            fixUrl(
                it.attr("data-src")
                    .ifBlank { it.attr("src") }
            )
        }
        val quality = this.select("div.quality").text()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }


    companion object {
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
        val res = app.get(link).document
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

        val res = app.get("${request.data}&page=$page", headers).document
        val items = res.select("div.item").map { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val poster = document.selectFirst("div.poster img, a.poster img")?.attr("src")
        val titleText = document.selectFirst("h1.title,h1.movie-detail-title")?.text().orEmpty()
        val plot = document.selectFirst("div.description")?.text()
        val year = document.select("div.metadata.set span:nth-child(4)").text().toIntOrNull()
        val rating = document.selectFirst("div.metadata.set span.IMDb")?.ownText()
        val contentRating = document.select("div.metadata.set span.ratingR").text()
        val backgroundPoster = document.selectFirst("div.detail-bg,div.site-movie-bg")
            ?.attr("style")?.substringAfter("url('")?.substringBefore("'")

        val dataId = document.selectFirst(".movie-eps-list a")
            ?.attr("data-id")?.takeIf { it.isNotBlank() }
            ?: document.select("[x-data]").firstNotNullOfOrNull {
                Regex("id:\\s*'([^']+)'")
                    .find(it.attr("x-data"))
                    ?.groupValues?.getOrNull(1)
            }

        val decoded = decode(dataId)

        val api = app.get("$mainUrl/api/v1/titles/$dataId/episodes?_=$decoded").parsedSafe<ApiResponse>()

        val title = api?.result?.title ?: return null

        val recommendations = document.select("div.item > div.inner").map { it.toSearchResult() }

        if (title.type == "movie") {

            val tmdbMovieId = runCatching { fetchtmdb(titleText, true) }.getOrNull()

            val bgurl = runCatching {
                val json = app.get(
                    "${TMDBAPI}/movie/$tmdbMovieId/images?api_key=${TMDB_API_KEY}&language=en-US&include_image_language=en,null"
                ).text

                val backdrops = JSONObject(json).optJSONArray("backdrops")
                val bestBackdrop = backdrops?.optJSONObject(0)?.optString("file_path")?.takeIf { it.isNotBlank() }

                bestBackdrop?.let { "https://image.tmdb.org/t/p/original$it" }
            }.getOrNull()

            val imdbIdFromMovie = tmdbMovieId?.let { id ->
                runCatching {
                    val jsonText = app.get("$TMDBAPI/movie/$id/external_ids?api_key=$TMDB_API_KEY").text
                    JSONObject(jsonText).optString("imdb_id").takeIf { it.isNotBlank() }
                }.getOrNull()
            }

            val logoPath = imdbIdFromMovie?.let {
                "https://live.metahub.space/logo/medium/$it/img"
            }

            val movieCreditsJsonText = tmdbMovieId?.let { id ->
                runCatching {
                    app.get("$TMDBAPI/movie/$id/credits?api_key=$TMDB_API_KEY&language=en-US").text
                }.getOrNull()
            }

            val movieCastList = parseCredits(movieCreditsJsonText)

            return newMovieLoadResponse(title.title, url, TvType.Movie, api.result.seasons?.firstOrNull()
                ?.episodes?.firstOrNull()?.id )
            {
                this.posterUrl = title.poster.medium ?: poster
                this.backgroundPosterUrl = bgurl ?: title.backdrop.medium ?: backgroundPoster ?: poster
                try { this.logoUrl = logoPath } catch(_: Throwable) {}
                this.plot = title.synopsis ?: plot
                this.year = title.release_year ?: year
                this.contentRating = contentRating
                this.tags = title.genres.map { it.title }
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                imdbIdFromMovie?.let { addImdbId(it) }
                this.actors = movieCastList
            }
        }

        val episodes = ArrayList<Episode>()

        val tmdbShowId = runCatching { fetchtmdb(titleText, false) }.getOrNull()

        val imdbIdFromShow = tmdbShowId?.let { id ->
            runCatching {
                val jsonText = app.get("$TMDBAPI/tv/$id/external_ids?api_key=$TMDB_API_KEY").text
                JSONObject(jsonText).optString("imdb_id").takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        val logoPath = imdbIdFromShow?.let {
            "https://live.metahub.space/logo/medium/$it/img"
        }

        val showCreditsJsonText = tmdbShowId?.let { id ->
            runCatching {
                app.get("$TMDBAPI/tv/$id/credits?api_key=$TMDB_API_KEY&language=en-US").text
            }.getOrNull()
        }

        val castList: List<ActorData> = parseCredits(showCreditsJsonText)

        val bgurl = runCatching {
            val json = app.get(
                "$TMDBAPI/tv/$tmdbShowId/images?api_key=$TMDB_API_KEY&language=en-US&include_image_language=en,null"
            ).text

            val backdrops = JSONObject(json).optJSONArray("backdrops")
            val bestBackdrop = backdrops?.optJSONObject(0)?.optString("file_path")
                ?.takeIf { it.isNotBlank() }

            bestBackdrop?.let { "https://image.tmdb.org/t/p/original$it" }
        }.getOrNull()

        api.result.seasons?.forEach { season ->
            val seasonNumber = season.number
            val tmdbSeasonJson = tmdbShowId?.let { id ->
                runCatching {
                    app.get("$TMDBAPI/tv/$id/season/$seasonNumber?api_key=$TMDB_API_KEY&language=en-US").text
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

            season.episodes.forEach { ep ->
                val tmdbEpJson = tmdbEpisodeMap?.get(ep.number)
                val epTitle = tmdbEpJson?.optString("name")?.takeIf { it.isNotBlank() } ?: ep.detail_name
                val airDate = tmdbEpJson?.optString("air_date")?.takeIf { it.isNotBlank() } ?: ep.detail_released_at
                val desc = tmdbEpJson?.optString("overview")?.takeIf { it.isNotBlank() }
                val posterUrl = tmdbEpJson?.optString("still_path")?.takeIf { it.isNotBlank() }?.let { "$TMDBIMAGEBASEURL$it" }
                val score = tmdbEpJson?.optDouble("vote_average")?.takeIf { it > 0 }?.let { Score.from10(it.toString()) }

                episodes += newEpisode(ep.id) {
                    this.name = epTitle
                    this.season = seasonNumber
                    this.episode = ep.number
                    this.posterUrl = posterUrl
                    this.description = desc
                    this.score = score
                    this.addDate(airDate)
                }
            }
        }
        return newTvSeriesLoadResponse(title.title, url, TvType.TvSeries, episodes) {
            this.posterUrl = title.poster.medium ?: poster
            this.backgroundPosterUrl = bgurl ?: title.backdrop.medium ?: backgroundPoster ?: poster
            try { this.logoUrl = logoPath } catch(_: Throwable) {}
            this.plot = title.synopsis ?: plot
            this.year = title.release_year ?: year
            this.contentRating = contentRating
            this.tags = title.genres.map { it.title }
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

        val dataid = data.substringAfterLast("/")

        return try {
            val decodetoken = decode(dataid)

            val listResp = app.get("$mainUrl/api/v1/episodes/$dataid?_=$decodetoken")
                .parsedSafe<LinksResponse>()

            val links = listResp?.result?.links

            if (links.isNullOrEmpty()) {
                Log.d(name, "No servers found")
                return false
            }

            links.forEach { server ->
                try {
                    val lid = server.id
                    val serverName = server.name?.ifBlank { "Server" }

                    val linkToken = decode(lid)

                    val linkResp = app.get("$mainUrl/api/v1/links/$lid?_=$linkToken").parsedSafe<LinkDetailResponse>()

                    val result = linkResp?.result
                    if (result.isNullOrBlank()) {
                        Log.d(name, "Empty result for $serverName ($lid)")
                        return@forEach
                    }

                    val decodedPayload = try {
                        decodeReverse(result)
                    } catch (e: Exception) {
                        Log.d(name, "decodeReverse failed: ${e.message}")
                        return@forEach
                    }

                    val iframeUrl = try {
                        extractVideoUrlFromJson(decodedPayload)
                    } catch (e: Exception) {
                        Log.d(name, "extract failed: ${e.message}")
                        null
                    }

                    if (iframeUrl.isNullOrBlank()) {
                        Log.d(name, "No iframe for $serverName")
                        return@forEach
                    }

                    val displayName = "⌜ $name ⌟  |  $serverName"
                    loadExtractor(iframeUrl, displayName, subtitleCallback, callback)

                } catch (e: Exception) {
                    Log.d(name, "Server error: ${e.message}")
                }
            }

            true

        } catch (e: Exception) {
            Log.d(name, "loadLinks failed: ${e.message}")
            false
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

