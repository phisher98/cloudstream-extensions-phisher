package com.kissasian

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class Kissasiansi : MainAPI() {
    override var mainUrl = "https://kissasian.si"
    override var name = "Kissasian SI"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
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
        "Status/Ongoing/?page=" to "Ongoing Drama",
        "DramaList/LatestUpdate?page=" to "Latest Updated",
        "DramaList/Newest?page=" to "New Drama",
        "DramaList/MostPopular?page=" to "Most Popular",
        "Status/Completed/?page=" to "Completed Drama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page").document
        val home = document.select("div.row div.full.inner").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("h2 a")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("src"))
        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearchquickResult(): SearchResponse? {
        val href = fixUrl(this.attr("href") ?: return null)
        val title = this.text().trim()
        val posterUrl = "https://i.etsystatic.com/27082425/r/il/427619/2781153828/il_fullxfull.2781153828_nsoo.jpg"
        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/Search/SearchSuggest/?type=Anime&keyword=$query").document
        return document.select("a.item_search_link").mapNotNull {
            it.toSearchquickResult()
        }
    }

    open val contentInfoClass = "barContentInfo"
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("#leftside h1")?.text()?.trim() ?: return null
        var poster = document.select("meta[property=og:image]").attr("content").toString()
        val tags = document.select("#leftside > div:nth-child(2) > div.barContent.full > div.full > div:nth-child(11) a").map { it.text().removePrefix(",").trim() }

        val year = document.selectFirst("#leftside > div:nth-child(2) > div.barContent.full > div.full > p:nth-child(4) > span")?.text()?.trim()?.toIntOrNull()
        val status = getStatus(document.selectFirst("#leftside > div:nth-child(2) > div.barContent.full > div.full > div.static_single > p:nth-child(1)")?.ownText()?.trim())
        val description = document.selectFirst("#leftside div.summary p")?.text()

        val episodes = document.select("div.full div.listing div  div h3").map {
            val name = it.selectFirst("a")?.ownText()
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
        document.select("#info_player option").amap {
            val value=it.attr("value")
            val id= value.substringAfter("id=").substringBefore("&")
            val server= value.substringAfter("s=").substringBefore("&")
            val api="$mainUrl/ajax/anime/load_episodes_v2?s=$server&episode_id=$id"
            val iframe = app.get(api).parsedSafe<Response>()?.getHtml() ?: throw ErrorLoadingException(
                "Could not parse json"
            )
            var href=iframe.select("iframe").attr("src")
            if (href.startsWith("//"))
            {
                href=href.substringAfter("//")
            }
            if (href.contains("embed.vodstream.xyz"))
                {
                    Log.d("Phisher href", href.toString())
                    val script= app.get(href, referer = mainUrl).document.selectFirst("script:containsData(sources)")
                        ?.toString()
                    if (script!=null) {
                        //Log.d("Phisher script", script.toString())
                        val m3u8 =
                            Regex("""\{"file":"(.*?)"""").find(script)?.groupValues?.getOrNull(1)
                        if (m3u8 != null) {
                            if (m3u8.contains("goto.php?url=")) {
                                val link=app.get(m3u8, referer = mainUrl, allowRedirects = false).headers["location"] ?:""
                                callback.invoke(
                                    ExtractorLink(
                                        "Vodstream Go",
                                        "Vodstream Go",
                                        link.replace("\\/", "/"),
                                        mainUrl,
                                        Qualities.P1080.value,
                                        type = INFER_TYPE
                                    )
                                )
                            } else
                            {
                                Log.d("Phisher", m3u8.toString())
                                callback.invoke(
                                    ExtractorLink(
                                        "Vodstream",
                                        "Vodstream",
                                        m3u8.replace("\\/", "/"),
                                        mainUrl,
                                        Qualities.P1080.value,
                                        type = INFER_TYPE
                                    )
                                )
                            }
                        } else {
                            Log.d("Phisher Error:", script.toString())
                        }
                    } else {
                        Log.d("Phisher Error:", "Null")
                    }
                }
            else
                {
                    loadExtractor(href,subtitleCallback, callback)
                }
        }
        return true
    }
    data class Response(
        val status: Boolean,
        val value: String,
        val tracks: List<Any?>,
        val embed: Boolean,
        val html5: Boolean,
        val type: String,
        val sv: String,
    ) {
        fun getHtml(): Document {
            return Jsoup.parse(value)
        }
    }

}