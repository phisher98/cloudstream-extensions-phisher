package com.HindiProviders

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody
import java.net.URI

class Oldserials : MainAPI() {
    override var mainUrl = "https://oldserials.co"
    override var name = "Oldserials"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Latest",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/", timeout = 30L).document
        val home = document.select("div.row div.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("title") ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = this.selectFirst("a figure")?.attr("style").toString().substringAfter("('").substringBefore("')")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toJsonResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("title") ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = this.selectFirst("a figure")?.attr("style").toString().substringAfter("('").substringBefore("')")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("title") ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = "https://oldserials.co/pub/assets/images/OldSerials-logo-v3.png"
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    private fun Suggestion.toSearchResponse(): SearchResponse {
        return newTvSeriesSearchResponse(value,link,TvType.TvSeries) {
            this.posterUrl = "https://oldserials.co/pub/assets/images/OldSerials-logo-v3.png"
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("${mainUrl}/search?cat=all site&query=$query").parsedSafe<Serial>()?.suggestions?.map { suggestion ->
            suggestion.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 30L).document
        val title = document.selectFirst("div.contentp h1")?.text() ?: "Unknown"
        val posterUrl = document.selectFirst("div.cont-img figure img")?.attr("src") ?: "https://oldserials.co/pub/assets/images/OldSerials-logo-v3.png"
        val lastpage=document.select("#pagination_btns a:contains(Last)").attr("href").substringAfterLast("/").toIntOrNull() ?: 0
        val plot = document.selectFirst("div.story-section")?.text() ?: ""
        val recommendations = document.select("ul.serial-listing li").mapNotNull {
            it.toSearchResult()
        }
        val tvSeriesEpisodes = mutableListOf<Episode>()
        if (lastpage==0)
        {
            val doc= app.get(url).document
            var i=1
            doc.select("div.serial-main-page.shows-list div.row div.shows-box").mapNotNull {
                it.select("a").mapNotNull { episode ->
                    val epName = episode.selectFirst("span")?.text()
                    val epNum = i
                    val epUrl = fixUrl(episode.attr("href"))
                    tvSeriesEpisodes.add(
                        newEpisode(epUrl) {
                            name = epName
                            this.episode = epNum
                            this.posterUrl=posterUrl
                        }
                    )
                }
                i++
            }
        }
        else {
            for (i in 1..lastpage) {
                val doc = app.get("$url/asec/$i").document
                doc.select("div.serial-main-page.shows-list div.row div.shows-box").mapNotNull {
                    it.select("a").mapNotNull { episode ->
                        val epName = episode.selectFirst("span")?.text()
                        val epNum =
                            episode.selectFirst("span")?.text()?.substringAfter("Episode")?.trim()
                                ?.toIntOrNull() ?: 0
                        val epUrl = fixUrl(episode.attr("href"))
                        tvSeriesEpisodes.add(
                            newEpisode(epUrl) {
                                name = epName
                                this.episode = epNum
                                this.posterUrl = posterUrl
                            }
                        )
                    }
                }
            }
        }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, tvSeriesEpisodes) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.recommendations=recommendations
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data, timeout = 30L).document
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            "Content-Type" to "application/x-www-form-urlencoded"
        )
        document.select("div.bottom_episode_list  li div").forEach {
            val dataid=it.attr("data-id")
            val initialhref=it.attr("data-href")
            val body = FormBody.Builder()
                .addEncoded("id", dataid)
                .build()
            val initialpost=app.post(initialhref,requestBody = body, headers = headers, timeout = 30L).document.selectFirst("script:containsData($dataid)")?.data() ?:""
            val secondvalue=initialpost.substringAfterLast("value=\"").substringBefore("\">")
            val secondhref=initialpost.substringAfter("myRedirect(\"").substringBefore("\"")
            val secondbody = FormBody.Builder()
                .addEncoded("id", dataid)
                .addEncoded("channel", secondvalue)
                .build()
            val secondpost=app.post(secondhref,requestBody = secondbody, headers = headers, timeout = 30L).toString()
            val thirdid=secondpost.substringAfterLast("myRedirect(\"").substringAfter("id\", \"").substringBefore("\");")
            val thirdhref=secondpost.substringAfterLast("myRedirect(\"").substringBefore("\",")
            val thirdbody = FormBody.Builder()
                .addEncoded("id", thirdid)
                .build()
            val thirddomain=getBaseUrl(thirdhref)
            val forthhref= app.post(thirdhref,requestBody = thirdbody, referer = thirddomain, headers = headers, timeout = 30L).document.selectFirst("iframe")
                ?.attr("src") ?:""
            val domain=getBaseUrl(forthhref)
            val hash=forthhref.substringAfterLast("video/").substringBefore("/")
            val finalurl="$domain/player/index.php?data=$hash&do=getVideo"
            val link= app.post(finalurl, referer = domain ,headers = mapOf("X-Requested-With" to  "XMLHttpRequest"), timeout = 30L).parsedSafe<Video>()?.videoSource ?:""
            Log.d("Phisher","$domain $link")
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    link,
                    "",
                    getQualityFromName(""),
                    isM3u8 = true
                )
            )
        }
        return true
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    data class Serial(
        val query: String,
        val suggestions: List<Suggestion>,
    )

    data class Suggestion(
        val value: String,
        val data: String,
        val type: String,
        val link: String,
    )

    //final json
    data class Video(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<Any?>,
        val attachmentLinks: List<Any?>,
        val ck: String,
    )


}
