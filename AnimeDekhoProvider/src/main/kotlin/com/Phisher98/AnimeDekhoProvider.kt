package com.phisher98

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.amap
import org.jsoup.nodes.Element
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId

open class AnimeDekhoProvider : MainAPI() {
    override var mainUrl = "https://animedekho.app"
    override var name = "Anime Dekho"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true

    override val supportedTypes =
        setOf(
            TvType.Cartoon,
            TvType.Anime,
            TvType.AnimeMovie,
            TvType.Movie,
        )

    override val mainPage =
        mainPageOf(
            "/category/anime/" to "Anime",
            "/category/cartoon/" to "Cartoon",
            "/category/crunchyroll/" to "Crunchyroll",
            "/category/hindi-dub/" to "Hindi",
            "/category/tamil/" to "Tamil",
            "/category/telugu/" to "Telugu"
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val link = "$mainUrl${request.data}"
        val document = app.get(link).document
        val home =
            document.select("article").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = this.selectFirst("a.lnk-blk")?.attr("href") ?: return null
        val title = this.selectFirst("header h2")?.text() ?: "null"
        var posterUrl = this.selectFirst("div figure img")?.attr("src")
        if (posterUrl!!.contains("data:image"))
        {
            posterUrl=this.selectFirst("div figure img")?.attr("data-lazy-src")
        }
        return newAnimeSearchResponse(title, Media(href, posterUrl).toJson(), TvType.Anime, false) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<AnimeSearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("ul[data-results] li article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val media = parseJson<Media>(url)
        val document = app.get(media.url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim()?.substringAfter("Watch Online ")
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringAfter("Watch Online ")?.substringBefore(" Movie in Hindi Dubbed Free") ?: "No Title"
        val poster = fixUrlNull(document.selectFirst("div.post-thumbnail figure img")?.attr("src") ?: media.poster)
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[name=twitter:description]")?.attr("content")
        val year = (document.selectFirst("span.year")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:updated_time]")?.attr("content")
                ?.substringBefore("-"))?.toIntOrNull()

        val tags = document.select("ul.details-lst li:contains(Genres) a").map { it.text() }
        val anilistUrl = document.selectFirst("a[href*='anilist.php?id=']")?.attr("href")
        val malUrl = document.selectFirst("a[href*='myanimelist.php?id=']")?.attr("href")
        val tmdbId = document.select("a[href*='themoviedb.org/tv/'], a[href*='themoviedb.org/movie/']").firstOrNull()?.attr("href")?.let { href ->
            Regex("""themoviedb\.org/(?:tv|movie)/(\d+)""").find(href)?.groupValues?.getOrNull(1)
        }

        var anilistId: Int? = null
        var malId: Int? = null

        if (anilistUrl != null) {
            runCatching {
                val finalUrl = app.get(anilistUrl).url
                anilistId = Regex("""anilist\.co/anime/(\d+)""").find(finalUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        }
        if (malUrl != null) {
            runCatching {
                val finalUrl = app.get(malUrl).url
                malId = Regex("""myanimelist\.net/anime/(\d+)""").find(finalUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        }

        var aniZipData: MetaAnimeData? = null
        var backgroundPoster: String? = null
        var metaPoster: String? = null
        
        val urlsToTry = listOfNotNull(
            tmdbId?.let { "https://api.ani.zip/mappings?themoviedb_id=$it" },
            anilistId?.let { "https://api.ani.zip/mappings?anilist_id=$it" },
            malId?.let { "https://api.ani.zip/mappings?mal_id=$it" }
        )

        for (aniUrl in urlsToTry) {
            val success = runCatching {
                val syncMetaData = app.get(aniUrl).text
                aniZipData = parseJson<MetaAnimeData>(syncMetaData)
                backgroundPoster = aniZipData.images?.find { it.coverType == "Fanart" }?.url
                metaPoster = aniZipData.images?.find { it.coverType == "Poster" }?.url
                if (anilistId == null) anilistId = aniZipData.mappings?.anilistId
                if (malId == null) malId = aniZipData.mappings?.malId
                true
            }.onFailure {
                Log.e("AnimeDekho", "Error fetching ani.zip data for url $aniUrl")
            }.getOrDefault(false)
            
            if (success) break
        }

        val lst = document.select("ul.seasons-lst li")

        return if (lst.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, Media(
                media.url,
                mediaType = 1
            ).toJson()) {
                this.posterUrl = metaPoster ?: poster
                this.backgroundPosterUrl = backgroundPoster ?: poster
                this.plot = plot
                this.year = year
                this.tags = tags
                addMalId(malId)
                addAniListId(anilistId)
                addTMDbId(tmdbId)
            }
        } else {
                val tmdbIdFinal = tmdbId ?: aniZipData?.mappings?.themoviedbId
                val tmdbSeasonData = mutableMapOf<Int, TmdbSeasonResponse>()
                val apiKey = "1865f43a0549ca50d341dd9ab8b29f49"

                val episodesList = document.select("ul.seasons-lst li").mapNotNull { it }
                val seasonsPresent = episodesList.mapNotNull { 
                    it.selectFirst("h3.title > span")?.text()?.substringAfter("S")?.substringBefore("-")?.toIntOrNull() 
                }.distinct()

                if (tmdbIdFinal != null) {
                    seasonsPresent.amap { s ->
                        app.get("https://api.themoviedb.org/3/tv/$tmdbIdFinal/season/$s?api_key=$apiKey").parsedSafe<TmdbSeasonResponse>()?.let { res ->
                            tmdbSeasonData[s] = res
                        }
                    }
                }

                val episodes = episodesList.mapNotNull {
                    val name = it.selectFirst("h3.title")?.ownText() ?: "null"
                    val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val poster = it.selectFirst("div > div > figure > img")?.attr("src")
                    val epString = it.selectFirst("h3.title > span")?.text().toString()
                    val seasonnumber = epString.substringAfter("S").substringBefore("-")
                    val season = seasonnumber.toIntOrNull()
                    val epNumRegex = Regex("""E(\d+)""")
                    val epNumStr = epNumRegex.find(epString)?.groupValues?.getOrNull(1)
                    val epNum = epNumStr?.toIntOrNull()
                    
                    val tmdbEp = if (season != null && epNum != null) {
                        tmdbSeasonData[season]?.episodes?.find { it.episode_number == epNum }
                    } else null

                    val meta = if (tmdbEp == null && (season == 1 || season == null)) aniZipData?.episodes?.get(epNumStr ?: "") else null

                    newEpisode(Media(href, mediaType = 2).toJson())
                    {
                        this.name = tmdbEp?.name?.takeIf { it.isNotBlank() } ?: meta?.title?.get("en") ?: meta?.title?.get("x-jat") ?: name
                        
                        val tmdbImage = tmdbEp?.still_path?.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w500$it" }
                        this.posterUrl = tmdbImage ?: meta?.image ?: poster
                        
                        this.season = season
                        this.episode = epNum
                        this.description = tmdbEp?.overview?.takeIf { it.isNotBlank() } ?: meta?.overview
                        
                        val airDate = tmdbEp?.air_date?.takeIf { it.isNotBlank() && it != "null" } ?: meta?.airDateUtc
                        airDate?.let { addDate(it) }
                        
                        val vote = tmdbEp?.vote_average
                        if (vote != null && vote > 0) {
                            this.score = Score.from10(vote.toString())
                        } else {
                            meta?.rating?.let { this.score = Score.from10(it) }
                        }
                        
                        this.runTime = tmdbEp?.runtime?.takeIf { it > 0 } ?: meta?.runtime
                    }
                }
            val recommendations = document.select("div.swiper-wrapper article").map {
                val recName = it.selectFirst("h2")?.text() ?: "Unknown"
                val recHref = it.selectFirst("a")!!.attr("href")
                val recPosterUrl = it.selectFirst("figure img")?.attr("src")
                val mediadata = Media(
                    url = recHref,
                    poster = recPosterUrl,
                    mediaType = 0 // You can adjust this
                )
                val mediaJson = Gson().toJson(mediadata)
                newAnimeSearchResponse(recName, mediaJson, TvType.Anime) {
                    this.posterUrl = mediadata.poster
                }
            }
            newAnimeLoadResponse(title, url, TvType.Anime) {
                addEpisodes(DubStatus.Subbed, episodes)
                this.posterUrl = metaPoster ?: poster
                this.backgroundPosterUrl = backgroundPoster ?: poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
                malId?.let { addMalId(it) }
                anilistId?.let { addAniListId(it) }
                tmdbId?.let { addTMDbId(it) }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val media = runCatching { parseJson<Media>(data) }.getOrElse {
            Log.e("Error:", "Failed to parse media JSON $it" )
            return false
        }

        //VidStream
        val headers = mapOf("Cookie" to "toronites_server=vidstream")
        val doc = app.get(media.url, headers = headers).document
        doc.select("iframe.serversel[src]").amap { iframe ->
            val serverUrl = iframe.attr("src")
            if (serverUrl.isBlank()) return@amap

            val innerIframeUrl = runCatching {
                app.get(serverUrl).document
                    .selectFirst("iframe[src]")
                    ?.attr("src")
            }.getOrNull()

            if (!innerIframeUrl.isNullOrBlank()) {
                loadExtractor(innerIframeUrl, subtitleCallback, callback)
            }
        }
        //

        val bodyClass = runCatching {
            app.get(media.url).document.selectFirst("body")?.attr("class")
        }.getOrNull()

        val term = Regex("""(?:term|postid)-(\d+)""").find(bodyClass ?: "")
            ?.groupValues?.getOrNull(1)

        if (term.isNullOrEmpty()) {
            Log.e("Error:", "No postid/term ID found in body class: $bodyClass")
            return false
        }

        (0..10).toList().amap { i ->
            val iframeUrl = runCatching {
                app.get("$mainUrl/?trdekho=$i&trid=$term&trtype=${media.mediaType}")
                    .document.selectFirst("iframe")?.attr("src")
            }.getOrNull()
            if (!iframeUrl.isNullOrEmpty()) {
                Log.d("Error:", "Found iframe: $iframeUrl")
                runCatching {
                    loadExtractor(iframeUrl, subtitleCallback, callback)
                }.onFailure {
                    Log.e("Error:", "Failed to load extractor for $iframeUrl $it")
                }
            } else {
                Log.w("Error:", "No iframe found for iteration $i")
            }
        }

        return true
    }


    data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)

}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaImage(
    @param:JsonProperty("coverType") val coverType: String?,
    @param:JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisode(
    @param:JsonProperty("episode") val episode: String?,
    @param:JsonProperty("airDateUtc") val airDateUtc: String?,
    @param:JsonProperty("runtime") val runtime: Int?,
    @param:JsonProperty("image") val image: String?,
    @param:JsonProperty("title") val title: Map<String, String>?,
    @param:JsonProperty("overview") val overview: String?,
    @param:JsonProperty("rating") val rating: String?,
    @param:JsonProperty("finaleType") val finaleType: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaMappings(
    @param:JsonProperty("mal_id") val malId: Int? = null,
    @param:JsonProperty("anilist_id") val anilistId: Int? = null,
    @param:JsonProperty("themoviedb_id") val themoviedbId: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @param:JsonProperty("titles") val titles: Map<String, String>?,
    @param:JsonProperty("images") val images: List<MetaImage>?,
    @param:JsonProperty("episodes") val episodes: Map<String, MetaEpisode>?,
    @param:JsonProperty("mappings") val mappings: MetaMappings? = null
)

data class TmdbSeasonResponse(
    val episodes: List<TmdbEpisode>?
)

data class TmdbEpisode(
    val episode_number: Int?,
    val name: String?,
    val still_path: String?,
    val overview: String?,
    val air_date: String?,
    val vote_average: Double?,
    val runtime: Int?
)
