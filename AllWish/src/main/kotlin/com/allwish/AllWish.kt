package com.allwish

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AllWish : MainAPI() {
    override var mainUrl = AllWish.mainUrl
    override var name = AllWish.name
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "en"
    override val hasMainPage = true
    
    companion object {
        val mainUrl = "https://all-wish.me"
        var name = "AllWish"
        val xmlHeader = mapOf("X-Requested-With" to "XMLHttpRequest")
    }

    override val mainPage = mainPageOf(
        "$mainUrl/ajax/home/widget/trending?page=" to "Trending",
        "$mainUrl/ajax/home/widget/updated-sub?page=" to "Recently Updated (Sub)",
        "$mainUrl/ajax/home/widget/updated-dub?page=" to "Recently Updated (Dub)",
        "$mainUrl/ajax/home/widget/random?page=" to "Random Animes",
    )

    private fun searchResponseBuilder(res: Document): List<AnimeSearchResponse> {
        val results = mutableListOf<AnimeSearchResponse>()
        res.select("div.item").forEach { item ->
            val name = item.selectFirst("div.name > a")?.text() ?: ""
            val url = item.selectFirst("div.name > a")?.attr("href")?.substringBeforeLast("/") ?: ""
            val subCount = item.selectFirst("div.dub-sub-total > span.sub")?.text()?.toIntOrNull()
            val dubCount = item.selectFirst("div.dub-sub-total > span.dub")?.text()?.toIntOrNull()
            results += newAnimeSearchResponse(name, url) {
                this.posterUrl = item.selectFirst("a.poster img")?.attr("data-src")
                addDubStatus(dubCount != null, subCount != null, dubCount, subCount)
            }
        }
        return results
    }

    override suspend fun search(query: String,page: Int): SearchResponseList? {
        val res = app.get("$mainUrl/filter?keyword=$query&page=$page").documentLarge
        return searchResponseBuilder(res).toNewSearchResponseList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val res = app.get(request.data + page.toString(), xmlHeader).parsedSafe<APIResponse>()
        return if (res?.status == 200) {
            val searchRes = searchResponseBuilder(res.html)
            newHomePageResponse(request.name, searchRes, true)
        } else null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url).documentLarge
        val id = res.select("main > div.container").attr("data-id")

        val vrf = generateEpisodeVrf(id)
        val epRes = app.get("$mainUrl/ajax/episode/list/$id?vrf=$vrf", xmlHeader)
            .parsedSafe<APIResponse>()

        val malId = epRes?.html?.selectFirst("div.range > div > a")
            ?.attr("data-mal")?.toIntOrNull()

        val syncMetaData = app.get("https://api.ani.zip/mappings?mal_id=$malId").toString()
        val animeMetaData = parseAnimeData(syncMetaData)

        val data = res.selectFirst("div#media-info")
        val name = data?.selectFirst("h1.title")?.text()?.trim()?.replace(" (Dub)", "") ?: ""
        val posterRegex = Regex("/'(.*)'/gm")

        val (subEpisodes, dubEpisodes) = parseEpisodes(epRes, animeMetaData)
        val status = getStatus(data?.select("div:contains(Status:) > span > a")?.text()?.trim())
        val genres = data?.select("div:contains(Genre:) > span > a")?.map { it.text() }
        val content = data?.select("div.status > span.rating.mini-status")?.text()
        val year = data?.select("div:contains(Premiered:) > span > a")?.text()?.trim()?.substringAfterLast(" ")?.toIntOrNull()

        return newAnimeLoadResponse(name, url, TvType.Anime) {
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            addMalId(malId)
            this.showStatus = status
            this.tags = genres
            this.plot = data?.selectFirst("div.description > div.full > div")?.text()?.trim()
            this.contentRating = content
            this.year = year
            this.backgroundPosterUrl = animeMetaData?.images
                ?.firstOrNull { it.coverType == "Fanart" }?.url
                ?: posterRegex.find(res.selectFirst("div.media-bg")?.attr("style") ?: "")
                    ?.destructured?.toList()?.getOrNull(0)
                        ?: data?.selectFirst("div.poster img")?.attr("src").orEmpty()
            this.posterUrl = data?.selectFirst("#media-info div.poster img")?.attr("src") ?: animeMetaData?.images
                ?.firstOrNull { it.coverType.equals("Poster", ignoreCase = true) }?.url
            this.year = data?.select("div.meta > div > span")
                ?.find { it.attr("itemprop") == "dateCreated" }
                ?.text()?.toIntOrNull()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val type = data.replace("$mainUrl/", "").split("|")[0].split(",")
        val id = data.replace("$mainUrl/", "").split("|")[1]
        val res = app.get("$mainUrl/ajax/server/list?servers=$id", xmlHeader).parsedSafe<APIResponse>()

        if (res?.status == 200) {
            res.html.select("div.server-type").forEach { section ->
                val sectionType = section.attr("data-type") // sub/dub
                val isHardSub = section.selectFirst("span")?.text()?.contains("H-Sub", ignoreCase = true) ?: false

                if (type.contains(sectionType)) {
                    section.select("div.server-list > div.server").forEach { server ->
                        //val serverName = server.selectFirst("div > span")?.text() ?: ""
                        val dataId = server.attr("data-link-id")
                        val apiRes = app.get("$mainUrl/ajax/server?get=$dataId", xmlHeader)
                            .parsedSafe<APIResponseUrl>()
                        val realUrl = apiRes?.result?.url ?: ""

                        val epIdWithType = when {
                            sectionType == "dub" -> "[Dub]"
                            sectionType == "sub" && isHardSub -> "[Hard Sub]"
                            else -> "[Sub]"
                        }
                        loadExtractor(realUrl,epIdWithType,subtitleCallback,callback)
                    }
                }
            }
        }

        return true
    }

    data class APIResponse(
        @JsonProperty("status") val status: Int? = null,
        @JsonProperty("result") val result: String? = null,
        val html: Document = Jsoup.parse(result ?: "")
    )

    data class APIResponseUrl(
        @JsonProperty("status") val status: Int? = null,
        @JsonProperty("result") val result: ServerUrl? = null,
    )

    data class ServerUrl(
        @JsonProperty("url") val url: String? = null,
    )

    private fun createEpisode(
        animeMetaData: MetaAnimeData?,
        episodeNumber: Int,
        epId: String,
        isDub: Boolean,
        htmlTitle: String
    ): Episode {
        val epData = animeMetaData?.episodes?.get(episodeNumber.toString())
        val prefix = when {
            isDub -> "dub"
            epId.contains("|HSub") -> "hardsub"
            else -> "sub"
        }

        return newEpisode("$prefix|$epId") {
            this.episode = episodeNumber
            this.name = resolveTitle(epData, htmlTitle, episodeNumber)
            this.posterUrl = epData?.image ?: animeMetaData?.images?.firstOrNull()?.url ?: ""
            this.description = epData?.overview ?: "No summary available"
            this.score = Score.from10(epData?.rating)
            this.runTime = epData?.runtime
            this.addDate(epData?.airDateUtc)
        }
    }

    private fun resolveTitle(epData: MetaEpisode?, htmlTitle: String, episodeNumber: Int): String {
        val jsonTitle = epData?.title?.get("en")
            ?: epData?.title?.get("ja")
            ?: epData?.title?.get("x-jat")
            ?: htmlTitle
        return jsonTitle.ifBlank { "Episode $episodeNumber" }
    }

    private fun parseEpisodes(
        epRes: APIResponse?,
        animeMetaData: MetaAnimeData?
    ): Pair<List<Episode>, List<Episode>> {
        val subEpisodes = mutableListOf<Episode>()
        val dubEpisodes = mutableListOf<Episode>()

        if (epRes?.status == 200) {
            epRes.html.select("div.range > div > a").forEach { element ->
                val epId = element.attr("data-ids")
                val title = element.attr("title")
                val episodeNumber = element.attr("data-slug").toIntOrNull() ?: 0
                val hasSub = element.attr("data-sub") == "1"
                val hasDub = element.attr("data-dub") == "1"

                if (hasSub) {
                    subEpisodes += createEpisode(
                        animeMetaData,
                        episodeNumber,
                        epId,
                        isDub = false,
                        htmlTitle = "$title (Sub)"
                    )
                }

                if (hasDub) {
                    dubEpisodes += createEpisode(
                        animeMetaData,
                        episodeNumber,
                        epId,
                        isDub = true,
                        htmlTitle = "$title (Dub)"
                    )
                }
            }
        }

        return Pair(subEpisodes, dubEpisodes)
    }
}
