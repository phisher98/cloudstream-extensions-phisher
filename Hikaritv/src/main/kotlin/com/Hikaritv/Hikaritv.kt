package com.hikaritv

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar

class Hikaritv : MainAPI() {
    override var mainUrl = "https://hikari.gg"
    override var name = "HikariTV"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object
    {
        private val HikariAPI="https://api.hikari.gg"
        private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    }

    override val supportedSyncNames = setOf(
        SyncIdName.MyAnimeList,
    )

    override val mainPage = mainPageOf(
        "api/anime/?sort=created_at&order=asc&ani_stats=1&ani_release=$currentYear&page=" to "OnGoing Animes",
        "api/anime/?sort=created_at&order=asc&ani_type=2&page=" to "Anime Movies",
        "api/anime/?sort=created_at&order=asc&ani_stats=2&page=" to "Completed Animes",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val res = app.get("$HikariAPI/${request.data}$page").parsedSafe<HomePage>()
        val home = res?.results?.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home!!)
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episodes/") -> {
                var title = uri.substringAfter("$mainUrl/episodes/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }
            uri.contains("/seasons/") -> {
                var title = uri.substringAfter("$mainUrl/seasons/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }
            else -> {
                uri
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 a")?.text() ?: return null
        val href = getProperLink(fixUrl(this.selectFirst("h3 > a")!!.attr("href")))
        val posterUrl = fixUrlNull(this.select("img").last()?.getImageAttr())
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            this.quality = quality
        }

    }

    private fun Result.toSearchResult(): SearchResponse? {
        val title=this.aniName
        val href="$mainUrl/info/${this.uid}"
        val posterUrl=this.aniPoster
        val episodes=this.aniEp.toIntOrNull()
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDub(episodes)
            addSub(episodes)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?keyword=$query").document
        return document.select("#main-wrapper div.tab-content div.flw-item").map {
            val title = it.selectFirst("h3 a")?.text() ?: "Unknown"
            val href = getProperLink(fixUrl(it.selectFirst("h3 > a")!!.attr("href")))
            val posterlink=fixUrlNull(it.select("img").last()?.getImageAttr())
            newMovieSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterlink
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val animeId = url.substringAfter("/info/").substringBefore("/").toIntOrNull()
        val syncData = app.get("https://api.ani.zip/mappings?mal_id=$animeId").toString()
        val animeData = parseAnimeData(syncData)
        val response = app.get("$HikariAPI/api/anime/uid/$animeId").parsedSafe<Load>()
        val animeTitle = response?.aniEname ?: animeData.titles?.get("en") ?: "Unknown"
        val posterUrl = animeData.images?.firstOrNull { it.coverType == "Fanart" }?.url
            ?: response?.aniPoster
        Log.d("Phisher",posterUrl.toString())
        val aniGenre = response?.aniGenre ?: ""
        val genreTags = aniGenre.split(",")
        val releaseYear =response?.aniRelease
        val animeDescription = response?.aniSynonyms
        val tvType = if (response?.aniType == 1) TvType.TvSeries else TvType.Movie

        if (tvType == TvType.TvSeries) {
            val subbedEpisodes = mutableListOf<Episode>()
            val responseJson = app.get("$HikariAPI/api/episode/uid/$animeId").text
            val type = object : TypeToken<List<EpisodeItem>>() {}.type
            val episodesList = Gson().fromJson<List<EpisodeItem>>(responseJson, type)
            episodesList.forEach { episode ->
                val episodeNumber = episode.ep_id_name.toIntOrNull() ?: return@forEach
                val episodeName = episode.ep_name
                subbedEpisodes += newEpisode("$HikariAPI/api/embed/$animeId/$episodeNumber") {
                    this.name = episodeName
                    this.season = 1
                    this.episode = episodeNumber
                    this.posterUrl = animeData.episodes?.get(episodeNumber.toString())?.image
                    this.description = animeData.episodes?.get(episodeNumber.toString())?.overview ?: "No summary available"
                }
            }

            return newAnimeLoadResponse(animeTitle, url, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = releaseYear
                this.plot = animeDescription
                this.tags = genreTags
                addEpisodes(DubStatus.Subbed, subbedEpisodes)
                addMalId(animeId)
            }
        } else {
            val embedData = "$HikariAPI/api/embed/$animeId/1"
            Log.d("Phisher",embedData)
            return newMovieLoadResponse(animeTitle, url, TvType.Movie, embedData) {
                this.posterUrl = posterUrl
                this.year = releaseYear
                this.plot = animeDescription
                this.tags = genreTags
                addMalId(animeId)
            }

        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val json = app.get(data).toString()
            val embedDataList = parseEmbedData(json)

            embedDataList.forEach {
                val nameSuffix = when (it.embedType) {
                    "2" -> "SUB"
                    "3" -> "DUB"
                    else -> "MULTI"
                }
                val name = "Hikari | $nameSuffix"
                loadCustomTagExtractor(name,it.embedFrame,mainUrl,subtitleCallback, callback)
            }
            true
        } catch (e: Exception) {
            Log.e("Phisher", "Error occurred while loading links: ${e.message}")
            false
        }
    }


    private fun parseEmbedData(json: String): List<EmbedData> {
        val gson = Gson()
        val listType = object : TypeToken<List<EmbedData>>() {}.type
        return gson.fromJson(json, listType)
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun extractIframeSrc(jsonResponse: String): String? {
        val type = object : TypeToken<List<String>>() {}.type
        val iframeHtmlList: List<String> = gson.fromJson(jsonResponse, type)
        val iframeHtml = iframeHtmlList.firstOrNull() ?: return null
        return Jsoup.parse(iframeHtml).selectFirst("iframe")?.attr("src")
    }


    private val gson = Gson()
    private suspend fun extractEmbedUrls(id: Int?, serverClass: String): List<LoadUrls> {
        /*
        val typeres = app.get("$mainUrl/ajax/embedserver/$id/1").parsedSafe<TypeRes>()?.html ?: ""
        val typedoc = Jsoup.parse(typeres)
        val embedUrls = mutableListOf<LoadUrls>()

        typedoc.select(serverClass).forEach { type ->
            val embedId = type.attr("id").substringAfter("embed-")
            if (embedId.toIntOrNull() != null) {
                val href = "$mainUrl/ajax/embed/$id/1/$embedId" // Construct the URL
                embedUrls.add(LoadUrls(type = if (serverClass.contains("sub")) "sub" else "dub", href = href))
            }
        }

         */
        return emptyList()
    }

    private suspend fun extractEmbedIdsAndEpisodesToJson(id: Int?): String {
        val subUrls = extractEmbedUrls(id, ".servers-sub .server-item a")
        val dubUrls = extractEmbedUrls(id, ".servers-dub .server-item a")
        val allEmbedUrls = subUrls + dubUrls
        return gson.toJson(allEmbedUrls)
    }


    data class LoadUrls(
        val type: String,
        val href: String,
    )

    data class LoadSeriesUrls(val type: String, val href: List<String>)


    //Custom
    private suspend fun loadCustomTagExtractor(
        tag: String? = null,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        link.source,
                        "${link.name}${if (tag!!.contains("dub", true) || tag.contains("sub", true)) " $tag" else ""}",
                        url = link.url,
                        type = link.type
                    ) {
                        this.referer = link.referer
                        this.quality = when (link.type) {
                            ExtractorLinkType.M3U8 -> link.quality
                            else -> quality ?: link.quality
                        }
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }

    fun String.toJson(): String {
        return "\"$this\"" // Simple example: return the string in JSON format (e.g., "Action")
    }

}
