package com.AnimeKai

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.apmapIndexed
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


class AnimeKai : MainAPI() {
    override var mainUrl = "https://animekai.to"
    override var name = "Animekai"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    override val supportedSyncNames = setOf(
        SyncIdName.MyAnimeList,
        SyncIdName.Anilist
    )

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(this.select("a.poster").attr("href"))
        val title = this.select("a.title").text()
        val subCount = this.selectFirst("div.info span.sub")?.text()?.toIntOrNull()
        val dubCount = this.selectFirst("div.info span.dub")?.text()?.toIntOrNull()
        val posterUrl = fixUrl(this.select("a.poster img").attr("data-src"))
        val type = getType(this.selectFirst("div.fd-infor > span.fdi-item")?.text() ?: "")

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubCount != null, subCount != null, dubCount, subCount)
        }
    }

    private fun Element.toRecommendResult(): SearchResponse {
        val href = fixUrl(this.attr("href"))
        val title = this.select("div.title").text()
        val posterUrl = fixUrl(this.attr("style").substringAfter("('").substringBefore("')"))
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }


    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Releasing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage =
            mainPageOf(
                    "$mainUrl/browser?keyword=&status%5B%5D=releasing&sort=updated_date" to "Latest Episode",
                    "$mainUrl/browser?keyword=&status[]=releasing&sort=trending" to "Trending",
                    "$mainUrl/browser?keyword=&sort=released_date" to "New Releases",
                    "$mainUrl/browser?keyword=&status%5B%5D=completed&sort=mal_score" to "Completed"
            )

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/browser?keyword=$query"
        val res = app.get(link).document
        return res.select("div.aitem-wrapper div.aitem").map { it.toSearchResult() }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("${request.data}&page=$page").document
        val items = res.select("div.aitem-wrapper div.aitem").map { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val malid=document.select("div.watch-section").attr("data-mal-id")
        val aniid=document.select("div.watch-section").attr("data-al-id")

        val title = document.selectFirst("div.title")?.text().toString()
        val jptitle=document.selectFirst("div.title")?.attr("data-jp").toString()
        val poster = document.selectFirst("div.watch-section-bg")?.attr("style")?.substringAfter("(")?.substringBefore(")")
        val animeId = document.selectFirst("div.rate-box")?.attr("data-id")

        val subCount = document.selectFirst("#main-entity div.info span.sub")?.text()?.toIntOrNull()
        val dubCount = document.selectFirst("#main-entity div.info span.dub")?.text()?.toIntOrNull()
        val dubEpisodes = emptyList<Episode>().toMutableList()
        val subEpisodes = emptyList<Episode>().toMutableList()
        val decoder=AnimekaiDecoder()
        val epRes =
                app.get("$mainUrl/ajax/episodes/list?ani_id=$animeId&_=${decoder.generateToken(animeId ?:"")}")
                        .parsedSafe<Response>()
                        ?.getDocument()
        epRes?.select("div.eplist a")?.forEachIndexed { index, ep ->
            subCount?.let {
                if (index < it) {
                    subEpisodes +=
                            newEpisode("sub|" + ep.attr("token")) {
                                name = ep.selectFirst("span")?.text()
                                episode = ep.attr("num").toIntOrNull()
                            }
                }
            }
            dubCount?.let {
                if (index < it) {
                    dubEpisodes +=
                            newEpisode("dub|" + ep.attr("token")) {
                                name = ep.selectFirst("span")?.text()
                                episode = ep.attr("num").toIntOrNull()
                            }
                }
            }
        }
        val recommendations = document.select("div.aitem-col a").map { it.toRecommendResult() }
        val genres = document.select("div.detail a")
            .asSequence()
            .filter { it.attr("href").contains("/genres/") }
            .map { it.text() }
            .toList()
        val status = document.select("div.detail div:contains(Status)")
            .select("span")
            .text()
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            this.recommendations = recommendations
            this.japName = jptitle
            this.tags=genres
            this.showStatus = getStatus(status)
            addMalId(malid.toIntOrNull())
            addAniListId(aniid.toIntOrNull())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val decoder = AnimekaiDecoder()
        val token = data.split("|").last().split("=").last()
        val dubType = data.replace("$mainUrl/", "").split("|").firstOrNull() ?: "raw"
        val types = if ("sub" in data) listOf(dubType, "softsub") else listOf(dubType)

        val servers = types.flatMap { type ->
            app.get("$mainUrl/ajax/links/list?token=$token&_=${decoder.generateToken(token)}")
                .parsed<Response>()
                .getDocument()
                .select("div.server-items[data-id=$type] span[data-lid]")
                .map { lid -> type to lid.attr("data-lid") }
        }

        servers.distinct().apmapIndexed { index, (type, lid) ->
            val result = app.get("$mainUrl/ajax/links/view?id=$lid&_=${decoder.generateToken(lid)}")
                .parsed<Response>().result
            val iframe = extractVideoUrlFromJson(decoder.decodeIframeData(result))
            val nameSuffix = when (type) {
                "softsub" -> "[Soft Sub]"
                else -> ""
            }
            val name = "AnimeKai HD-${index + 1} ${nameSuffix.trim().capitalize()}".trim()
            loadExtractor(iframe, name, subtitleCallback, callback)
        }
        return true
    }

    data class Response(
            @JsonProperty("status") val status: Boolean,
            @JsonProperty("result") val result: String
    ) {
        fun getDocument(): Document {
            return Jsoup.parse(result)
        }
    }

    data class VideoData(
        val url: String,
        val skip: Skip,
    )

    data class Skip(
        val intro: List<Long>,
        val outro: List<Long>,
    )

    private fun extractVideoUrlFromJson(jsonData: String): String {
        val gson = com.google.gson.Gson()
        val videoData = gson.fromJson(jsonData, VideoData::class.java)
        return videoData.url
    }

    data class M3U8(
        val sources: List<Source>,
        val tracks: List<Track>,
        val download: String,
    )
    data class Source(
        val file: String,
    )

    data class Track(
        val file: String,
        val label: String?,
        val kind: String,
        val default: Boolean?,
    )
}
