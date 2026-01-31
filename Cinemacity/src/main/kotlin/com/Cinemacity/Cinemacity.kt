package com.Cinemacity

import android.util.Log
import com.google.gson.Gson
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element


class Cinemacity : MainAPI() {
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries
    )
    companion object
    {
        val headers = mapOf(
            "Cookie" to base64Decode("ZGxlX3VzZXJfaWQ9MzI3Mjk7IGRsZV9wYXNzd29yZD04OTQxNzFjNmE4ZGFiMThlZTU5NGQ1YzY1MjAwOWEzNTs=")
        )
        private const val TMDBIMAGEBASEURL = "https://image.tmdb.org/t/p/original"
        private const val cinemeta_url = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta"
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

    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "tv-series" to "TV Series",
        "xfsearch/genre/animation" to "Animation",
        "xfsearch/genre/documentary" to "Documentary",
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = if (page==1) app.get("$mainUrl/${request.data}").document
        else app.get("$mainUrl/${request.data}/page/$page").document

        val home = doc.select("div.dar-short_item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home, true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.children().firstOrNull { it.tagName() == "a" }?.ownText()?.substringBefore("(")?.trim().orEmpty()
        val href = fixUrl(this.children().firstOrNull { it.tagName() == "a" }?.attr("href") ?: "")
        val posterUrl = fixUrlNull(this.select("div.dar-short_bg a ").attr("href"))
        val score = this.selectFirst("span.rating-color")?.ownText()
        val quality = this
            .selectFirst("div.dar-short_bg.e-cover > div span:nth-child(2) > a")
            ?.text()
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.contains("TS", true)) "TS" else "HD" }
            ?: run {
                if (
                    this.selectFirst("div.dar-short_bg.e-cover > div > span")
                        ?.text()
                        ?.contains("TS", true) == true
                ) "TS" else "HD"
            }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
            this.quality = getQualityFromString(quality)
        }
    }


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val doc = app.get("$mainUrl/index.php?do=search&subaction=search&search_start=$page&full_search=0&story=$query").document
        val res = doc.select("div.dar-short_item").mapNotNull { it.toSearchResult() }
        return res.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse {
        val page = app.get(url, headers)
        val doc = page.document

        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val title = ogTitle.substringBefore("(").trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val bgposter = doc.selectFirst("div.dar-full_bg a")?.attr("href")
        val trailer = doc.select("div.dar-full_bg.e-cover > div").attr("data-vbg")

        val audioLanguages = doc
            .select("li")
            .firstOrNull {
                it.selectFirst("span")?.text()
                    ?.equals("Audio language", ignoreCase = true) == true
            }
            ?.select("span:eq(1) a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(", ")

        val descriptions = doc.selectFirst("#about div.ta-full_text1")?.text()


        val recommendation = doc.select("div.ta-rel > div.ta-rel_item").map {
            val title = it.select("a").text().substringBefore("(").trim()
            val href = fixUrl(it.selectFirst("> div > a")?.attr("href") ?: "")
            val score = it.select("span.rating-color1").text()
            val posterUrl=it.selectFirst("div > a")?.attr("href")

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        }

        val year = ogTitle.substringAfter("(", "").substringBefore(")").toIntOrNull()
        var contenttype = doc.select("div.dar-full_meta > span:nth-child(5) > a").text()

        val tvtype = if (url.contains("/movies/", true)) TvType.Movie else TvType.TvSeries
        val tmdbmetatype = if (tvtype == TvType.TvSeries) "tv" else "movie"

        var genre: List<String>? = null
        var background: String? = null
        var description: String? = null


        val imdbId = doc
            .select("div.ta-full_rating1 > div")
            .mapNotNull { it.attr("onclick") }
            .firstNotNullOfOrNull { Regex("tt\\d+").find(it)?.value }

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

        val logoPath = imdbId?.let {
            "https://live.metahub.space/logo/medium/$it/img"
        }

        val creditsJson = tmdbId?.let {
            runCatching {
                app.get(
                    "https://api.themoviedb.org/3/$tmdbmetatype/$it/credits" +
                            "?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US"
                ).textLarge
            }.getOrNull()
        }

        val castList = parseCredits(creditsJson)
        val typeset = if (tvtype == TvType.TvSeries) "series" else "movie"

        val responseData = imdbId?.takeIf { it.isNotBlank() }?.let {
            val text = app.get("$cinemeta_url/$typeset/$it.json").text
            if (text.startsWith("{")) Gson().fromJson(text, ResponseData::class.java) else null
        }

        responseData?.meta?.let {
            description = it.description ?: descriptions
            background = it.background ?: poster
            genre = it.genres
        }

        val epMetaMap: Map<String, ResponseData.Meta.EpisodeDetails> =
            responseData?.meta?.videos
                ?.filter { it.season != null && it.episode != null }
                ?.associateBy { "${it.season}:${it.episode}" }
                ?: emptyMap()


        /* ---------------- PlayerJS parsing ---------------- */

        val playerScript = doc
            .select("script:containsData(atob)")
            .getOrNull(1)
            ?.data()
            ?: error("PlayerJS not found; only torrent links available")

        val decodedPlayer = base64Decode(
            playerScript.substringAfter("atob(\"").substringBefore("\")")
        )

        val playerJson = JSONObject(
            decodedPlayer
                .substringAfter("new Playerjs(")
                .substringBeforeLast(");")
        )


        /* ---------------- SAFE file parsing ---------------- */

        val rawFile = playerJson.opt("file")
            ?: error("PlayerJS: missing file field")

        val fileArray: JSONArray = when (rawFile) {
            is JSONArray -> rawFile
            is String -> {
                val value = rawFile.trim()

                when {
                    value.startsWith("[") && value.endsWith("]") ->
                        JSONArray(value)

                    value.startsWith("{") && value.endsWith("}") ->
                        JSONArray().apply { put(JSONObject(value)) }

                    value.isNotBlank() ->
                        JSONArray().apply {
                            put(JSONObject().apply {
                                put("file", value)
                            })
                        }

                    else -> error("PlayerJS: empty file string")
                }
            }
            else -> error("PlayerJS: unsupported file type")
        }


        val seasonRegex = Regex("Season\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val episodeRegex = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)

        val episodeList = mutableListOf<Episode>()

        val movieHrefs: String? = fileArray.optJSONObject(0)
                ?.takeIf { !it.has("folder") }
                ?.optString("file")
                ?.takeIf { it.isNotBlank() }

        val movieSubtitleTracks = parseSubtitles(
            when {
                playerJson.opt("subtitle") is String ->
                    playerJson.optString("subtitle")
                fileArray.optJSONObject(0)?.opt("subtitle") is String ->
                    fileArray.optJSONObject(0)?.optString("subtitle")
                else -> null
            }
        )

        val moviejson = movieHrefs?.let {
            JSONObject().apply {
                put("streamUrl", it)
                put("subtitleTracks", movieSubtitleTracks)
            }.toString()
        }

        if (tvtype == TvType.TvSeries) {
            for (i in 0 until fileArray.length()) {
                val seasonJson = fileArray.getJSONObject(i)

                val seasonNumber = seasonRegex
                    .find(seasonJson.optString("title"))
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: continue

                val episodes = seasonJson.optJSONArray("folder") ?: continue
                for (j in 0 until episodes.length()) {
                    val epJson = episodes.getJSONObject(j)

                    val episodeNumber = episodeRegex
                        .find(epJson.optString("title"))
                        ?.groupValues?.get(1)?.toIntOrNull()
                        ?: continue

                    val streamUrls = mutableListOf<String>()

                    epJson.optString("file")
                        .takeIf { it.isNotBlank() }
                        ?.let { streamUrls += it }

                    epJson.optJSONArray("folder")?.let { sources ->
                        for (k in 0 until sources.length()) {
                            sources.optJSONObject(k)
                                ?.optString("file")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { streamUrls += it }
                        }
                    }

                    if (streamUrls.isEmpty()) continue

                    val metaKey = "$seasonNumber:$episodeNumber"
                    val epMeta = epMetaMap[metaKey]

                    val epSubtitleTracks =
                        parseSubtitles(epJson.optString("subtitle"))

                    val epjson = JSONObject().apply {
                        put("streams", JSONArray(streamUrls))
                        put("subtitleTracks", epSubtitleTracks)
                    }.toString()

                    episodeList += newEpisode(epjson) {
                        this.season = seasonNumber
                        this.episode = episodeNumber
                        this.name = epMeta?.title ?: "S${seasonNumber}E${episodeNumber}"
                        this.description = epMeta?.overview
                        this.posterUrl = epMeta?.thumbnail
                        addDate(epMeta?.released)
                    }
                }
            }
            return newTvSeriesLoadResponse(
                responseData?.meta?.name ?: title,
                url,
                TvType.TvSeries,
                episodeList
            ) {
                this.backgroundPosterUrl = background ?: bgposter
                this.posterUrl = poster
                try { this.logoUrl = logoPath } catch(_:Throwable){}
                this.year = year ?: responseData?.meta?.year?.toIntOrNull()
                this.plot = buildString {
                    append(description ?: descriptions)
                    if (!audioLanguages.isNullOrBlank()) {
                        append(" — Audio: ")
                        append(audioLanguages)
                    }
                }
                this.recommendations = recommendation
                this.tags = genre
                this.actors = castList
                this.score = Score.from10(responseData?.meta?.imdbRating)
                this.contentRating = responseData?.meta?.appExtras?.certification
                addImdbId(imdbId)
                addTMDbId(tmdbId)
                addTrailer(trailer)
            }
        }

        responseData?.meta?.appExtras?.certification?.let { Log.d("Phisher", it) }

        return newMovieLoadResponse(
            responseData?.meta?.name ?: title,
            url,
            TvType.Movie,
            moviejson
        ) {
            this.backgroundPosterUrl = background ?: bgposter
            this.posterUrl = poster
            try { this.logoUrl = logoPath } catch(_:Throwable){}
            this.year = year ?: responseData?.meta?.year?.toIntOrNull()
            this.plot = buildString {
                append(description ?: descriptions)
                if (!audioLanguages.isNullOrBlank()) {
                    append(" — Audio: ")
                    append(audioLanguages)
                }
            }
            this.recommendations = recommendation
            this.tags = genre
            this.actors = castList
            this.contentRating = responseData?.meta?.appExtras?.certification
            this.score = Score.from10(responseData?.meta?.imdbRating)
            addImdbId(imdbId)
            addTMDbId(tmdbId)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val obj = JSONObject(data)

        obj.optJSONArray("subtitleTracks")?.let { subs ->
            for (i in 0 until subs.length()) {
                val s = subs.getJSONObject(i)
                subtitleCallback(
                    newSubtitleFile(
                        s.getString("language"),
                        s.getString("subtitleUrl")
                    )
                )
            }
        }

        val streamUrls = mutableListOf<String>()

        obj.optJSONArray("streams")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)
                    .takeIf { it.isNotBlank() }
                    ?.let { streamUrls += it }
            }
        }

        if (streamUrls.isEmpty()) {
            obj.optString("streamUrl")
                .takeIf { it.isNotBlank() }
                ?.let { streamUrls += it }
        }

        if (streamUrls.isEmpty()) return false

        streamUrls.forEach { url ->
            callback(
                newExtractorLink(
                    name,
                    name,
                    url,
                    INFER_TYPE
                ) {
                    referer = mainUrl
                    quality = extractQuality(url)
                }
            )
        }

        return true
    }


    fun extractQuality(url: String): Int {
        return when {
            url.contains("2160p") -> Qualities.P2160.value
            url.contains("1440p") -> Qualities.P1440.value
            url.contains("1080p") -> Qualities.P1080.value
            url.contains("720p")  -> Qualities.P720.value
            url.contains("480p")  -> Qualities.P480.value
            url.contains("360p")  -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    fun parseSubtitles(raw: String?): JSONArray {
        val tracks = JSONArray()
        if (raw.isNullOrBlank()) return tracks

        raw.split(",").forEach { entry ->
            val match = Regex("""\[(.+?)](https?://.+)""").find(entry.trim())
            if (match != null) {
                tracks.put(
                    JSONObject().apply {
                        put("language", match.groupValues[1])
                        put("subtitleUrl", match.groupValues[2])
                    }
                )
            }
        }
        return tracks
    }

}