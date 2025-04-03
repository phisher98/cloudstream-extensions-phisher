package com.HiAnime

import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.phisher98.BuildConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import kotlin.math.roundToInt

class HiAnime : MainAPI() {
    override var mainUrl = "https://hianime.bz"
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
        val poster = animeMetaData?.images?.find { it.coverType == "Fanart" }?.url
            ?: document.selectFirst(".anisc-poster img")?.attr("src")
        val animeId = URI(url).path.split("-").last()
        Log.d("Phisher",poster.toString())
        val subCount = document.selectFirst(".anisc-detail .tick-sub")?.text()?.toIntOrNull()
        val dubCount = document.selectFirst(".anisc-detail .tick-dub")?.text()?.toIntOrNull()

        val dubEpisodes = emptyList<Episode>().toMutableList()
        val subEpisodes = emptyList<Episode>().toMutableList()
        val responseBody = app.get("$mainUrl/ajax/v2/episode/list/$animeId").body.string()
        val epRes = responseBody.stringParse<Response>()?.getDocument()

        epRes?.select(".ss-list > a[href].ssl-item.ep-item")?.forEachIndexed { index, ep ->
            subCount?.let {
                if (index < it) {
                    subEpisodes +=
                            newEpisode("sub|" + ep.attr("href")) {
                                name = ep.attr("title")
                                episode = ep.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                                this.rating = animeMetaData?.episodes?.get(episode?.toString())?.rating
                                    ?.toDoubleOrNull()
                                    ?.times(10)
                                    ?.roundToInt()
                                    ?: 0
                                this.posterUrl = animeMetaData?.episodes?.get(episode?.toString())?.image
                                    ?: return@newEpisode
                                this.description = animeMetaData.episodes[episode?.toString()]?.overview
                                    ?: "No summary available"
                            }
                }
            }
            dubCount?.let {
                if (index < it) {
                    dubEpisodes +=
                            newEpisode("dub|" + ep.attr("href")) {
                                name = ep.attr("title")
                                episode = ep.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                                this.rating = animeMetaData?.episodes?.get(episode?.toString())?.rating
                                    ?.toDoubleOrNull()
                                    ?.times(10)
                                    ?.roundToInt()
                                    ?: 0
                                this.posterUrl = animeMetaData?.episodes?.get(episode?.toString())?.image
                                    ?: return@newEpisode
                                this.description = animeMetaData.episodes[episode?.toString()]?.overview
                                    ?: "No summary available"
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
            backgroundPosterUrl = poster
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            this.recommendations = recommendations
            this.actors = actors

            // adding info
            document.select(".anisc-info > .item").forEach { info ->
                val infoType = info.select("span.item-head").text().removeSuffix(":")
                when (infoType) {
                    "Overview" -> plot = info.selectFirst(".text")?.text()
                    "Japanese" -> japName = info.selectFirst(".name")?.text()
                    "Premiered" ->
                            year =
                                    info.selectFirst(".name")
                                            ?.text()
                                            ?.substringAfter(" ")
                                            ?.toIntOrNull()
                    "Duration" ->
                            duration = getDurationFromString(info.selectFirst(".name")?.text())
                    "Status" -> showStatus = getStatus(info.selectFirst(".name")?.text().toString())
                    "Genres" -> tags = info.select("a").map { it.text() }
                    "MAL Score" -> rating = info.selectFirst(".name")?.text().toRatingInt()
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
        val dubType = data.replace("$mainUrl/", "").split("|").firstOrNull() ?: "raw"
        val epId = data.split("|").last().split("=").last()
        val servers: List<String> =
                app.get("$mainUrl/ajax/v2/episode/servers?episodeId=$epId")
                        .parsed<Response>()
                        .getDocument()
                        .select(".server-item[data-type=raw][data-id],.server-item[data-type=$dubType][data-id]")
                        .map { it.attr("data-id") }
        // val extractorData = "https://ws1.rapid-cloud.ru/socket.io/?EIO=4&transport=polling"
        val selectedTypes = app.get("$mainUrl/ajax/v2/episode/servers?episodeId=$epId")
            .parsed<Response>()
            .getDocument()
            .select(".server-item[data-type]")
            .map { it.attr("data-type") }

// Set `dubType` to "raw" if a raw type is present in the selected servers
        val type = if (selectedTypes.contains("raw")) "raw" else dubType
        // Prevent duplicates
        val gson = Gson()

        servers.distinct().amap {
            val animeEpisodeId = data.substringAfterLast("/").substringBeforeLast("?")
            val serverlist = listOf("vidstreaming", "vidcloud") //Vidstream=HD-1 & vidcloud=HD-2
            for (server in serverlist) {
                Log.d("Phisher","$data $animeEpisodeId $epId")
                //val api = "${BuildConfig.HianimeAPI}?animeEpisodeId=$animeEpisodeId?ep=$epId&server=$server&category=$type"
                //"https://consumet.8man.me/anime/zoro/watch?episodeId=sakamoto-days-19431$episode$132565$dub&server=vidstreaming"
                val api= "${BuildConfig.HianimeAPI}episodeId=$animeEpisodeId${'$'}episode$$epId$$type&server=$server"
                try {
                    val responseText = app.get(api, referer = api).text

                    val response = try {
                        gson.fromJson(responseText, HiAnimeResponse::class.java)
                    } catch (e: JsonSyntaxException) {
                        null
                    }

                    if (response == null) {
                        Log.e("Error:", "Failed to parse Root response: Data is null")
                        continue
                    }

                    response.sources.firstOrNull { it.isM3U8 }?.let { source ->
                        val m3u8headers = mapOf(
                            "Referer" to "https://megacloud.club/",
                            "Origin" to "https://megacloud.club/"
                        )
                        val serverName = if (server.equals("vidstreaming", ignoreCase = true)) "HD-1" else "HD-2"

                        M3u8Helper.generateM3u8(
                            "⌜ HiAnime ⌟ | ${serverName.uppercase()} | ${type.uppercase()}",
                            source.url,
                            mainUrl,
                            headers = m3u8headers
                        ).forEach(callback)
                    }

                    response.subtitles.forEach { subtitle ->
                        subtitleCallback.invoke(
                            SubtitleFile(
                                lang = subtitle.lang,
                                url = subtitle.url
                            )
                        )
                    }
                }
                catch (e: Exception) {
                    Log.e("Error:", "Error fetching or parsing response: ${e.message}")
                }
            }
        }

        return true
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
        val intro: HiAnimeIntro,
        val outro: HiAnimeOutro,
        val sources: List<HiAnimeSource>,
        val subtitles: List<HiAnimeSubtitle>,
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
        } catch (e: Exception) {
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

}
