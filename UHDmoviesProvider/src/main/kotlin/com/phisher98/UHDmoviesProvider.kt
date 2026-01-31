package com.phisher98


import android.util.Log
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class UHDmoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl: String = runBlocking {
        UHDmoviesProviderPlugin.getDomains()?.UHDMovies ?: "https://uhdmovies.rip"
    }
    override var name = "UHDmovies"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "" to "Home",
        "movies/" to "Movies",
        "tv-series/" to "TV Series",
        "tv-shows/" to "TV Shows",
        "movies/dual-audio-movies/" to "Dual Audio Movies",
        "movies/collection-movies/" to "Hollywood",
        "tv-shows/netflix/" to "Netflix",
        "web-series/" to "Web Series",
        "amazon-prime/" to "Amazon Prime",
    )

    private suspend fun cfKiller(url: String): NiceResponse {
        var doc = app.get(url)
        if (doc.documentLarge.select("title").text() == "Just a moment...") {
            doc = app.get(url, interceptor = CloudflareKiller())
        }
        return doc
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            cfKiller("$mainUrl/${request.data}").documentLarge
        } else {
            cfKiller("$mainUrl/${request.data}" + "/page/$page/").documentLarge
        }

        val home = document.select("article.gridlove-post").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val titleRaw = this.select("h1.sanket").text().trim().removePrefix("Download ")
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groups?.get(1)?.value ?: titleRaw
        val href = fixUrl(this.select("div.entry-image > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.entry-image > a > img").attr("src"))
        val quality = getSearchQuality(titleRaw)
        return if (titleRaw.contains("season|S0", true) || titleRaw.contains("episode", true) || titleRaw.contains("S0", true)) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = cfKiller("$mainUrl?s=$query ").documentLarge

        return document.select("article.gridlove-post").mapNotNull {
            it.toSearchResult()
        }
    }

    data class UHDLinks(
        @JsonProperty("sourceName") val sourceName: String,
        @JsonProperty("sourceLink") val sourceLink: String
    )

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).documentLarge
        val titleRaw = doc.select("div.gridlove-content div.entry-header h1.entry-title").text().trim().removePrefix("Download ")
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groupValues?.get(1)?.trim()?.substringBefore("(")?.substringBefore("Season")?.substringBefore("S0") ?: titleRaw.substringBefore("(").substringBefore("Season").substringBefore("S0")
        val img = doc.selectFirst("div.entry-content p img")

        val poster = img?.attr("srcset")
            ?.split(",")
            ?.map { it.trim() }
            ?.maxByOrNull {
                it.substringAfterLast(" ")
                    .removeSuffix("w")
                    .toIntOrNull() ?: 0
            }
            ?.substringBefore(" ")
            ?: img?.attr("src")
        val collectionposter = doc.select("meta[property=og:image]").attr("content")
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(titleRaw)?.value?.toIntOrNull()
        val tags = doc.select("div.entry-category > a.gridlove-cat").map { it.text() }
        val tvTags = doc.selectFirst("h1.entry-title")?.text() ?:""
        val type = if (tvTags.contains("Season") || tvTags.contains("S0")) TvType.TvSeries else TvType.Movie
        val ids = fetchIds(
            title,
            year,
            type == TvType.TvSeries
        )
        val meta = if (!ids.imdbId.isNullOrBlank()) fetchMetaData(ids.imdbId, type) else null
        val metaVideos = meta?.get("videos")?.toList() ?: emptyList()

        val Background = meta?.get("background")?.asText() ?: poster
        val Description = meta?.get("description")?.asText() ?: ""
        val IMDBRating = meta?.get("imdbRating")?.asText()
        val trailer = doc.select("p iframe").attr("src")


        val simklId = ids.imdbId?.let {
            fetchSimklId(it, isSeries = type == TvType.TvSeries)
        }

        return if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            var pTags = doc.select("p:has(a:contains(Episode))")
            if (pTags.isEmpty())
            {
                pTags = doc.select("div:has(a:contains(Episode))")
            }

            val tvSeriesEpisodes = mutableListOf<Episode>()
            val episodesMap: MutableMap<Pair<Int, Int>, List<String>> = mutableMapOf()

            pTags.mapNotNull { pTag ->
                val prevPtag = pTag.previousElementSibling()
                val details = prevPtag ?. text() ?: ""
                val realSeason = Regex("""(?:Season |S0)(\d+)""").find(details) ?. groupValues
                    ?. get(1) ?.toIntOrNull() ?: 0
                val aTags = pTag.select("a:contains(Episode)")

                aTags.mapNotNull { aTag ->
                    val realEp = Regex("""Episode\s+(\d+)""").find(aTag.toString()) ?. groupValues ?. get(1) ?.toIntOrNull() ?: 0
                    val epUrl = aTag.attr("href")
                    val key = Pair(realSeason, realEp)

                    if(!epUrl.isEmpty()) {
                        if (episodesMap.containsKey(key)) {
                            val currentList = episodesMap[key] ?: emptyList()
                            val newList = currentList.toMutableList()
                            newList.add(epUrl)
                            episodesMap[key] = newList
                        } else {
                            episodesMap[key] = mutableListOf(epUrl)
                        }
                    }

                }
            }

            for ((key, value) in episodesMap) {
                if(key.first == 0 || key.second == 0) continue

                val epMeta = metaVideos.firstOrNull {
                    it["season"]?.asInt() == key.first &&
                        it["episode"]?.asInt() == key.second
                }

                val data = value.map { source->
                    UHDLinks(
                        "UHD",
                        source
                    )
                }

                val epName =
                    epMeta?.get("name")?.asText()?.takeIf { it.isNotBlank() }
                        ?: "Episode ${key.second}"

                val epDesc =
                    epMeta?.get("overview")?.asText()
                        ?: epMeta?.get("description")?.asText()
                        ?: ""

                val epThumb =
                    epMeta?.get("thumbnail")?.asText()?.takeIf { it.isNotBlank() }
                        ?: ""

                val aired =
                    epMeta?.get("firstAired")?.asText()?.takeIf { it.isNotBlank() }
                        ?: ""
                tvSeriesEpisodes.add(
                    newEpisode(data) {
                        this.name = epName
                        this.season = key.first
                        this.episode = key.second
                        this.posterUrl = epThumb
                        this.description = epDesc
                        addDate(aired)
                    }
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, tvSeriesEpisodes) {
                this.posterUrl = poster?.trim() ?: collectionposter
                this.year = year
                this.tags = tags
                addTrailer(trailer)
                addImdbId(ids.imdbId)
                addTMDbId(ids.tmdbId.toString())
                addSimklId(simklId)
                this.backgroundPosterUrl = Background ?: collectionposter
                this.plot = Description
                this.year = year
                this.tags = tags
                this.score = Score.from10(IMDBRating)
            }
        } else {
            val iframeRegex = Regex("""\[.*]""")
            val iframe = doc.select("""div.entry-content > p""").amap { it }.filter {
                iframeRegex.find(it.toString()) != null
            }
            val data = iframe.amap {
                UHDLinks(
                    it.text().substringBefore("Download"),
                    it.nextElementSibling()?.select("a.maxbutton-1")?.attr("href") ?: ""
                )
            }
            Log.d("Phisher","$poster $collectionposter $Background")
            newMovieLoadResponse(title, url, TvType.Movie, data) {
                this.posterUrl = poster?.trim() ?: collectionposter
                this.year = year
                this.tags = tags
                addTrailer(trailer)
                addImdbId(ids.imdbId)
                addTMDbId(ids.tmdbId.toString())
                addSimklId(simklId)
                this.backgroundPosterUrl = Background ?: collectionposter
                this.plot = Description
                this.year = year
                this.tags = tags
                this.score = Score.from10(IMDBRating)
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        if (data.startsWith("https://")) {
            val finalLink = if (data.contains("unblockedgames")) {
                bypassHrefli(data) ?: return@coroutineScope true
            } else {
                data
            }
            loadExtractor(finalLink, subtitleCallback, callback)
        } else {
            val sources = parseJson<ArrayList<UHDLinks>>(data)

            sources.forEach { me ->
                launch {
                    runCatching {
                        val link = me.sourceLink
                        val finalLink = if (link.contains("unblockedgames")) {
                            bypassHrefli(link) ?: return@runCatching
                        } else {
                            link
                        }
                        loadExtractor(finalLink, subtitleCallback, callback)
                    }.onFailure {
                    }
                }
            }
        }
        return@coroutineScope true
    }
}

suspend fun fetchIds(
    title: String,
    year: Int?,
    isSeries: Boolean
): IdResult {
    val TMDB_API = "https://api.themoviedb.org/3"
    val TMDB_API_KEY = "1865f43a0549ca50d341dd9ab8b29f49"

    val type = if (isSeries) "tv" else "movie"

    val searchUrl = buildString {
        append("$TMDB_API/search/$type")
        append("?api_key=$TMDB_API_KEY")
        append("&query=${title.urlEncode()}")
        if (year != null) {
            append(if (isSeries) "&first_air_date_year=$year" else "&year=$year")
        }
    }

    val searchJson = JSONObject(app.get(searchUrl).textLarge)
    val results = searchJson.optJSONArray("results")
    val tmdbId = results?.optJSONObject(0)?.optInt("id")

    val imdbId = tmdbId?.let { id ->
        val extUrl = "$TMDB_API/$type/$id/external_ids?api_key=$TMDB_API_KEY"
        val extJson = JSONObject(app.get(extUrl).textLarge)
        extJson.optString("imdb_id").takeIf { it.isNotBlank() }
    }

    return IdResult(
        tmdbId = tmdbId,
        imdbId = imdbId
    )
}

data class IdResult(
    val tmdbId: Int?,
    val imdbId: String?
)

fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

private suspend fun fetchMetaData(imdbId: String?, type: TvType): JsonNode? {
    if (imdbId.isNullOrBlank()) return null

    val metaType = if (type == TvType.TvSeries) "series" else "movie"
    val url = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta/$metaType/$imdbId.json"

    return try {
        val resp = app.get(url).text
        mapper.readTree(resp)["meta"]
    } catch (_: Exception) {
        null
    }
}

private suspend fun fetchSimklId(
    imdbId: String,
    isSeries: Boolean
): Int? = runCatching {
    val type = if (isSeries) "tv" else "movies"
    val url = "https://api.simkl.com/$type/$imdbId?client_id=${BuildConfig.SIMKL_CLIENT_ID}"

    JSONObject(app.get(url).text)
        .optJSONObject("ids")
        ?.optInt("simkl")
        ?.takeIf { it != 0 }
}.getOrNull()
