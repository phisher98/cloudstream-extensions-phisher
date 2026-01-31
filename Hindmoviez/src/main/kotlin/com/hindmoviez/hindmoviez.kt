package com.hindmoviez

import com.google.gson.Gson
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
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.nodes.Element


class Hindmoviez : MainAPI() {
    override var mainUrl: String = runBlocking {
        HindmoviezPlugin.getDomains()?.hindmoviez ?: "https://hindmoviez.cafe"
    }
    override var name = "Hindmoviez"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries
    )
    companion object
    {
        private const val cinemeta_url = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta"
    }

    override val mainPage = mainPageOf(
        "" to "HomePage",
        "movies" to "Movies",
        "web-series" to "Web Series",
        "dramas/korean-drama" to "Korean Dramas",
        "dramas/chinese-drama" to "Chinese Dramas",
        "anime" to "Anime",
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = if (page==1) app.get("$mainUrl/${request.data}", timeout = 5000L).document else app.get("$mainUrl/${request.data}/page/$page", timeout = 5000L).document

        val home = doc.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home, true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = cleanTitle(this.selectFirst("div.entry-content img")?.attr("alt"))
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.entry-content img").let {
            img -> img.attr("data-src").takeIf { it.isNotBlank() }
            ?: img.attr("src") })

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getSearchQuality(title)
        }
    }


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val doc = app.get("$mainUrl/page/$page/?s=$query").document
        val res = doc.select("article").mapNotNull { it.toSearchResult() }
        return res.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 10000).document

        var name: String? = null
        var imdbRating: String? = null
        var imdbId: String? = null
        var releaseYear: String? = null
        var docgenres: List<String> = emptyList()

        doc.select("ul > li").forEach { li ->
            val strongText = li.selectFirst("strong")?.text()?.trim() ?: return@forEach
            val key = strongText.substringBefore(":").trim()
            val value = strongText.substringAfter(":", "").trim()
            val tailText = li.ownText().trim()

            when (key) {
                "Name" -> name = tailText.ifEmpty { value }

                "IMDB Rating" -> {
                    imdbRating = value.substringBefore("/")
                    imdbId = li.selectFirst("a[href*=\"/title/tt\"]")
                        ?.attr("href")
                        ?.substringAfter("/title/")
                        ?.substringBefore("/")
                }

                "Release Year" -> releaseYear = tailText.ifEmpty { value }

                "Genre" -> {
                    val genreText = tailText.ifEmpty { value }
                    docgenres = genreText.split(",").map { it.trim() }
                }
            }
        }

        val title = name ?: "Unknown"
        val poster = doc.select("meta[property=og:image]").attr("content")
        val descriptions = doc.select("h3")
            .firstOrNull { it.text().contains("Storyline", ignoreCase = true) }
            ?.nextElementSibling()
            ?.takeIf { it.tagName() == "p" }
            ?.text()

        val typeraw = doc.select("h1.entry-title").text()
        val tvtype = if (typeraw.contains("Season", ignoreCase = true)) TvType.TvSeries else TvType.Movie

        var background: String? = null
        var description: String? = null

        val tmdbId = imdbId?.let { id ->
            runCatching {
                val obj = JSONObject(
                    app.get(
                        "https://api.themoviedb.org/3/find/$id" +
                                "?api_key=1865f43a0549ca50d341dd9ab8b29f49" +
                                "&external_source=imdb_id"
                    ).textLarge
                )

                obj.optJSONArray("movie_results")?.optJSONObject(0)?.optInt("id")?.takeIf { it != 0 }
                    ?: obj.optJSONArray("tv_results")?.optJSONObject(0)?.optInt("id")?.takeIf { it != 0 }
            }.getOrNull()?.toString()
        }

        val creditsJson = tmdbId?.let {
            val tmdbmetatype = if (tvtype == TvType.TvSeries) "tv" else "movie"
            runCatching {
                app.get(
                    "https://api.themoviedb.org/3/$tmdbmetatype/$it/credits" +
                            "?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US"
                ).textLarge
            }.getOrNull()
        }
        val castList = parseCredits(creditsJson)

        val hrefs = doc.select("a.maxbutton")
            .amap { element ->
                val listUrl = element.absUrl("href")
                if (listUrl.isBlank()) return@amap emptyList()

                app.get(listUrl).document
                    .select("div.entry-content a")
                    .mapNotNull { anchor ->
                        val href = anchor.absUrl("href")
                        href.takeIf(String::isNotBlank)
                    }
            }
            .flatten()
            .toJson()


        val typeset = if (tvtype == TvType.TvSeries) "series" else "movie"

        val responseData = if (imdbId?.isNotEmpty() == true) {
            val jsonResponse = app.get("$cinemeta_url/$typeset/$imdbId.json").text
            if (jsonResponse.startsWith("{")) {
                Gson().fromJson(jsonResponse, ResponseData::class.java)
            } else null
        } else null

        if (responseData != null) {
            description = responseData.meta?.description ?: descriptions
            background = responseData.meta?.background ?: poster
        }


        if (tvtype == TvType.TvSeries) {

            val episodeUrlMap = mutableMapOf<Pair<Int, Int>, MutableList<String>>()

            doc.select("h3").forEach seasonHeader@{ h3 ->

                val seasonNumber = Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(h3.text())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: return@seasonHeader

                val p = h3.nextElementSibling()
                if (p?.tagName() != "p") return@seasonHeader

                val episodeListUrl = p.selectFirst("a[href]")
                    ?.absUrl("href")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@seasonHeader

                val episodeDoc = app.get(episodeListUrl).document
                episodeDoc.select("h3 > a").forEach episodeLoop@{ epAnchor ->
                    val episodeNumber = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(epAnchor.text())
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                        ?: return@episodeLoop

                    val epUrl = epAnchor.absUrl("href")
                        .takeIf { it.isNotBlank() }
                        ?: return@episodeLoop

                    val key = seasonNumber to episodeNumber
                    episodeUrlMap.getOrPut(key) { mutableListOf() }.add(epUrl)
                }
            }

            val episodes = episodeUrlMap.map { (key, urls) ->
                val (seasonNumber, episodeNumber) = key

                val metaEpisode = responseData?.meta?.videos
                    ?.firstOrNull { it.season == seasonNumber && it.episode == episodeNumber }

                newEpisode(urls.toJson()) {
                    this.name = metaEpisode?.title
                    this.season = seasonNumber
                    this.episode = episodeNumber
                    this.posterUrl = metaEpisode?.thumbnail
                    this.description = metaEpisode?.overview
                    addDate(metaEpisode?.released)
                }
            }

            return newTvSeriesLoadResponse(
                responseData?.meta?.name ?: title,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.backgroundPosterUrl = background ?: poster
                this.posterUrl = poster
                this.year = releaseYear?.toIntOrNull()
                    ?: responseData?.meta?.year?.toIntOrNull()
                this.plot = description ?: plot
                this.tags = docgenres
                this.actors = castList
                try { this.logoUrl = responseData?.meta?.logo } catch (_: Throwable) {}
                this.score = Score.from10(imdbRating ?: responseData?.meta?.imdbRating)
                addImdbId(imdbId)
            }
        }

        return newMovieLoadResponse(
            responseData?.meta?.name ?: title,
            url,
            TvType.Movie,
            hrefs
        ) {
            this.backgroundPosterUrl = background ?: poster
            this.posterUrl = poster
            this.year = releaseYear?.toIntOrNull() ?: responseData?.meta?.year?.toIntOrNull()
            this.plot = description ?: plot
            this.tags = docgenres
            this.actors = castList
            try { this.logoUrl = responseData?.meta?.logo } catch(_:Throwable){}
            this.score = Score.from10(imdbRating ?: responseData?.meta?.imdbRating)
            addImdbId(imdbId)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links: List<String> = tryParseJson<List<String>>(data) ?: emptyList()
        links.amap { pageUrl ->
            val pageDoc = app.get(pageUrl).document
            pageDoc.select("a.btn").forEach { btn ->
                val btnUrl = btn.absUrl("href")
                if (btnUrl.isBlank()) return@forEach
                val name = pageDoc.selectFirst("div.container p:contains(Name:)")
                    ?.text()
                    ?.substringAfter("Name:")
                    ?.trim()
                    .orEmpty()


                val extractedSpecs = buildExtractedTitle(extractSpecs(name))

                val fileSize = pageDoc.selectFirst("div.container p:contains(Size:)")
                    ?.text()
                    ?.substringAfter("Size:")
                    ?.trim()
                    .orEmpty()

                val doc = app.get(btnUrl).document
                val quality = getIndexQuality(doc.select("div.container h2").text())

                doc.select("a.button").forEach { link ->
                    val servername = link.text()
                    val href = link.absUrl("href")
                    if (href.isNotBlank()) {
                        callback.invoke(
                            newExtractorLink(
                                servername,
                                "$name [HCloud] $extractedSpecs[$fileSize]",
                                href
                            )
                            {
                                this.referer = btnUrl
                                this.quality = quality
                            }
                        )
                    }
                }
            }
        }

        return true
    }
}