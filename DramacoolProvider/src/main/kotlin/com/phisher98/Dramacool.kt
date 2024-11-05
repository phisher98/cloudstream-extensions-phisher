package com.phisher98

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Dramacool : MainAPI() {
    override val supportedTypes = setOf(
        TvType.AsianDrama
    )
    override var lang = "en"

    override var mainUrl = "https://kstardrama.com"
    override var name = "Dramacool"

    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "popular-drama" to "Popular Drama",
        "popular-ongoing-series" to "Ongoing Series",
        "recently-added-drama" to "Recently Added Drama",
        "recently-added-movie" to "Recently Added Movie",
        "popular-completed-series" to "Popular Completed Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 30L).document
        val items = document.select("#drama div.card").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("a")?.attr("title") ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, LoadUrl(href, posterUrl).toJson()) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchtitle=query.createSlug()
        val url = "$mainUrl/search/$searchtitle"
        val document = app.get(url, referer = "$mainUrl/").document
        val items = document.select("#drama div.card").mapNotNull {
            it.toSearchResult()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = tryParseJson<LoadUrl>(url) ?: return null
        val document = app.get(d.url, referer = "$mainUrl/").document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val actors = document.select("div.slider div.img-container").map {
            Actor(
                it.select("div.bottom-right").text(),
                it.select("img").attr("src")
            )
        }
        val episodes = document.select("div.epdiv").mapNotNull { el ->
            val name=el.selectFirst("a")?.text()?.substringAfter("Episode")?.trim()
            val rawhref=el.selectFirst("a")?.attr("href") ?:""
            val href="$mainUrl/$rawhref"
            Episode(href, "Episode $name")
        }.reversed()

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = d.posterUrl
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val server = document.selectFirst("#load-iframe")?.attr("onclick")?.substringAfter("playThis(\"")?.substringBefore("\")")
        val iframe = app.get(httpsify(server ?: return false))
        val iframeDoc = iframe.document
        argamap({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    val extractorData = element.attr("data-video").substringBefore("=http")
                    val status = element.attr("data-status") ?: return@forEach
                    if (status != "1") return@forEach
                    loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            Log.d("Phisher",iframe.url)
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

    data class LoadUrl(
        val url: String,
        val posterUrl: String?
    )

    fun String?.createSlug(): String? {
        return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
            ?.trim()
            ?.replace("\\s+".toRegex(), "-")
            ?.lowercase()
    }

}
