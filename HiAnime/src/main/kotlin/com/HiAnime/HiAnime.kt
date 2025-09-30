package com.HiAnime

import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

class HiAnime : MainAPI() {
    override var mainUrl = HiAnimeProviderPlugin.currentHiAnimeServer
    override var name = "HiAnime"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(this.select("a").attr("href"))
        val title = this.select("h3.film-name").text()
        val subCount =
                this.selectFirst(".film-poster > .tick.ltr > .tick-sub")?.text()?.toIntOrNull()
        val dubCount =
                this.selectFirst(".film-poster > .tick.ltr > .tick-dub")?.text()?.toIntOrNull()

        val posterUrl = fixUrl(this.select("img").attr("data-src"))
        val type = getType(this.selectFirst("div.fd-infor > span.fdi-item")?.text() ?: "")

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubCount != null, subCount != null, dubCount, subCount)
        }
    }

    private fun Element.getActorData(): ActorData? {
        var actor: Actor? = null
        var role: ActorRole? = null
        var voiceActor: Actor? = null
        val elements = this.select(".per-info")
        elements.forEachIndexed { index, actorInfo ->
            val name = actorInfo.selectFirst(".pi-name")?.text() ?: return null
            val image = actorInfo.selectFirst("a > img")?.attr("data-src") ?: return null
            when (index) {
                0 -> {
                    actor = Actor(name, image)
                    val castType = actorInfo.selectFirst(".pi-cast")?.text() ?: "Main"
                    role = ActorRole.valueOf(castType)
                }
                1 -> voiceActor = Actor(name, image)
                else -> {}
            }
        }
        return ActorData(actor ?: return null, role, voiceActor = voiceActor)
    }

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage =
            mainPageOf(
                    "$mainUrl/recently-updated?page=" to "Latest Episodes",
                    "$mainUrl/top-airing?page=" to "Top Airing",
                    "$mainUrl/filter?status=2&language=1&sort=recently_updated&page=" to "Recently Updated (SUB)",
                    "$mainUrl/filter?status=2&language=2&sort=recently_updated&page=" to "Recently Updated (DUB)",
                    "$mainUrl/recently-added?page=" to "New On HiAnime",
                    "$mainUrl/most-popular?page=" to "Most Popular",
                    "$mainUrl/most-favorite?page=" to "Most Favorite",
                    "$mainUrl/completed?page=" to "Latest Completed",
            )

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search?keyword=$query"
        val res = app.get(link).document

        return res.select("div.flw-item").map { it.toSearchResult() }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("${request.data}$page").document
        val items = res.select("div.flw-item").map { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    @SuppressLint("DefaultLocale")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val syncData = tryParseJson<ZoroSyncData>(document.selectFirst("#syncData")?.data())
        val syncMetaData = app.get("https://api.ani.zip/mappings?mal_id=${syncData?.malId}").toString()
        val animeMetaData = parseAnimeData(syncMetaData)
        val title = document.selectFirst(".anisc-detail > .film-name")?.text().toString()
        val poster = document.select("#ani_detail div.film-poster img").attr("src")
        val backgroundposter = animeMetaData?.images?.find { it.coverType == "Fanart" }?.url
            ?: document.selectFirst(".anisc-poster img")?.attr("src")
        val animeId = URI(url).path.split("-").last()
        val subCount = document.selectFirst(".anisc-detail .tick-sub")?.text()?.toIntOrNull()
        val dubCount = document.selectFirst(".anisc-detail .tick-dub")?.text()?.toIntOrNull()

        val dubEpisodes = emptyList<Episode>().toMutableList()
        val subEpisodes = emptyList<Episode>().toMutableList()
        val responseBody = app.get("$mainUrl/ajax/v2/episode/list/$animeId").body.string()
        val epRes = responseBody.stringParse<Response>()?.getDocument()
        val malId = syncData?.malId ?: "0"
        val anilistId = syncData?.aniListId ?: "0"
        epRes?.select(".ss-list > a[href].ssl-item.ep-item")?.forEachIndexed { index, ep ->
            subCount?.let {
                if (index < it) {
                    val href = ep.attr("href").removePrefix("/")
                    val episodeData = "sub|$malId|$href"
                    subEpisodes += newEpisode(episodeData) {
                        name = ep.attr("title")
                        episode = ep.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                        val episodeKey = episode?.toString()
                        this.score = Score.from10(animeMetaData?.episodes?.get(episodeKey)?.rating)
                        this.posterUrl = animeMetaData?.episodes?.get(episodeKey)?.image ?: return@newEpisode
                        this.description = animeMetaData.episodes[episodeKey]?.overview ?: "No summary available"
                    }
                }
            }
            dubCount?.let {
                if (index < it) {
                    dubEpisodes += newEpisode("dub|" + "$malId|" + ep.attr("href").removePrefix("/")) {
                        name = ep.attr("title")
                        episode = ep.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                        val episodeKey = episode?.toString()
                        this.score = Score.from10(animeMetaData?.episodes?.get(episodeKey)?.rating)
                        this.posterUrl = animeMetaData?.episodes?.get(episodeKey)?.image ?: return@newEpisode
                        this.description = animeMetaData.episodes[episodeKey]?.overview ?: "No summary available"
                    }
                }
            }
        }

        val actors =
                document.select("div.block-actors-content div.bac-item").mapNotNull {
                    it.getActorData()
                }

        val recommendations =
                document.select("div.block_area_category div.flw-item").map { it.toSearchResult() }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            backgroundPosterUrl = backgroundposter
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            this.recommendations = recommendations
            this.actors = actors
            addMalId(malId.toIntOrNull())
            addAniListId(anilistId.toIntOrNull())
            // adding info
            document.select(".anisc-info > .item").forEach { info ->
                val infoType = info.select("span.item-head").text().removeSuffix(":")
                when (infoType) {
                    "Overview" -> plot = info.selectFirst(".text")?.text()
                    "Japanese" -> japName = info.selectFirst(".name")?.text()
                    "Premiered" -> year = info.selectFirst(".name")?.text()?.substringAfter(" ")?.toIntOrNull()
                    "Duration" -> duration = getDurationFromString(info.selectFirst(".name")?.text())
                    "Status" -> showStatus = getStatus(info.selectFirst(".name")?.text().toString())
                    "Genres" -> tags = info.select("a").map { it.text() }
                    "MAL Score" -> score = Score.from10(info.selectFirst(".name")?.text())
                    else -> {}
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val dubType = data.removePrefix("$mainUrl/").substringBefore("|").ifEmpty { "raw" }
            val hrefPart = data.substringAfterLast("|")
            val epId = hrefPart.substringAfter("ep=")
            val doc = app.get("$mainUrl/ajax/v2/episode/servers?episodeId=$epId")
                .parsed<Response>()
                .getDocument()
            val servers = doc.select(".server-item[data-type=$dubType][data-id], .server-item[data-type=raw][data-id]")
                .mapNotNull {
                    val id = it.attr("data-id")
                    val label = it.selectFirst("a.btn")?.text()?.trim()
                    if (id.isNotEmpty() && label != null) {
                        id to label
                    } else {
                        null
                    }
                }.distinctBy { it.first }
            servers.forEach { (id, label) ->
                val sourceurl = app.get("${mainUrl}/ajax/v2/episode/sources?id=$id").parsedSafe<EpisodeServers>()?.link
                if (sourceurl != null) {
                    loadCustomExtractor(
                        "HiAnime [$label]",
                        sourceurl,
                        "",
                        subtitleCallback,
                        callback,
                    )
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("HiAnime", "Critical error in loadLinks: ${e.localizedMessage}")
            return false
        }
    }



    data class Response(
        @SerializedName("status") val status: Boolean,
        @SerializedName("html") val html: String
    ) {
        fun getDocument(): Document {
            return Jsoup.parse(html)
        }
    }

    private data class ZoroSyncData(
            @JsonProperty("mal_id") val malId: String?,
            @JsonProperty("anilist_id") val aniListId: String?,
    )

    // HiAnime Response

    data class HiAnimeResponse(
        val headers: HiAnimeHeaders,
        val intro: HiAnimeIntro,
        val outro: HiAnimeOutro,
        val sources: List<HiAnimeSource>,
        val subtitles: List<HiAnimeSubtitle>,
    )

    data class HiAnimeHeaders(
        @JsonProperty("Referer")
        val referer: String,
    )

    data class HiAnimeIntro(
        val start: Long,
        val end: Long,
    )

    data class HiAnimeOutro(
        val start: Long,
        val end: Long,
    )

    data class HiAnimeSource(
        val url: String,
        val isM3U8: Boolean,
        val type: String,
    )

    data class HiAnimeSubtitle(
        val url: String,
        val lang: String,
    )


    data class HiAnimeAPI(
        val sources: List<Source>,
        val tracks: List<Track>,
    )

    data class Source(
        val file: String,
        val type: String,
    )

    data class Track(
        val file: String,
        val label: String,
    )

    data class EpisodeServers(
        val type: String,
        val link: String,
        val server: Long,
        val sources: List<Any?>,
        val tracks: List<Any?>,
        val htmlGuide: String,
    )




    // Metadata
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaImage(
        @JsonProperty("coverType") val coverType: String?,
        @JsonProperty("url") val url: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaEpisode(
        @JsonProperty("episode") val episode: String?,
        @JsonProperty("airDate") val airDate: String?,  // Keeping only one field
        @JsonProperty("runtime") val runtime: Int?,     // Keeping only one field
        @JsonProperty("image") val image: String?,
        @JsonProperty("title") val title: Map<String, String>?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("rating") val rating: String?,
        @JsonProperty("finaleType") val finaleType: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaAnimeData(
        @JsonProperty("titles") val titles: Map<String, String>?,
        @JsonProperty("images") val images: List<MetaImage>?,
        @JsonProperty("episodes") val episodes: Map<String, MetaEpisode>?
    )

    private fun parseAnimeData(jsonString: String): MetaAnimeData? {
        return try {
            val objectMapper = ObjectMapper()
            objectMapper.readValue(jsonString, MetaAnimeData::class.java)
        } catch (_: Exception) {
            null // Return null for invalid JSON instead of crashing
        }
    }

    private inline fun <reified T> String.stringParse(): T? {
        return try {
            Gson().fromJson(this, T::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null // Return null if JSON parsing fails
        }
    }

    private suspend fun loadCustomExtractor(
        name: String? = null,
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
                        name ?: link.source,
                        name ?: link.name,
                        link.url,
                    ) {
                        this.quality = when {
                            link.name == "VidSrc" -> Qualities.P1080.value
                            link.type == ExtractorLinkType.M3U8 -> link.quality
                            else -> quality ?: link.quality
                        }
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }

}
