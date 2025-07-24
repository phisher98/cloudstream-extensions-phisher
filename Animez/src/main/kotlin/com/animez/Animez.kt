package com.animez

import android.annotation.SuppressLint
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class Animez : MainAPI() {
    override var mainUrl = "https://animeyy.com"
    override var name = "Animez"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "?act=search&f[status]=all&f[sortby]=lastest-chap&&pageNum=" to "Recent Episode Added",
        "?act=searchadvance&f[min_num_chapter]=1&f[status]=In%20process&f[sortby]=top-manga&&pageNum=" to "Trending",
        "?act=searchadvance&f[min_num_chapter]=1&f[status]=In%20process&f[sortby]=lastest-manga&&pageNum=" to "Latest Update",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page").document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h2")?.text() ?:""
        val href = fixUrl(mainUrl+this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.select("img").last()?.getImageAttr())
        val num= this.selectFirst("span.mli-eps")!!.text().toIntOrNull()
        return newAnimeSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addDubStatus(num != null, num != null, num, num)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?act=search&f[keyword]=$query").document
        return document.select("article").map {
            it.toSearchResult()
        }
    }


    private fun getStatus(t: String): ShowStatus {
        return when (t) {
            "Finished Airing" -> ShowStatus.Completed
            "Updating" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    @SuppressLint("SuspiciousIndentation")
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.select("article.TPost.Single h2").text().trim()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.mvici-left > ul > li:nth-child(4) a").map { it.text() }
        val year = Regex(",\\s?(\\d+)").find(
            document.select("span.date").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val hrefm= mainUrl+document.select("ul.version-chap li a").attr("href")
        val tvType = if (document.select("ul.version-chap li").size > 1) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val rating = document.selectFirst("div.mrt5.mrb10 > span > span:nth-child(1)")?.text()?.toRatingInt()
        val status=document.select("div.mvici-left > ul > li:nth-child(2)").text().substringAfter(":").trim()
        val recommendations = document.select("div.TPostMv").map {
            val recName = it.selectFirst("a")!!.attr("title").removeSuffix("/").split("/").last()
            val recHref = mainUrl+it.selectFirst("a")!!.attr("href")
            val recPosterUrl = it.selectFirst("img")?.getImageAttr()
            newAnimeSearchResponse(recName, recHref, TvType.Anime) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val subEpisodes = mutableListOf<Episode>()
            val dubEpisodes = mutableListOf<Episode>()
            val regex = Regex("""load_list_chapter\((\d+)\)""")
            val lastPageNum = document.select("#nav_list_chapter_id_detail a.page-link")
                .mapNotNull { regex.find(it.attr("onclick"))?.groupValues?.get(1)?.toIntOrNull() }
                .maxOrNull()
                if (lastPageNum != null) {
                    runBlocking {
                    val malid = document.select("h2.SubTitle").attr("data-manga").takeIf { it.isNotEmpty() }
                    if (!malid.isNullOrEmpty()) {
                        coroutineScope {
                            val jobs = (lastPageNum downTo 1).map { page ->
                                async(Dispatchers.IO) {
                                    try {
                                        val rawres = app.get("$mainUrl/?act=ajax&code=load_list_chapter&manga_id=$malid&page_num=$page&chap_id=0&keyword=").text
                                        val listChapHtml = JSONObject(rawres).getString("list_chap")
                                        val parsedHtml: Document = Jsoup.parse(listChapHtml)

                                        parsedHtml.select("li.wp-manga-chapter a").forEach { element ->
                                            val href = element.attr("href").trim()
                                            val episodeName = element.text().trim()
                                            val episode = episodeName.filter { it.isDigit() }.toIntOrNull()

                                            val episodeObj = newEpisode(href) {
                                                this.name = "Episode ${episodeName.substringBefore("-")}"
                                                this.season = 1
                                                this.episode = episode
                                            }

                                            synchronized(subEpisodes) {
                                                if (episodeName.contains("dub", ignoreCase = true)) {
                                                    dubEpisodes += episodeObj
                                                } else {
                                                    subEpisodes += episodeObj
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            jobs.awaitAll() // Wait for all requests to complete
                        }
                    }
                }
            }
            else {
                document.select("ul.version-chap li").amap {
                    val href = it.select("a").attr("href")
                    val name = it.select("a").text().trim()
                    val image = it.selectFirst("div.imagen > img")?.getImageAttr()
                    val episode = it.select("div.numerando").text().replace(" ", "").split("-").last().toIntOrNull() ?: name.toIntOrNull()
                    val season = it.select("div.numerando").text().replace(" ", "").split("-").first().toIntOrNull() ?: 1
                    val episodeObj = newEpisode(href) {
                        this.name = "Episode ${
                            if (name.contains(
                                    "dub",
                                    ignoreCase = true
                                )
                            ) name.substringBefore("-") else name
                        }"
                        this.season = season
                        this.episode = episode
                        this.posterUrl = image
                    }
                    if (name.contains("dub", ignoreCase = true)) {
                        dubEpisodes += episodeObj
                    } else {
                        subEpisodes += episodeObj
                    }
                }
            }
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
                addTrailer(trailer)
                addEpisodes(DubStatus.Subbed, subEpisodes.reversed())
                addEpisodes(DubStatus.Dubbed, dubEpisodes.reversed())
                this.showStatus = getStatus(status)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, hrefm) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
                addTrailer(trailer)
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
        val token=document.select("iframe").attr("src").substringAfter("/embed/")
        document.select("#list_sv a").map {
            val host=it.attr("data-link")
            val m3u8="$host/anime/$token"
            val headers = mapOf(
                "referer" to m3u8,
            )
            M3u8Helper.generateM3u8(
                name,
                m3u8,
                m3u8,
                headers = headers
            ).forEach(callback)
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }
}
