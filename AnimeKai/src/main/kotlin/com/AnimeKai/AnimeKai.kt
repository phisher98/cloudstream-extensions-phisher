package com.AnimeKai

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addKitsuId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.phisher98.BuildConfig
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.random.Random


class AnimeKai : MainAPI() {
    override var mainUrl = AnimeKaiPlugin.currentAnimeKaiServer
    override var name = "Animekai"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

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

        suspend fun decode(text: String?): String {
            return try {
                val res = app.get("${BuildConfig.KAIENC}?text=$text").text
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                app.get("${BuildConfig.KAISVA}/?f=e&d=$text").text
            }
        }

        private val JSON = "application/json; charset=utf-8".toMediaType()

        suspend fun decodeReverse(text: String): String {
            val jsonBody = """{"text":"$text"}""".toRequestBody(JSON)

            return try {
                val res = app.post(
                    BuildConfig.KAIDEC,
                    requestBody = jsonBody
                ).text
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                app.get("${BuildConfig.KAISVA}/?f=d&d=$text").text
            }
        }



        fun getType(t: String): TvType {
            val lower = t.lowercase()
            return when {
                "ova" in lower || "special" in lower -> TvType.OVA
                "movie" in lower -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Releasing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed // optionally log unexpected status
            }
        }
    }


    override val mainPage = mainPageOf(
            "$mainUrl/browser?keyword=&status[]=releasing&sort=trending" to "Trending",
            "$mainUrl/browser?keyword=&status[]=releasing&sort=updated_date" to "Latest Episode",
            "$mainUrl/browser?keyword=&type[]=tv&status[]=releasing&sort=added_date&language[]=sub&language[]=softsub" to "Recently SUB",
            "$mainUrl/browser?keyword=&type[]=tv&status[]=releasing&sort=added_date&language[]=dub" to "Recently DUB",
    )

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query,1).items


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val link = "$mainUrl/browser?keyword=$query&page=$page"
        val res = app.get(link).documentLarge
        return res.select("div.aitem-wrapper div.aitem").map { it.toSearchResult() }.toNewSearchResponseList()
    }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        delay(Random.nextLong(1000, 2000))

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "$mainUrl/",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Cookie" to "usertype=guest; session=Mv2Y6x1b2I8SEw3fj0eNDfQYJM3CTpH9KjJc3ACK; cf_clearance=z9kEgtOSx3us4aluy5_5MfYEL6Ei8RJ3jCbcFTD2R1E-1745122952-1.2.1.1-UYjW2QUhPKUmojZE3XUE.gqHf3g5O6lvdl0qDCNPb5IjjavrpZIOpbE64osKxLbcblCAWynfNLv6bKSO75WzURG.FqDtfcu_si3MrCHECNtbMJC.k9cuhqDRcsz8hHPgpQE2fY8rR1z5Z4HfGmCw2MWMT6GelsZW_RQrTMHUYtIqjaEiAtxfcg.O4v_RGPwio_2J2V3rP16JbWO8wRh_dObNvWSMwMW.t44PhOZml_xWuh7DH.EIxLu3AzI91wggYU9rw6JJkaWY.UBbvWB0ThZRPTAJZy_9wlx2QFyh80AXU2c5BPHwEZPQhTQHBGQZZ0BGZkzoAB8pYI3f3eEEpBUW9fEbEJ9uoDKs7WOow8g"
        )

        val res = app.get("${request.data}&page=$page", headers).documentLarge
        val items = res.select("div.aitem-wrapper div.aitem").map { it.toSearchResult() }

        return newHomePageResponse(request.name, items)
    }



    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val malid = document.select("div.watch-section").attr("data-mal-id")
        val aniid = document.select("div.watch-section").attr("data-al-id")
        val poster = document.select("div.poster img").attr("src")
        val syncMetaData = app.get("https://api.ani.zip/mappings?anilist_id=$aniid").toString()
        val animeMetaData = parseAnimeData(syncMetaData)
        val kitsuid = animeMetaData?.mappings?.kitsuid

        val data = anilistAPICall(
            "query (\$id: Int = ${aniid}) { Media(id: \$id, type: ANIME) { id title { romaji english } startDate { year } genres description averageScore status bannerImage coverImage { extraLarge large medium } bannerImage episodes format nextAiringEpisode { episode } airingSchedule { nodes { episode } } recommendations { edges { node { id mediaRecommendation { id title { romaji english } coverImage { extraLarge large medium } } } } } } }"
        ).data.media ?: throw Exception("Unable to fetch media details")

        val backgroundposter = data.bannerImage ?: animeMetaData?.images?.find { it.coverType == "Fanart" }?.url ?: data.coverImage.extraLarge
        ?: document.selectFirst(".anisc-poster img")?.attr("src")
        val title = document.selectFirst("h1.title")?.text().orEmpty()
        val jptitle = document.selectFirst("h1.title")?.attr("data-jp").orEmpty()
        val plot= document.selectFirst("div.desc")?.text()

        val animeId = document.selectFirst("div.rate-box")?.attr("data-id")
        val subCount = document.selectFirst("#main-entity div.info span.sub")?.text()?.toIntOrNull()
        val dubCount = document.selectFirst("#main-entity div.info span.dub")?.text()?.toIntOrNull()

        val subEpisodes = mutableListOf<Episode>()
        val dubEpisodes = mutableListOf<Episode>()

        val decoded = decode(animeId)

        val epRes = app.get("$mainUrl/ajax/episodes/list?ani_id=$animeId&_=$decoded")
            .parsedSafe<Response>()?.getDocument()

        epRes?.select("div.eplist a")?.forEachIndexed { index, ep ->
            // --- Helper to get best episode title ---
            fun resolveTitle(ep: Element, episodeKey: String): String {
                val titleMap = animeMetaData?.episodes?.get(episodeKey)?.title
                val jsonTitle = titleMap?.get("en")
                    ?: titleMap?.get("ja")
                    ?: titleMap?.get("x-jat")
                    ?: animeMetaData?.titles?.get("en")
                    ?: animeMetaData?.titles?.get("ja")
                    ?: animeMetaData?.titles?.get("x-jat")
                    ?: ""
                val attrTitle = ep.selectFirst("span")?.text() ?: ep.attr("title")
                return jsonTitle.ifBlank { attrTitle }
            }

            val episodeNum = index + 1
            fun createEpisode(source: String, ep: Element, episodeNum: Int): Episode {
                val episodeKey = episodeNum.toString()
                val metaEp = animeMetaData?.episodes?.get(episodeKey)
                return newEpisode("$source|${ep.attr("token")}") {
                    this.name = resolveTitle(ep, episodeKey)
                    this.episode = episodeNum
                    this.score = Score.from10(metaEp?.rating)
                    this.posterUrl = metaEp?.image ?: animeMetaData?.images?.firstOrNull()?.url ?: ""
                    this.description = metaEp?.overview ?: "No summary available"
                    this.addDate(metaEp?.airdate)
                    this.runTime = metaEp?.runtime
                }
            }
            // Sub episodes
            subCount?.let { subTotal ->
                if (index < subTotal) {
                    subEpisodes += createEpisode("sub", ep, episodeNum)
                }
            }

            // Dub episodes
            dubCount?.let { dubTotal ->
                if (index < dubTotal) {
                    val dubEpisodeNum = ep.attr("num").toIntOrNull() ?: episodeNum
                    dubEpisodes += createEpisode("dub", ep, dubEpisodeNum)
                }
            }
        }

        val recommendations = document.select("div.aitem-col a").map { it.toRecommendResult() }
        val genres = document.select("div.detail a")
            .asSequence()
            .filter { it.attr("href").contains("/genres/") }
            .map { it.text() }
            .toList()
        val status = document.select("div:containsOwn(Status) span")
            .firstOrNull()
            ?.text()?.trim()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            japName = jptitle
            posterUrl = poster
            backgroundPosterUrl = backgroundposter
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            this.recommendations = recommendations
            this.tags = genres
            this.plot = plot
            showStatus = status?.let { getStatus(it) }
            addMalId(malid.toIntOrNull())
            addAniListId(aniid.toIntOrNull())
            try { addKitsuId(kitsuid) } catch(_:Throwable){}
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val token = data.split("|").last().split("=").last()
        val dubType = data.replace("$mainUrl/", "").split("|").firstOrNull() ?: "raw"
        val types = if ("sub" in data) listOf(dubType, "softsub") else listOf(dubType)
        val decodetoken =decode(token)
        val document =
            app.get("$mainUrl/ajax/links/list?token=$token&_=$decodetoken")
                .parsed<Response>()
                .getDocument()

        val servers = types.flatMap { type ->
            document.select("div.server-items[data-id=$type] span.server[data-lid]")
                .map { server ->
                    val lid = server.attr("data-lid")
                    val serverName = server.text()
                    Triple(type, lid, serverName)
                }
        }.distinct()

        servers.amap { (type, lid, serverName) ->
            val decodelid = decode(lid)

            val result = app.get("$mainUrl/ajax/links/view?id=$lid&_=$decodelid")
                .parsed<Response>().result
            val decodeiframe= decodeReverse(result)

            val iframe = extractVideoUrlFromJson(decodeiframe)
            val nameSuffix = if (type == "softsub") " [Soft Sub]" else ""
            val name = "⌜ AnimeKai ⌟  |  $serverName  | $nameSuffix"
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

    private fun extractVideoUrlFromJson(jsonData: String): String {
        val jsonObject = JSONObject(jsonData)
        return jsonObject.getString("url")
    }
}
