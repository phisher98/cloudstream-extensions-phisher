package com.kissasian

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

open class Kissasianv2 : MainAPI() {
    override var mainUrl = "https://kissasiantv.to"
    override var name = "Kissasian V2"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    companion object {
        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "drama-list/ongoing.html?page=" to "Drama Ongoing",
        "drama-list/completed.html?page=" to "Drama Completed",
        "genre/variety/?page=" to "Variety Show",
        "genre/romance/?page=" to "Romance",
        "genre/action/?page=" to "Action",
        "genre/mystery/?page=" to "Mystery",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page").document
        val home = document.select("div.item,div.flw-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("span.title")?.text()?.trim() ?:""
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src")) ?:""
        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search.html?keyword=$query").document
        return document.select("div.list-drama div.item").mapNotNull {
            it.toSearchResult()
        }
    }

    open val contentInfoClass = "barContentInfo"
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.$contentInfoClass a")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.select("div.$contentInfoClass img").last()?.attr("src"))
        val tags = document.select("div.$contentInfoClass p:contains(Genres:) a").map { it.text().removePrefix(",").trim() }

        val year = document.selectFirst("div.$contentInfoClass p.type.Releasea")?.text()?.trim()?.toIntOrNull()
        val status = getStatus(document.selectFirst("div.$contentInfoClass p:contains(Status:)")?.ownText()?.trim())
        val description = document.selectFirst("div.$contentInfoClass p.des, div.$contentInfoClass p:last-child")?.nextElementSiblings()?.select("p")?.text()

        val episodes = document.select("ul.listing li, table.listing td.episodeSub").map {
            val name = it.selectFirst("a")?.attr("title")
            val link = fixUrlNull(it.selectFirst("a")?.attr("href"))
            val epNum = Regex("Episode\\s(\\d+)").find("$name")?.groupValues?.getOrNull(1)?.toIntOrNull()
            newEpisode(link) {
                this.name = name
                this.episode = epNum
            }
        }.reversed()

        if (episodes.size == 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, episodes[0].data) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
            }
        } else {
            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes = episodes) {
                posterUrl = poster
                this.year = year
                showStatus = status
                plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val server = document.selectFirst("select#selectServer option")?.attr("value")
        val iframe = app.get(httpsify(server ?: return false))
        val iframeDoc = iframe.document

        argamap({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    val status = element.attr("data-status") ?: return@forEach
                    if (status != "1") return@forEach
                    val extractorData = element.attr("data-video") ?: return@forEach
                    loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            val iv = "9262859232435825"
            val secretKey = "93422192433952489752342908585752"
            val secretDecryptKey = secretKey
            GogoHelper.extractVidstream(
                iframe.url,
                this.name,
                callback,
                iv,
                secretKey,
                secretDecryptKey,
                isUsingAdaptiveKeys = false,
                isUsingAdaptiveData = true,
                iframeDocument = iframeDoc
            )
        })

        return true
    }

}

class Kswplayer : Filesim() {
    override val name = "Kswplayer"
    override var mainUrl = "https://kswplayer.info"
}