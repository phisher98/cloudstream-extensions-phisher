package com.kissasian

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

    override var mainUrl = "https://kdramaweb.com"
    override var name = "Dramacool"

    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "popular-drama" to "Popular Drama",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
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
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, referer = "$mainUrl/").document

        val items = document.select(".list-thumb li").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val name = it.selectFirst("h2")?.text() ?: return@mapNotNull null
            val posterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))
            val href = a.attr("href")
            newMovieSearchResponse(name, LoadUrl(href, posterUrl).toJson()) {
                this.posterUrl = posterUrl
            }
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
        Log.d("Phisher",data.toString())
        val server = document.selectFirst("#load-iframe")?.attr("onclick")?.substringAfter("playThis(\"")?.substringBefore("\")")
        val iframe = app.get(httpsify(server ?: return false))
        val iframeDoc = iframe.document
        argamap({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    Log.d("Phisher",element.toString())
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

    data class LoadUrl(
        val url: String,
        val posterUrl: String?
    )

}
