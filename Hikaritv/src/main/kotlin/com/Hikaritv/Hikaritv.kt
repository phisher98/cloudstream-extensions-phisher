package com.hikaritv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.nodes.Element
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
        "api/anime/?sort=created_at&order=asc&ani_stats=1&ani_release=$currentYear&page=" to "Animes",
        "api/anime/?sort=created_at&order=asc&ani_type=2&page=" to "Anime Movies",
        "api/anime/?sort=created_at&order=asc&ani_stats=2&page=" to "Completed Animes",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home = mutableListOf<SearchResponse>()

        val res1 = app.get("$HikariAPI/${request.data}$page").parsedSafe<HomePage>()
        home.addAll(res1?.results?.map { it.toSearchResult() } ?: emptyList())

        val res2 = app.get("$HikariAPI/${request.data}${page + 1}").parsedSafe<HomePage>()
        home.addAll(res2?.results?.map { it.toSearchResult() } ?: emptyList())

        return newHomePageResponse(request.name, home)
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

    private fun Result.toSearchResult(): SearchResponse {
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
        val res1 = app.get("$HikariAPI/api/anime/?sort=created_at&order=asc&search=$query").parsedSafe<HomePage>()
        return res1?.results?.mapNotNull { it.toSearchResult() } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val animeId = url.substringAfter("/info/").substringBefore("/").toIntOrNull()
        val syncData = app.get("https://api.ani.zip/mappings?mal_id=$animeId").toString()
        val animeData = parseAnimeData(syncData)
        val response = try {
            val responseText = app.get("$HikariAPI/api/anime/uid/$animeId/").text
            Gson().fromJson(responseText, Load::class.java)
        } catch (e: JsonSyntaxException) {
            null // or handle the error appropriately
        }
        val animeTitle = response?.ani_name ?: animeData.titles?.get("en") ?: "Unknown"
        val posterUrl = animeData.images?.firstOrNull { it.coverType == "Fanart" }?.url
            ?: response?.ani_poster
        val aniGenre = response?.ani_genre ?: ""
        val genreTags = aniGenre.split(",")
        val releaseYear =response?.ani_release
        val animeDescription = response?.ani_synopsis
        val tvType = if (response?.ani_type == 1) TvType.TvSeries else TvType.Movie
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
        return runCatching {
            val json = app.get(data).text // Avoid unnecessary `.toString()`
            val embedDataList = parseEmbedData(json)

            embedDataList.forEach { embed ->
                val nameSuffix = when (embed.embedType) {
                    "2" -> "SUB"
                    "3" -> "DUB"
                    else -> "MULTI"
                }
                val name = "Hikari | $nameSuffix"
                loadCustomTagExtractor(
                    tag = name,
                    url = embed.embedFrame,
                    referer = mainUrl,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            }

            true
        }.getOrElse { e ->
            Log.e("Hikari", "Failed to load links: ${e.localizedMessage}")
            false
        }
    }



    private fun parseEmbedData(json: String): List<EmbedData> {
        val gson = Gson()
        val listType = object : TypeToken<List<EmbedData>>() {}.type
        return gson.fromJson(json, listType)
    }

    /*
    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }
     */


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
}
