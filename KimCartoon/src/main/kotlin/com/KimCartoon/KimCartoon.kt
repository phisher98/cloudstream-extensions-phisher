package com.KimCartoon

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class KimCartoon : MainAPI() {
    override var mainUrl = "https://kimcartoon.si"
    override var name = "KimCartoon"
    override val supportedTypes = setOf(TvType.Cartoon)
    override var lang = "en"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "Status/Ongoing/MostPopular" to "MostPopular Cartoons",
        "Status/Ongoing/LatestUpdate" to "Latest Updated Cartoons",
        "Status/Ongoing" to "Ongoing Cartoons",
        "Status/Ongoing/Newest" to "Newest Cartoons",
    )


    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("a h2")?.text() ?: return null
        val href = fixUrlNull(selectFirst("div a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("a img")?.attr("src"))

        return newMovieSearchResponse(title, LoadUrl(href, posterUrl).toJson()) {
            this.posterUrl = posterUrl
        }
    }



    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val items = document.select("div.list-cartoon div.item").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/Search/?s=$query&page=$i").document

            val results = document.select("div.list-cartoon div.item").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }


    override suspend fun load(url: String): LoadResponse? {
        val data = tryParseJson<LoadUrl>(url) ?: return null
        val document = app.get(data.url).document
        val title       = document.selectFirst("div.barContent.full h1 a")?.text()?.trim() ?: "No Title"
        val poster = fixUrl(data.posterUrl!!)
        val description = document.select("div.summary p").text().trim()
        val genre = document.select("div.barContent.full p a").map { it.text() }
            val episodes=document.select("div.listing div.full.item_ep").map { info->
                val href = info.select("h3 a").attr("href")
                val episode = info.select("h3 a").text()
                newEpisode(href)
                {
                    this.name=episode
                }
            }
            return newTvSeriesLoadResponse(title, data.url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
                this.tags=genre
            }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id=data.substringAfter("id=")
        app.get(data).document.select("#info_player #selectServer option").map { s ->
            val server=s.attr("sv")
            val href=app.post("${mainUrl}/ajax/anime/load_episodes_v2?s=$server", data = mapOf("episode_id" to id)).document.selectFirst("iframe")?.attr("src")?.replace("\\\"","")  ?:""
            val response= app.get(href, referer = mainUrl).toString()
            val m3u8 =Regex("file\":\"(.*?m3u8.*?)\"").find(response)?.groupValues?.getOrNull(1)
            if (m3u8!=null)
            {
                callback.invoke(
                    newExtractorLink(
                        "$name ${server.uppercase()}",
                        "$name ${server.uppercase()}",
                        url = m3u8,
                        INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
        return true
    }
}

data class LoadUrl(
    val url: String,
    val posterUrl: String?
)
