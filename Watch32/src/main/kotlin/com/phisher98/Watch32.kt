package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import org.jsoup.nodes.Document

class Watch32 : MainAPI() {
    override var mainUrl = "https://watch32.sx"
    override var name = "Watch32"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "en"
    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "-")}"
        val response = app.get(url)
        return if (response.code == 200) searchResponseBuilder(response.documentLarge)
        else listOf()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override val mainPage =
            mainPageOf(
                    "$mainUrl/movie?page=" to "Popular Movies",
                    "$mainUrl/tv-show?page=" to "Popular TV Shows",
                    "$mainUrl/coming-soon?page=" to "Coming Soon",
                    "$mainUrl/top-imdb?page=" to "Top IMDB Rating",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val response = app.get(url)
        if (response.code == 200)
                return newHomePageResponse(
                        request.name,
                        searchResponseBuilder(response.documentLarge),
                        true
                )
        else throw ErrorLoadingException("Could not load data")
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url)
        if (res.code != 200) throw ErrorLoadingException("Could not load data$url")

        val type = url
        val contentId = res.documentLarge.select("div.detail_page-watch").attr("data-id")
        val details = res.documentLarge.select("div.detail_page-infor")
        val name = details.select("h2.heading-name > a").text()
        val year = res.documentLarge.select("div.row-line:has(> span.type > strong:contains(Released))").text().replace("Released:", "").trim().substringBefore("-").toIntOrNull()
        val actors = res.documentLarge
            .select("div.row-line:has(> span.type > strong:contains(Casts)) a")
            .map { it.text().trim() }
        val genres = res.documentLarge
            .select("div.row-line:has(> span.type > strong:contains(Genre)) a")
            .map { it.text().trim() }

        if (type.contains("movie")) {
            val tmdbMovieId = runCatching { fetchtmdb(name,true) }.getOrNull()
            val imdbIdFromMovie = tmdbMovieId?.let { id ->
                runCatching {
                    val url = "$TMDBAPI/movie/$id/external_ids?api_key=1865f43a0549ca50d341dd9ab8b29f49"
                    val jsonText = app.get(url).textLarge
                    JSONObject(jsonText).optString("imdb_id").takeIf { it.isNotBlank() }
                }.getOrNull()
            }

            val movieCreditsJsonText = tmdbMovieId?.let { id ->
                runCatching {
                    app.get("$TMDBAPI/movie/$id/credits?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US").textLarge
                }.getOrNull()
            }

            val bgurl = runCatching {
                val json = app.get(
                    "$TMDBAPI/movie/$tmdbMovieId/images?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US&include_image_language=en,null"
                ).textLarge

                val backdrops = JSONObject(json).optJSONArray("backdrops")
                val bestBackdrop = backdrops?.optJSONObject(0)?.optString("file_path")?.takeIf { it.isNotBlank() }

                bestBackdrop?.let { "https://image.tmdb.org/t/p/original$it" }
            }.getOrNull()

            val movieCastList = parseCredits(movieCreditsJsonText)

            val logoPath = imdbIdFromMovie?.let {
                "https://live.metahub.space/logo/medium/$it/img"
            }


            return newMovieLoadResponse(name, url, TvType.Movie, "list/$contentId") {
                this.posterUrl = details.select("div.film-poster > img").attr("src")
                this.backgroundPosterUrl = bgurl ?: posterUrl
                try { this.logoUrl = logoPath } catch(_:Throwable){}
                this.plot = details.select("div.description").text()
                this.year = year
                this.score = Score.from10(
                        details.select("button.btn-imdb")
                                .text()
                                .replace("N/A", "").substringAfter(":").trim())
                this.tags = genres
                this.actors = movieCastList
                addTrailer(res.documentLarge.select("iframe#iframe-trailer").attr("data-src"))
            }
        } else {

            val tmdbShowId = runCatching { fetchtmdb(name,false) }.getOrNull()
            val imdbIdFromShow = tmdbShowId?.let { id ->
                runCatching {
                    val url = "${TMDBAPI}/tv/$id/external_ids?api_key=$TMDB_API_KEY"
                    val jsonText = app.get(url).textLarge
                    JSONObject(jsonText).optString("imdb_id").takeIf { it.isNotBlank() }
                }.getOrNull()
            }

            val logoPath = imdbIdFromShow?.let {
                "https://live.metahub.space/logo/medium/$it/img"
            }

            val showCreditsJsonText = tmdbShowId?.let { id ->
                runCatching {
                    app.get("${TMDBAPI}/tv/$id/credits?api_key=${TMDB_API_KEY}&language=en-US").textLarge
                }.getOrNull()
            }
            val castList: List<ActorData> = parseCredits(showCreditsJsonText)

            val bgurl = runCatching {
                val json = app.get(
                    "${TMDBAPI}/tv/$tmdbShowId/images?api_key=${TMDB_API_KEY}&language=en-US&include_image_language=en,null"
                ).textLarge

                val backdrops = JSONObject(json).optJSONArray("backdrops")
                val bestBackdrop = backdrops?.optJSONObject(0)?.optString("file_path")?.takeIf { it.isNotBlank() }

                bestBackdrop?.let { "https://image.tmdb.org/t/p/original$it" }
            }.getOrNull()

            val episodes = ArrayList<Episode>()
            val seasonsRes =
                    app.get("$mainUrl/ajax/season/list/$contentId").documentLarge.select("a.ss-item")

            seasonsRes.forEach { season ->
                val seasonId = season.attr("data-id")
                val seasonNum = season.text().replace("Season ", "")

                val tmdbSeasonJson = tmdbShowId?.let { id ->
                    runCatching {
                        app.get("${TMDBAPI}/tv/$id/season/$seasonNum?api_key=${TMDB_API_KEY}&language=en-US").textLarge
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

                app.get("$mainUrl/ajax/season/episodes/$seasonId")
                        .documentLarge
                        .select("a.eps-item")
                        .forEach { episode ->
                            val epId = episode.attr("data-id")
                            val match = Regex("Eps (\\d+): (.+)").find(episode.attr("title"))
                                ?: return@forEach

                            val (epNum, epName) = match.destructured
                            val tmdbEpJson = tmdbEpisodeMap?.get(epNum.toInt())
                            episodes.add(
                                    newEpisode(epId) {
                                        this.name = tmdbEpJson?.optString("name")?.takeIf { it.isNotBlank() } ?: epName
                                        this.episode = epNum.toInt()
                                        this.season = seasonNum.replace("Series", "").trim().toInt()
                                        this.data = "servers/$epId"
                                        this.description = tmdbEpJson?.optString("overview")?.takeIf { it.isNotBlank() }
                                        this.posterUrl = tmdbEpJson?.optString("still_path")?.takeIf { it.isNotBlank() }?.let { "${TMDBIMAGEBASEURL}$it" }
                                        this.score = tmdbEpJson?.optDouble("vote_average")?.let { Score.from10(it.toString()) }
                                        addDate(tmdbEpJson?.optString("air_date")?.takeIf { it.isNotBlank() })
                                    }
                            )
                        }
            }
            return newTvSeriesLoadResponse(name, url, TvType.TvSeries, episodes) {
                this.posterUrl = details.select("div.film-poster > img").attr("src")
                this.backgroundPosterUrl = bgurl ?: posterUrl
                try { this.logoUrl = logoPath } catch(_:Throwable){}
                this.plot = details.select("div.description").text()
                this.year = year
                this.score = Score.from10(
                        details.select("button.btn-imdb")
                                .text()
                                .replace("N/A", "").substringAfter(":").trim())
                this.tags = genres
                this.actors = castList
                addActors(actors)
                addTrailer(res.documentLarge.select("iframe#iframe-trailer").attr("data-src"))
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val serversRes = app.get("$mainUrl/ajax/episode/$data").documentLarge.select("a.link-item")
        serversRes.forEach { server ->
            val linkId =
                server.attr("data-linkid").ifEmpty { server.attr("data-id") }
            val source = app.get("$mainUrl/ajax/episode/sources/$linkId").parsedSafe<Source>()
            loadExtractor(source?.link.toString(), subtitleCallback, callback)
        }
        return true
    }

    data class Source(
            @JsonProperty("type") var type: String,
            @JsonProperty("link") var link: String
    )

    private fun searchResponseBuilder(webDocument: Document): List<SearchResponse> {
        val searchCollection =
                webDocument.select("div.flw-item").mapNotNull { element ->
                    val title =
                            element.selectFirst("h2.film-name > a")?.attr("title")
                                    ?: return@mapNotNull null
                    val link =
                            element.selectFirst("h2.film-name > a")?.attr("href")
                                    ?: return@mapNotNull null
                    val poster =
                            element.selectFirst("img.film-poster-img")?.attr("data-src")
                                    ?: return@mapNotNull null
                    val quality = element.select("div.pick.film-poster-quality").text()
                    newMovieSearchResponse(title, link) {
                        this.posterUrl = poster
                        this.quality = getQualityFromString(quality)
                    }
                }
        return searchCollection
    }
}
