package com.movies4u

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
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
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.text.Normalizer

class Movies4u : MainAPI() {
    override var mainUrl: String = runBlocking {
        Movies4uProvider.getDomains()?.movies4u ?: "https://movies4u.kitchen"
    }
    override var name = "Movies4u"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object
    {
        const val TMDBAPIKEY = "1865f43a0549ca50d341dd9ab8b29f49"
        const val TMDBBASE = "https://image.tmdb.org/t/p/original"
        const val TMDBAPI = "https://wild-surf-4a0d.phisher1.workers.dev"
    }

    override val mainPage = mainPageOf(
        "" to "Home",
        "category/telugu/" to "Telugu",
        "category/bollywood/" to "BollyWood",
        "category/hollywood/" to "HollyWood",
        "category/web-series/" to "WEB-Series",
        "category/anime/" to "Anime / Animation",
        "category/k-drama/" to "K-Drama",
        "category/south-hindi-movies/" to "South Hindi Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "$mainUrl/${request.data}" else "$mainUrl/${request.data}page/$page"
        val document = app.get(url).document
        val items = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("h2 a") ?: return null
        val img = selectFirst("img") ?: return null

        val href = fixUrl(aTag.attr("href"))
        val rawText = aTag.ownText().trim()

        val title = rawText.substringBefore(" (").trim()
        val year = rawText.substringAfter("(", "").substringBefore(")").takeIf { it.matches(Regex("\\d{4}")) }
        val lang = rawText.substringAfter("[", "").substringBefore("]").takeIf { it.isNotBlank() }

        val fullTitle = buildString {
            append(title)
            if (year != null) append(" ($year)")
            if (lang != null) append(" [$lang]")
        }

        val posterUrl = fixUrlNull(img.attr("src"))

        return newMovieSearchResponse(fullTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getSearchQuality(rawText)
        }
    }


    override suspend fun search(query:String,page:Int):SearchResponseList{
        val url=if(page==1)"$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val document=app.get(url).document
        val results=document.select("article").mapNotNull{article->
            val aTag=article.selectFirst("h3.entry-title a")?:return@mapNotNull null
            val img=article.selectFirst("div.post-thumbnail img")?:return@mapNotNull null
            val href=fixUrl(aTag.attr("href"))
            val rawText=aTag.text().trim()
            val title=rawText.substringBefore("(").trim()
            val year=rawText.substringAfter("(","").substringBefore(")").takeIf{it.matches(Regex("\\d{4}"))}
            val lang=rawText.substringAfter("[","").substringBefore("]").takeIf{it.isNotBlank()}
            val fullTitle=buildString{
                append(title)
                if(year!=null)append(" ($year)")
                if(lang!=null)append(" [$lang]")
            }
            val poster=fixUrlNull(img.attr("src"))
            val tvType=if(rawText.contains("Season",true)||rawText.contains("Series",true))TvType.TvSeries else if(rawText.contains("Anime",true))TvType.Anime else TvType.Movie
            newMovieSearchResponse(fullTitle,href,tvType){
                this.posterUrl=poster
                this.quality=getSearchQuality(rawText)
            }
        }
        return results.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        var title = document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore("(")?.trim() ?: "Unknown Title"
        val hrefList = document.select("div.downloads-btns-div a[href]").map { it.attr("href") }
        val plot = document.selectFirst("h3.movie-title:contains(Storyline:)")?.nextElementSibling()?.takeIf { it.tagName() == "p" }?.text()
        val poster = document.selectFirst("div.post-thumbnail img")?.attr("src")
        val typeraw = document.selectFirst("div.single-service-content h1")?.text() ?: ""
        val tvtype = if (typeraw.contains("Series", true)) TvType.TvSeries else TvType.Movie
        val isMovie = tvtype == TvType.Movie

        var description: String? = plot
        var background = poster
        var year: Int? = null
        var actorData: List<ActorData> = emptyList()

        val imdbId = document.selectFirst("a[href*='imdb.com/title/']")?.attr("href")?.substringAfter("/title/")?.substringBefore("/").orEmpty()
        var tmdbIdResolved = ""

        // ---------- TMDB FIND ----------
        if (imdbId.isNotBlank()) {
            try {
                val json = JSONObject(app.get("$TMDBAPI/find/$imdbId?api_key=$TMDBAPIKEY&external_source=imdb_id").text)
                tmdbIdResolved =
                    (if (isMovie)
                        json.optJSONArray("movie_results")
                    else
                        json.optJSONArray("tv_results"))
                        ?.optJSONObject(0)
                        ?.optInt("id")
                        ?.toString()
                        .orEmpty()

            } catch (_: Exception) {}
        }

        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = "https://api.themoviedb.org/3",
            apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
            type = tvtype,
            tmdbId = tmdbIdResolved.toIntOrNull(),
            appLangCode = "en"
        )

        // ---------- TMDB SEARCH ----------
        if (tmdbIdResolved.isBlank()) {
            try {

                val type = if (isMovie) "movie" else "tv"

                val json =
                    JSONObject(
                        app.get(
                            "$TMDBAPI/search/$type?api_key=$TMDBAPIKEY&query=$title"
                        ).text
                    )

                tmdbIdResolved =
                    json.optJSONArray("results")
                        ?.optJSONObject(0)
                        ?.optInt("id")
                        ?.toString()
                        .orEmpty()

            } catch (_: Exception) {}
        }

        // ---------- LOAD EPISODE METADATA ----------
        val videoMap = HashMap<String, VideoLocal>()

        if (tmdbIdResolved.isNotBlank() && !isMovie) {
            val seasons = document.select("div.download-links-div h4").mapNotNull { Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE).find(it.text())?.groupValues?.getOrNull(1)?.toIntOrNull() }.distinct()
            seasons.forEach { seasonNum ->
                try {
                    val seasonJson = JSONObject(app.get("$TMDBAPI/tv/$tmdbIdResolved/season/$seasonNum?api_key=$TMDBAPIKEY").text)
                    val arr = seasonJson.optJSONArray("episodes") ?: return@forEach
                    for (i in 0 until arr.length()) {
                        val ep = arr.optJSONObject(i) ?: continue
                        val epNum = ep.optInt("episode_number")
                        videoMap["${seasonNum}_${epNum}"] =
                            VideoLocal(
                                title = ep.optString("name"),
                                season = seasonNum,
                                episode = epNum,
                                overview = ep.optString("overview"),
                                thumbnail = ep.optString("still_path").takeIf { it.isNotBlank() }?.let { TMDBBASE + it },
                                released = ep.optString("air_date"),
                                rating = ep.optDouble("vote_average", 0.0)
                            )
                    }

                } catch (_: Exception) {}
            }
        }


        // ---------- COLLECT ALL LINKS ----------
        val episodesMap = HashMap<String, MutableList<String>>()
        document.select("div.download-links-div h4").forEach { h4 ->
                val seasonNum = Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE).find(h4.text())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val qualityLinks = h4.nextElementSibling()?.select("a[href]")?.map { it.attr("href") } ?: return@forEach

                qualityLinks.forEach { qualityLink ->
                    try {
                        val seasonDoc = app.get(qualityLink).document
                        val episodeBlocks = seasonDoc.select("h5")
                        if (episodeBlocks.isNotEmpty()) {
                            episodeBlocks.forEach { h5 ->

                                val epNum = Regex("""Episodes:\s*(\d+)""").find(h5.text())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEach
                                val links = h5.nextElementSibling()?.select("a[href]")?.map { it.attr("href") } ?: return@forEach
                                val key = "${seasonNum}_${epNum}"
                                val list = episodesMap.getOrPut(key) { mutableListOf() }
                                list.addAll(links)
                            }

                        } else {

                            val key = "${seasonNum}_1"

                            val list = episodesMap.getOrPut(key) { mutableListOf() }
                            list.add(qualityLink)
                        }

                    } catch (_: Exception) {}
                }
            }

        // ---------- BUILD EPISODES ----------
        val episodes=episodesMap.map{(key,links)->

            val parts=key.split("_")
            val seasonNum=parts[0].toInt()
            val epNum=parts[1].toInt()
            val meta=videoMap[key]

            val isCompleteSeason=meta==null||meta.title.isNullOrBlank()

            newEpisode(links.distinct().toJson()){
                this.name=when{
                    !meta?.title.isNullOrBlank()-> meta.title
                    isCompleteSeason->"Complete Season $seasonNum"
                    else->"Episode $epNum"
                }

                this.season=seasonNum
                this.episode=epNum

                this.description=when{
                    !meta?.overview.isNullOrBlank()-> meta.overview
                    isCompleteSeason->"Complete Season $seasonNum"
                    else->plot
                }

                this.posterUrl=meta?.thumbnail?:poster
                this.score=meta?.rating?.let{Score.from10(it)}
                addDate(meta?.released)
            }

        }.sortedWith(compareBy({it.season},{it.episode}))


        if (tmdbIdResolved.isNotBlank()) {
            try {
                val type = if (isMovie) "movie" else "tv"
                val json = JSONObject(
                    app.get(
                        "$TMDBAPI/$type/$tmdbIdResolved?api_key=$TMDBAPIKEY&append_to_response=credits"
                    ).text
                )

                title = json.optString("name").ifBlank { json.optString("title") }.ifBlank { title }
                description = json.optString("overview").ifBlank { description }
                background = json.optString("backdrop_path").takeIf { it.isNotBlank() }?.let { TMDBBASE + it } ?: background
                year = json.optString("first_air_date").ifBlank { json.optString("release_date") }.take(4).toIntOrNull()
                val actorDataList = mutableListOf<ActorData>()

                json.optJSONObject("credits")?.optJSONArray("cast")?.let { castArr ->
                        for (i in 0 until castArr.length()) {
                            val c = castArr.optJSONObject(i) ?: continue
                            val name = c.optString("name").takeIf { it.isNotBlank() } ?: c.optString("original_name")
                            val profile = c.optString("profile_path").takeIf { it.isNotBlank() }?.let { TMDBBASE + it }
                            val character = c.optString("character").takeIf { it.isNotBlank() }
                            val actor =
                                Actor(
                                    name = name,
                                    image = profile
                                )

                            actorDataList += ActorData(
                                    actor = actor,
                                    roleString = character
                                )
                        }
                    }
                actorData = actorDataList
            } catch (_: Exception) {}
        }

        return if (!isMovie) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes)
            {
                this.backgroundPosterUrl = background ?: poster
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.actors = actorData
                addImdbUrl(imdbId)
            }

        } else {
            newMovieLoadResponse(title, url, TvType.Movie, hrefList.firstOrNull() ?: url)
            {
                this.backgroundPosterUrl = background ?: poster
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.actors = actorData
                addImdbUrl(imdbId)
            }
        }
    }



    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("["))
        {
            val links = tryParseJson<List<String>>(data)
            if (links.isNullOrEmpty()) return false
            links.forEach { link ->
                if (link.isNotBlank()) { loadExtractor(link, name, subtitleCallback, callback)
                }
            }
        }
        else loadExtractor(data, name, subtitleCallback, callback)
        return true
    }


    fun getSearchQuality(check: String?): SearchQuality? {
        val s = check ?: return null
        val u = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val patterns = listOf(
            Regex("\\b(4k|ds4k|uhd|2160p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.FourK,

            // CAM / THEATRE SOURCES FIRST
            Regex("\\b(hdts|hdcam|hdtc)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HdCam,
            Regex("\\b(camrip|cam[- ]?rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip,
            Regex("\\b(cam)\\b", RegexOption.IGNORE_CASE) to SearchQuality.Cam,

            // WEB / RIP
            Regex("\\b(web[- ]?dl|webrip|webdl)\\b", RegexOption.IGNORE_CASE) to SearchQuality.WebRip,

            // BLURAY
            Regex("\\b(bluray|bdrip|blu[- ]?ray)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,

            // RESOLUTIONS
            Regex("\\b(1440p|qhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.BlueRay,
            Regex("\\b(1080p|fullhd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,
            Regex("\\b(720p)\\b", RegexOption.IGNORE_CASE) to SearchQuality.SD,

            // GENERIC HD LAST
            Regex("\\b(hdrip|hdtv)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HD,

            Regex("\\b(dvd)\\b", RegexOption.IGNORE_CASE) to SearchQuality.DVD,
            Regex("\\b(hq)\\b", RegexOption.IGNORE_CASE) to SearchQuality.HQ,
            Regex("\\b(rip)\\b", RegexOption.IGNORE_CASE) to SearchQuality.CamRip
        )


        for ((regex, quality) in patterns) if (regex.containsMatchIn(u)) return quality
        return null
    }
}

